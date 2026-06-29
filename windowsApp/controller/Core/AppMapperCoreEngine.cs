using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Models;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.Core;

/// <summary>
/// 核心引擎。持有全部业务服务，跑在后台线程（TCP AcceptLoop / 配对码 Timer / server 工作线程），
/// 实现 <see cref="ICoreFacade"/> 作为 UI 唯一入口。不引用 WPF、不碰 Dispatcher：
/// 业务事件在后台线程触发，UI 侧自行切线程；UI 卡死时核心继续推进，
/// 恢复后通过 <see cref="GetStateSnapshot"/> 重取状态。
/// </summary>
public sealed class AppMapperCoreEngine : ICoreFacade, IDisposable
{
    private readonly LogService log;
    private readonly SettingsService settingsService;
    private readonly TcpJsonServer server;
    private readonly PairingCodeService pairingCode;
    private readonly AppMappingService mapping;

    private readonly ConcurrentDictionary<string, DeviceState> devices = new();
    private readonly object stateLock = new();
    private string activeDeviceId = "";
    private string currentStatus = "等待设备连接";
    private string serverAddress = "";
    private string pairingUri = "";

    public Settings Settings => settingsService.Current;

    public string CurrentStatus => currentStatus;

    public event Action<LogEntry>? LogEmitted;
    public event Action<PairingInfo>? PairingChanged;
    public event Action<IReadOnlyList<DeviceState>>? DevicesChanged;
    public event Action<string>? StatusChanged;

    public AppMapperCoreEngine(LogService log, SettingsService settingsService)
    {
        this.log = log;
        this.settingsService = settingsService;
        server = new TcpJsonServer(log);
        pairingCode = new PairingCodeService();
        mapping = new AppMappingService(log);

        log.LogEmitted += e => LogEmitted?.Invoke(e);

        server.HelloReceived += OnHello;
        server.ActiveAppReceived += OnActiveApp;
        server.IdleReceived += OnIdle;
        server.Disconnected += OnDisconnected;

        pairingCode.CodeChanged += _ => UpdatePairingDisplay();

        // 设置副作用：RelaunchMapperWhenClosed 变化同步到映射服务。
        Settings.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(Settings.RelaunchMapperWhenClosed))
                mapping.RelaunchWhenClosed = Settings.RelaunchMapperWhenClosed;
        };
        mapping.RelaunchWhenClosed = Settings.RelaunchMapperWhenClosed;
    }

    public Task StartAsync()
    {
        RestartServer(Settings.Port);
        return Task.CompletedTask;
    }

    public async Task StopAsync()
    {
        try
        {
            server.Stop();
            mapping.CloseCurrent();
            pairingCode.Dispose();
        }
        catch (Exception ex)
        {
            log.Warn($"Stop error: {ex.Message}");
        }
        await Task.CompletedTask;
    }

    public void RestartServer(int port)
    {
        if (port < 1 || port > 65535) port = 8765;
        Settings.Port = port;
        server.Start(port, pairingCode.IsValid, maxDevices: 3);
        UpdatePairingDisplay();
        log.Info($"Pairing code: {pairingCode.CurrentCode}");
    }

    public void OpenAppsDirectory()
    {
        try
        {
            Directory.CreateDirectory(mapping.AppsDirectory);
            Process.Start(new ProcessStartInfo
            {
                FileName = mapping.AppsDirectory,
                UseShellExecute = true,
            });
        }
        catch (Exception ex)
        {
            log.Warn($"Open apps directory failed: {ex.Message}");
        }
    }

    public CoreStateSnapshot GetStateSnapshot()
    {
        lock (stateLock)
        {
            return new CoreStateSnapshot
            {
                PairingCode = pairingCode.CurrentCode,
                ServerAddress = serverAddress,
                PairingUri = pairingUri,
                Port = Settings.Port,
                CurrentStatus = currentStatus,
                Devices = GetDevicesSnapshot(),
                RecentLogs = log.GetRecent(),
            };
        }
    }

    private void UpdatePairingDisplay()
    {
        lock (stateLock)
        {
            var host = NetworkInfoService.GetPrimaryIPv4();
            serverAddress = $"{host}:{Settings.Port}";
            pairingUri = $"appmapper://connect?host={host}&port={Settings.Port}&code={pairingCode.CurrentCode}";
        }

        PairingChanged?.Invoke(new PairingInfo(pairingCode.CurrentCode, serverAddress, pairingUri, Settings.Port));
    }

    private void OnHello(string deviceId, string deviceName, string remoteEndpoint)
    {
        lock (stateLock)
        {
            var device = GetOrCreateDevice(deviceId);
            device.DeviceName = deviceName;
            device.State = "Connected";
            device.LastSeen = DateTime.Now;
            device.LastSequence = 0;
            RaiseDevicesChanged();
            log.Info($"Device connected: {deviceName} ({remoteEndpoint}).");
        }
    }

    private void OnActiveApp(string deviceId, AppInfo app, long sequence)
    {
        lock (stateLock)
        {
            var device = GetOrCreateDevice(deviceId);
            if (sequence < device.LastSequence) return;
            device.LastSequence = sequence;
            device.State = "Active";
            device.CurrentApp = app.DisplayName;
            device.LastSeen = DateTime.Now;
            activeDeviceId = deviceId;

            try
            {
                mapping.Show(app);
                SetStatus($"当前映射：{app.DisplayName}");
            }
            catch (Exception ex)
            {
                log.Error($"Mapping failed: {ex.Message}");
            }

            RaiseDevicesChanged();
        }
    }

    private void OnIdle(string deviceId, string reason, long sequence)
    {
        lock (stateLock)
        {
            var device = GetOrCreateDevice(deviceId);
            if (sequence < device.LastSequence) return;
            device.LastSequence = sequence;
            device.State = $"Idle: {reason}";
            device.CurrentApp = "";
            device.LastSeen = DateTime.Now;

            if (activeDeviceId == deviceId)
            {
                mapping.CloseCurrent();
                activeDeviceId = "";
                SetStatus("手机进入 idle，已关闭映射窗口");
            }

            RaiseDevicesChanged();
        }
    }

    private void OnDisconnected(string deviceId)
    {
        lock (stateLock)
        {
            var device = GetOrCreateDevice(deviceId);
            device.State = "Disconnected";
            device.LastSeen = DateTime.Now;

            if (activeDeviceId == deviceId)
            {
                mapping.CloseCurrent();
                activeDeviceId = "";
                SetStatus("当前活跃设备断线，已关闭映射窗口");
            }

            RaiseDevicesChanged();
            log.Info($"Device disconnected: {deviceId}.");
        }
    }

    private DeviceState GetOrCreateDevice(string deviceId)
    {
        if (devices.TryGetValue(deviceId, out var existing)) return existing;
        var created = new DeviceState { DeviceId = deviceId, DeviceName = deviceId };
        devices[deviceId] = created;
        return created;
    }

    private IReadOnlyList<DeviceState> GetDevicesSnapshot() =>
        devices.Values.OrderByDescending(x => x.LastSeen).ToList();

    private void RaiseDevicesChanged() => DevicesChanged?.Invoke(GetDevicesSnapshot());

    private void SetStatus(string value)
    {
        currentStatus = value;
        StatusChanged?.Invoke(value);
    }

    public void Dispose()
    {
        StopAsync().Wait();
        log.Dispose();
        settingsService.Dispose();
    }
}

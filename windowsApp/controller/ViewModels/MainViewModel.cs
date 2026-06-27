using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using AppMapper.Controller.Models;
using AppMapper.Controller.Services;
using WpfApplication = System.Windows.Application;

namespace AppMapper.Controller.ViewModels;

public sealed class MainViewModel : INotifyPropertyChanged
{
    private readonly PairingCodeService pairingCode = new();
    private readonly TcpJsonServer server = new();
    private readonly AppMappingService mapping = new();
    private readonly Dictionary<string, DeviceState> devices = new();
    private string activeDeviceId = "";
    private string serverAddress = "";
    private string pairingUri = "";
    private BitmapImage? qrImage;
    private string port = "8765";
    private string currentStatus = "等待设备连接";
    private bool relaunchMapperWhenClosed = true;

    public MainViewModel()
    {
        RestartServerCommand = new RelayCommand(RestartServer);
        OpenAppsDirectoryCommand = new RelayCommand(OpenAppsDirectory);

        server.Log += AddLog;
        server.HelloReceived += OnHello;
        server.ActiveAppReceived += OnActiveApp;
        server.IdleReceived += OnIdle;
        server.Disconnected += OnDisconnected;
        mapping.Log += AddLog;
        pairingCode.CodeChanged += _ => WpfApplication.Current.Dispatcher.Invoke(UpdatePairingDisplay);

        RestartServer();
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public ObservableCollection<DeviceState> Devices { get; } = new();
    public ObservableCollection<string> Logs { get; } = new();

    public ICommand RestartServerCommand { get; }
    public ICommand OpenAppsDirectoryCommand { get; }

    public string ServerAddress
    {
        get => serverAddress;
        private set => SetField(ref serverAddress, value);
    }

    public string PairingCode => pairingCode.CurrentCode;

    public string PairingUri
    {
        get => pairingUri;
        private set => SetField(ref pairingUri, value);
    }

    public BitmapImage? QrImage
    {
        get => qrImage;
        private set => SetField(ref qrImage, value);
    }

    public string Port
    {
        get => port;
        set => SetField(ref port, value);
    }

    public string CurrentStatus
    {
        get => currentStatus;
        private set => SetField(ref currentStatus, value);
    }

    public bool RelaunchMapperWhenClosed
    {
        get => relaunchMapperWhenClosed;
        set
        {
            if (SetField(ref relaunchMapperWhenClosed, value))
                mapping.RelaunchWhenClosed = value;
        }
    }

    private void RestartServer()
    {
        if (!int.TryParse(Port, out var parsedPort)) parsedPort = 8765;
        Port = parsedPort.ToString();
        server.Start(parsedPort, pairingCode.IsValid, maxDevices: 3);
        UpdatePairingDisplay();
        AddLog($"Pairing code: {PairingCode}");
    }

    private void UpdatePairingDisplay()
    {
        var host = NetworkInfoService.GetPrimaryIPv4();
        ServerAddress = $"{host}:{Port}";
        PairingUri = $"appmapper://connect?host={host}&port={Port}&code={PairingCode}";
        QrImage = QrCodeService.Generate(PairingUri);
        OnPropertyChanged(nameof(PairingCode));
    }

    private void OnHello(string deviceId, string deviceName, string remoteEndpoint)
    {
        WpfApplication.Current.Dispatcher.Invoke(() =>
        {
            var device = GetOrCreateDevice(deviceId);
            device.DeviceName = deviceName;
            device.State = "Connected";
            device.LastSeen = DateTime.Now;
            device.LastSequence = 0;
            RefreshDevices();
            AddLog($"Device connected: {deviceName} ({remoteEndpoint}).");
        });
    }

    private void OnActiveApp(string deviceId, AppInfo app, long sequence)
    {
        WpfApplication.Current.Dispatcher.Invoke(() =>
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
                CurrentStatus = $"当前映射：{app.DisplayName}";
            }
            catch (Exception ex)
            {
                AddLog($"Mapping failed: {ex.Message}");
            }

            RefreshDevices();
        });
    }

    private void OnIdle(string deviceId, string reason, long sequence)
    {
        WpfApplication.Current.Dispatcher.Invoke(() =>
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
                CurrentStatus = "手机进入 idle，已关闭映射窗口";
            }

            RefreshDevices();
        });
    }

    private void OnDisconnected(string deviceId)
    {
        WpfApplication.Current.Dispatcher.Invoke(() =>
        {
            var device = GetOrCreateDevice(deviceId);
            device.State = "Disconnected";
            device.LastSeen = DateTime.Now;

            if (activeDeviceId == deviceId)
            {
                mapping.CloseCurrent();
                activeDeviceId = "";
                CurrentStatus = "当前活跃设备断线，已关闭映射窗口";
            }

            RefreshDevices();
            AddLog($"Device disconnected: {deviceId}.");
        });
    }

    private DeviceState GetOrCreateDevice(string deviceId)
    {
        if (devices.TryGetValue(deviceId, out var existing)) return existing;
        var created = new DeviceState { DeviceId = deviceId, DeviceName = deviceId };
        devices[deviceId] = created;
        return created;
    }

    private void RefreshDevices()
    {
        Devices.Clear();
        foreach (var device in devices.Values.OrderByDescending(x => x.LastSeen))
            Devices.Add(device);
    }

    private void AddLog(string message)
    {
        WpfApplication.Current.Dispatcher.Invoke(() =>
        {
            Logs.Insert(0, $"[{DateTime.Now:HH:mm:ss}] {message}");
            while (Logs.Count > 300) Logs.RemoveAt(Logs.Count - 1);
        });
    }

    private void OpenAppsDirectory()
    {
        Directory.CreateDirectory(mapping.AppsDirectory);
        Process.Start(new ProcessStartInfo
        {
            FileName = mapping.AppsDirectory,
            UseShellExecute = true,
        });
    }

    private bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

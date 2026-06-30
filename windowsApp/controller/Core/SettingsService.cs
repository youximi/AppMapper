using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using AppMapper.Controller.Abstractions;
using Timer = System.Threading.Timer;

namespace AppMapper.Controller.Core;

/// <summary>
/// 读写 <c>config/settings.json</c>。订阅 <see cref="Settings"/> 的属性变更，
/// 防抖落盘——任何设置改动都会自动持久化（需求 18）。
/// </summary>
public sealed class SettingsService : IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    private readonly string configPath;
    private readonly Timer saveTimer;
    private readonly object saveLock = new();
    private bool disposed;

    public Settings Current { get; } = new();

    public SettingsService(string? baseDirectory = null)
    {
        var dir = Path.Combine(baseDirectory ?? AppContext.BaseDirectory, "config");
        Directory.CreateDirectory(dir);
        configPath = Path.Combine(dir, "settings.json");
        Load();
        Current.PropertyChanged += OnSettingChanged;
        saveTimer = new Timer(_ => SaveNow(), null, Timeout.Infinite, Timeout.Infinite);
    }

    private void OnSettingChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        // 防抖：500ms 内多次改动合并为一次落盘。
        lock (saveLock)
        {
            saveTimer.Change(TimeSpan.FromMilliseconds(500), Timeout.InfiniteTimeSpan);
        }
    }

    private void Load()
    {
        try
        {
            if (!File.Exists(configPath)) return;
            var json = File.ReadAllText(configPath);
            var loaded = JsonSerializer.Deserialize<SettingsDto>(json, JsonOptions);
            if (loaded == null) return;
            if (loaded.Port is { } port) Current.Port = port;
            if (loaded.RelaunchMapperWhenClosed is { } relaunch) Current.RelaunchMapperWhenClosed = relaunch;
            if (loaded.CloseToTray is { } closeToTray) Current.CloseToTray = closeToTray;
            if (loaded.PaneOpen is { } paneOpen) Current.PaneOpen = paneOpen;
            if (loaded.NetworkAdapterId is { } networkAdapterId) Current.NetworkAdapterId = networkAdapterId;
            if (loaded.NetworkAdapterName is { } networkAdapterName) Current.NetworkAdapterName = networkAdapterName;
            if (loaded.PreferredIpVersion is { } preferredIpVersion) Current.PreferredIpVersion = preferredIpVersion;
            if (loaded.SelectedIpAddress is { } selectedIpAddress) Current.SelectedIpAddress = selectedIpAddress;
        }
        catch
        {
            // 配置损坏时回退默认值。
        }
    }

    private void SaveNow()
    {
        lock (saveLock)
        {
            try
            {
                var dto = new SettingsDto
                {
                    Port = Current.Port,
                    RelaunchMapperWhenClosed = Current.RelaunchMapperWhenClosed,
                    CloseToTray = Current.CloseToTray,
                    PaneOpen = Current.PaneOpen,
                    NetworkAdapterId = Current.NetworkAdapterId,
                    NetworkAdapterName = Current.NetworkAdapterName,
                    PreferredIpVersion = Current.PreferredIpVersion,
                    SelectedIpAddress = Current.SelectedIpAddress,
                };
                var json = JsonSerializer.Serialize(dto, JsonOptions);
                File.WriteAllText(configPath, json);
            }
            catch
            {
                // 落盘失败不影响运行。
            }
        }
    }

    /// <summary>立即落盘（退出时调用）。</summary>
    public void Flush()
    {
        lock (saveLock)
        {
            saveTimer.Change(Timeout.Infinite, Timeout.Infinite);
        }
        SaveNow();
    }

    public void Dispose()
    {
        if (disposed) return;
        disposed = true;
        saveTimer.Dispose();
        Flush();
    }

    private sealed class SettingsDto
    {
        public int? Port { get; set; }
        public bool? RelaunchMapperWhenClosed { get; set; }
        public bool? CloseToTray { get; set; }
        public bool? PaneOpen { get; set; }
        public string? NetworkAdapterId { get; set; }
        public string? NetworkAdapterName { get; set; }
        public string? PreferredIpVersion { get; set; }
        public string? SelectedIpAddress { get; set; }
    }
}

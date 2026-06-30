using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace AppMapper.Controller.Abstractions;

/// <summary>
/// 配置模型（INPC）。核心层 <c>SettingsService</c> 订阅其变更自动落盘到
/// <c>config/settings.json</c>；<c>AppMapperCoreEngine</c> 订阅以应用副作用
/// （如 <see cref="RelaunchMapperWhenClosed"/> 同步到映射服务）。
/// UI 直接绑定此对象的属性。
/// </summary>
public sealed class Settings : INotifyPropertyChanged
{
    private int port = 8765;
    private bool relaunchMapperWhenClosed = true;
    private bool closeToTray = true;
    private bool paneOpen = true;
    private string networkAdapterId = "";
    private string networkAdapterName = "";
    private string preferredIpVersion = "IPv4";
    private string selectedIpAddress = "";

    /// <summary>TCP 监听端口。修改后需在 UI 点「重启服务」生效。</summary>
    public int Port
    {
        get => port;
        set => SetField(ref port, value);
    }

    /// <summary>用户关闭映射窗口后是否自动重开。</summary>
    public bool RelaunchMapperWhenClosed
    {
        get => relaunchMapperWhenClosed;
        set => SetField(ref relaunchMapperWhenClosed, value);
    }

    /// <summary>点窗口 X 时缩到托盘而非退出（需求 11）。</summary>
    public bool CloseToTray
    {
        get => closeToTray;
        set => SetField(ref closeToTray, value);
    }

    /// <summary>左侧导航栏是否展开显示文字（持久化折叠状态）。</summary>
    public bool PaneOpen
    {
        get => paneOpen;
        set => SetField(ref paneOpen, value);
    }

    /// <summary>绑定的网络适配器系统 ID；空值表示自动选择。</summary>
    public string NetworkAdapterId
    {
        get => networkAdapterId;
        set => SetField(ref networkAdapterId, value?.Trim() ?? "");
    }

    /// <summary>绑定网卡的展示名，仅用于网卡暂不可用时提示用户。</summary>
    public string NetworkAdapterName
    {
        get => networkAdapterName;
        set => SetField(ref networkAdapterName, value?.Trim() ?? "");
    }

    /// <summary>优先使用的 IP 版本：IPv4 / IPv6。</summary>
    public string PreferredIpVersion
    {
        get => preferredIpVersion;
        set => SetField(ref preferredIpVersion, value == "IPv6" ? "IPv6" : "IPv4");
    }

    /// <summary>绑定的具体 IP；空值表示在当前网卡上自动取一个可用地址。</summary>
    public string SelectedIpAddress
    {
        get => selectedIpAddress;
        set => SetField(ref selectedIpAddress, value?.Trim() ?? "");
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        return true;
    }
}

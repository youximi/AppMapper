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
    /// <summary>TCP 监听端口。修改后需在 UI 点「重启服务」生效。</summary>
    public int Port
    {
        get;
        set => SetField(ref field, value);
    } = 8765;

    /// <summary>用户关闭映射窗口后是否自动重开。</summary>
    public bool RelaunchMapperWhenClosed
    {
        get;
        set => SetField(ref field, value);
    } = true;

    /// <summary>点窗口 X 时缩到托盘而非退出（需求 11）。</summary>
    public bool CloseToTray
    {
        get;
        set => SetField(ref field, value);
    } = true;

    /// <summary>Launch when the current Windows user signs in.</summary>
    public bool StartWithWindows
    {
        get;
        set => SetField(ref field, value);
    }

    /// <summary>左侧导航栏是否展开显示文字（持久化折叠状态）。</summary>
    public bool PaneOpen
    {
        get;
        set => SetField(ref field, value);
    } = true;

    /// <summary>绑定的网络适配器系统 ID；空值表示自动选择。</summary>
    public string NetworkAdapterId
    {
        get;
        set => SetField(ref field, value?.Trim() ?? "");
    } = "";

    /// <summary>绑定网卡的展示名，仅用于网卡暂不可用时提示用户。</summary>
    public string NetworkAdapterName
    {
        get;
        set => SetField(ref field, value?.Trim() ?? "");
    } = "";

    /// <summary>优先使用的 IP 版本：IPv4 / IPv6。</summary>
    public string PreferredIpVersion
    {
        get;
        set => SetField(ref field, value == "IPv6" ? "IPv6" : "IPv4");
    } = "IPv4";

    /// <summary>绑定的具体 IP；空值表示在当前网卡上自动取一个可用地址。</summary>
    public string SelectedIpAddress
    {
        get;
        set => SetField(ref field, value?.Trim() ?? "");
    } = "";

    /// <summary>检查更新时同时接受正式版和测试版；默认仅接受正式版。</summary>
    public bool IncludePrereleases
    {
        get;
        set => SetField(ref field, value);
    }

    public int LastNotifiedBuild
    {
        get;
        set => SetField(ref field, value);
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

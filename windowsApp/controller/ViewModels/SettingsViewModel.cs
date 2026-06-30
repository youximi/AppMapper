using System.Windows.Input;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.ViewModels;

/// <summary>设置页 VM：端口 / 关闭映射窗口自动重开 / 关闭到托盘 / 显示侧栏文字。
/// 直接读写 <see cref="ICoreFacade.Settings"/>（核心自动落盘），并订阅其变更以同步外部改动（如主窗口折叠侧栏）。</summary>
public sealed class SettingsViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    private string port = "8765";
    private bool relaunchMapperWhenClosed = true;
    private bool closeToTray = true;
    private bool paneOpen = true;
    private string selectedNetworkAdapterId = "";
    private string preferredIpVersion = "IPv4";
    private string selectedIpAddress = "";
    private IReadOnlyList<NetworkAdapterChoice> networkAdapters = [];
    private IReadOnlyList<IpAddressChoice> ipAddresses = [];

    public SettingsViewModel(ICoreFacade core)
    {
        this.core = core;
        OpenAppsDirectoryCommand = new RelayCommand(core.OpenAppsDirectory);
        RestartServerCommand = new RelayCommand(RestartServer);
        RefreshNetworkAdaptersCommand = new RelayCommand(RefreshNetworkAdapters);

        var s = core.Settings;
        port = s.Port.ToString();
        relaunchMapperWhenClosed = s.RelaunchMapperWhenClosed;
        closeToTray = s.CloseToTray;
        paneOpen = s.PaneOpen;
        selectedNetworkAdapterId = s.NetworkAdapterId;
        preferredIpVersion = s.PreferredIpVersion;
        selectedIpAddress = s.SelectedIpAddress;
        RefreshNetworkAdapters();

        s.PropertyChanged += OnSettingChanged;
    }

    public ICommand OpenAppsDirectoryCommand { get; }
    public ICommand RestartServerCommand { get; }
    public ICommand RefreshNetworkAdaptersCommand { get; }

    public IReadOnlyList<string> IpVersionOptions { get; } = ["IPv4", "IPv6"];

    private void RestartServer()
    {
        if (!int.TryParse(port, out var p)) p = core.Settings.Port;
        core.RestartServer(p);
    }

    public string Port
    {
        get => port;
        set
        {
            if (SetField(ref port, value) && int.TryParse(value, out var p))
                core.Settings.Port = p;
        }
    }

    public IReadOnlyList<NetworkAdapterChoice> NetworkAdapters
    {
        get => networkAdapters;
        private set => SetField(ref networkAdapters, value);
    }

    public IReadOnlyList<IpAddressChoice> IpAddresses
    {
        get => ipAddresses;
        private set => SetField(ref ipAddresses, value);
    }

    public string SelectedNetworkAdapterId
    {
        get => selectedNetworkAdapterId;
        set
        {
            value ??= "";
            if (!SetField(ref selectedNetworkAdapterId, value)) return;

            var selected = NetworkAdapters.FirstOrDefault(x => x.Id == value);
            core.Settings.NetworkAdapterId = value;
            core.Settings.NetworkAdapterName = selected?.Name ?? "";
            selectedIpAddress = "";
            core.Settings.SelectedIpAddress = "";
            OnPropertyChanged(nameof(SelectedIpAddress));
            RefreshIpAddresses();
        }
    }

    public string PreferredIpVersion
    {
        get => preferredIpVersion;
        set
        {
            value = value == "IPv6" ? "IPv6" : "IPv4";
            if (!SetField(ref preferredIpVersion, value)) return;
            core.Settings.PreferredIpVersion = value;
            selectedIpAddress = "";
            core.Settings.SelectedIpAddress = "";
            OnPropertyChanged(nameof(SelectedIpAddress));
            RefreshIpAddresses();
        }
    }

    public string SelectedIpAddress
    {
        get => selectedIpAddress;
        set
        {
            value ??= "";
            if (SetField(ref selectedIpAddress, value))
                core.Settings.SelectedIpAddress = value;
        }
    }

    public bool RelaunchMapperWhenClosed
    {
        get => relaunchMapperWhenClosed;
        set
        {
            if (SetField(ref relaunchMapperWhenClosed, value))
                core.Settings.RelaunchMapperWhenClosed = value;
        }
    }

    public bool CloseToTray
    {
        get => closeToTray;
        set
        {
            if (SetField(ref closeToTray, value))
                core.Settings.CloseToTray = value;
        }
    }

    public bool PaneOpen
    {
        get => paneOpen;
        set
        {
            if (SetField(ref paneOpen, value))
                core.Settings.PaneOpen = value;
        }
    }

    private void OnSettingChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        Dispatch(() =>
        {
            var s = core.Settings;
            switch (e.PropertyName)
            {
                case nameof(Settings.Port):
                    port = s.Port.ToString();
                    OnPropertyChanged(nameof(Port));
                    break;
                case nameof(Settings.RelaunchMapperWhenClosed):
                    if (relaunchMapperWhenClosed != s.RelaunchMapperWhenClosed)
                    {
                        relaunchMapperWhenClosed = s.RelaunchMapperWhenClosed;
                        OnPropertyChanged(nameof(RelaunchMapperWhenClosed));
                    }
                    break;
                case nameof(Settings.CloseToTray):
                    if (closeToTray != s.CloseToTray)
                    {
                        closeToTray = s.CloseToTray;
                        OnPropertyChanged(nameof(CloseToTray));
                    }
                    break;
                case nameof(Settings.PaneOpen):
                    if (paneOpen != s.PaneOpen)
                    {
                        paneOpen = s.PaneOpen;
                        OnPropertyChanged(nameof(PaneOpen));
                    }
                    break;
                case nameof(Settings.NetworkAdapterId):
                    if (selectedNetworkAdapterId != s.NetworkAdapterId)
                    {
                        selectedNetworkAdapterId = s.NetworkAdapterId;
                        OnPropertyChanged(nameof(SelectedNetworkAdapterId));
                        RefreshIpAddresses();
                    }
                    break;
                case nameof(Settings.PreferredIpVersion):
                    if (preferredIpVersion != s.PreferredIpVersion)
                    {
                        preferredIpVersion = s.PreferredIpVersion;
                        OnPropertyChanged(nameof(PreferredIpVersion));
                        RefreshIpAddresses();
                    }
                    break;
                case nameof(Settings.SelectedIpAddress):
                    if (selectedIpAddress != s.SelectedIpAddress)
                    {
                        selectedIpAddress = s.SelectedIpAddress;
                        OnPropertyChanged(nameof(SelectedIpAddress));
                    }
                    break;
            }
        });
    }

    private void RefreshNetworkAdapters()
    {
        var choices = new List<NetworkAdapterChoice>
        {
            new("", "自动选择", "自动选择", false, true, []),
        };

        choices.AddRange(NetworkInfoService.GetAdapters().Select(adapter => new NetworkAdapterChoice(
            adapter.Id,
            adapter.Name,
            adapter.DisplayName,
            adapter.IsVirtual,
            adapter.IsUp,
            adapter.Addresses.Select(address => new IpAddressChoice(address.Address, address.Address, address.Version.ToString())).ToList())));

        if (!string.IsNullOrWhiteSpace(selectedNetworkAdapterId) && choices.All(x => x.Id != selectedNetworkAdapterId))
        {
            var name = string.IsNullOrWhiteSpace(core.Settings.NetworkAdapterName)
                ? "已绑定网卡"
                : core.Settings.NetworkAdapterName;
            choices.Add(new NetworkAdapterChoice(
                selectedNetworkAdapterId,
                name,
                $"{name}（当前不可用）",
                false,
                false,
                []));
        }

        NetworkAdapters = choices;
        RefreshIpAddresses();
    }

    private void RefreshIpAddresses()
    {
        var options = new List<IpAddressChoice>
        {
            new("", "自动使用当前网卡 IP", "自动"),
        };

        if (!string.IsNullOrWhiteSpace(selectedNetworkAdapterId))
        {
            var adapter = NetworkAdapters.FirstOrDefault(x => x.Id == selectedNetworkAdapterId);
            if (adapter != null)
            {
                options.AddRange(adapter.Addresses
                    .Where(x => x.Version == preferredIpVersion)
                    .OrderBy(x => x.Address, StringComparer.Ordinal));
            }
        }

        IpAddresses = options;
        if (!IpAddresses.Any(x => x.Address == selectedIpAddress))
        {
            if (!string.IsNullOrWhiteSpace(selectedNetworkAdapterId) &&
                !string.IsNullOrWhiteSpace(selectedIpAddress))
            {
                options.Add(new IpAddressChoice(
                    selectedIpAddress,
                    $"{selectedIpAddress}（当前不可用）",
                    preferredIpVersion));
                IpAddresses = options;
                return;
            }

            selectedIpAddress = "";
            core.Settings.SelectedIpAddress = "";
            OnPropertyChanged(nameof(SelectedIpAddress));
        }
    }
}

public sealed record NetworkAdapterChoice(
    string Id,
    string Name,
    string DisplayName,
    bool IsVirtual,
    bool IsUp,
    IReadOnlyList<IpAddressChoice> Addresses);

public sealed record IpAddressChoice(string Address, string DisplayName, string Version);

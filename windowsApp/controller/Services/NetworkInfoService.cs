using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using AppMapper.Controller.Abstractions;

namespace AppMapper.Controller.Services;

public enum IpVersion
{
    IPv4,
    IPv6,
}

public sealed record NetworkAddressOption(string Address, IpVersion Version);

public sealed record NetworkAdapterOption(
    string Id,
    string Name,
    string Description,
    bool IsUp,
    bool IsVirtual,
    IReadOnlyList<NetworkAddressOption> Addresses)
{
    public string DisplayName
    {
        get
        {
            var status = IsUp ? "已连接" : "未连接";
            var marker = IsVirtual ? "，疑似虚拟" : "";
            var address = Addresses.FirstOrDefault()?.Address;
            return string.IsNullOrWhiteSpace(address)
                ? $"{Name}（{status}{marker}）"
                : $"{Name} - {address}（{status}{marker}）";
        }
    }
}

public sealed record NetworkAddressSelection(
    string Host,
    string ServerAddressHost,
    string? Warning);

public static class NetworkInfoService
{
    private static readonly StringComparer IdComparer = StringComparer.OrdinalIgnoreCase;

    public static IReadOnlyList<NetworkAdapterOption> GetAdapters() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Select(ToRawOption)
            .OrderByDescending(x => x.IsUp)
            .ThenBy(x => x.IsVirtual)
            .ThenByDescending(x => x.HasGateway)
            .ThenBy(x => x.Name, StringComparer.CurrentCultureIgnoreCase)
            .Select(ToOption)
            .ToList();

    public static NetworkAddressSelection ResolveAddress(Settings settings)
    {
        var adapters = NetworkInterface.GetAllNetworkInterfaces().Select(ToRawOption).ToList();
        var selectedVersion = ParseIpVersion(settings.PreferredIpVersion);
        var hasBoundAdapter = !string.IsNullOrWhiteSpace(settings.NetworkAdapterId);
        RawAdapterOption? adapter = null;

        if (hasBoundAdapter)
        {
            adapter = adapters.FirstOrDefault(x => IdComparer.Equals(x.Id, settings.NetworkAdapterId));
            if (adapter == null)
            {
                var name = string.IsNullOrWhiteSpace(settings.NetworkAdapterName) ? "指定网卡" : settings.NetworkAdapterName;
                return LocalhostSelection($"{name} 当前不可用，请在设置中重新选择网络适配器。");
            }
        }
        else
        {
            adapter = adapters
                .Where(x => x.CanUseAutomatically)
                .Where(x => x.Addresses.Any(a => a.Version == selectedVersion))
                .OrderBy(x => x.IsVirtual)
                .ThenByDescending(x => x.HasGateway)
                .ThenByDescending(x => x.InterfaceType is NetworkInterfaceType.Wireless80211 or NetworkInterfaceType.Ethernet)
                .ThenBy(x => x.Name, StringComparer.CurrentCultureIgnoreCase)
                .FirstOrDefault();
        }

        if (adapter == null)
            return LocalhostSelection($"未找到可用的 {selectedVersion} 地址。");

        var candidates = adapter.Addresses.Where(x => x.Version == selectedVersion).ToList();
        if (!adapter.CanUseWhenBound || candidates.Count == 0)
        {
            return LocalhostSelection(
                $"{adapter.Name} 当前没有可用的 {selectedVersion} 地址，请检查网络或在设置中重新选择。");
        }

        if (hasBoundAdapter && !string.IsNullOrWhiteSpace(settings.SelectedIpAddress))
        {
            var selectedAddress = candidates.FirstOrDefault(x => x.Address == settings.SelectedIpAddress);
            if (selectedAddress == null)
            {
                return LocalhostSelection(
                    $"{adapter.Name} 上找不到绑定的 IP {settings.SelectedIpAddress}，请在设置中重新选择。");
            }

            return CreateSelection(selectedAddress.Address, selectedAddress.Version, null);
        }

        var address = candidates
            .OrderByDescending(x => IsPrivateIPv4(x.Address))
            .ThenBy(x => x.Address, StringComparer.Ordinal)
            .First();

        return CreateSelection(address.Address, address.Version, null);
    }

    public static string GetPrimaryIPv4() => ResolveAddress(new Settings()).Host;

    public static string FormatHostForUri(string address) =>
        Uri.EscapeDataString(address);

    private static NetworkAddressSelection LocalhostSelection(string warning) =>
        CreateSelection("127.0.0.1", IpVersion.IPv4, warning);

    private static NetworkAddressSelection CreateSelection(string address, IpVersion version, string? warning)
    {
        var serverAddressHost = version == IpVersion.IPv6 ? $"[{address}]" : address;
        return new NetworkAddressSelection(address, serverAddressHost, warning);
    }

    private static NetworkAdapterOption ToOption(RawAdapterOption raw) =>
        new(
            raw.Id,
            raw.Name,
            raw.Description,
            raw.IsUp,
            raw.IsVirtual,
            raw.Addresses.Select(x => new NetworkAddressOption(x.Address, x.Version)).ToList());

    private static RawAdapterOption ToRawOption(NetworkInterface networkInterface)
    {
        var properties = networkInterface.GetIPProperties();
        var addresses = properties.UnicastAddresses
            .Select(x => ToAddressOption(x.Address))
            .Where(x => x != null)
            .Select(x => x!)
            .ToList();

        return new RawAdapterOption(
            networkInterface.Id,
            networkInterface.Name,
            networkInterface.Description,
            networkInterface.OperationalStatus == OperationalStatus.Up,
            IsVirtualAdapter(networkInterface),
            properties.GatewayAddresses.Any(x => !IsAnyAddress(x.Address)),
            networkInterface.NetworkInterfaceType,
            addresses);
    }

    private static RawAddressOption? ToAddressOption(IPAddress address)
    {
        if (IPAddress.IsLoopback(address)) return null;

        return address.AddressFamily switch
        {
            AddressFamily.InterNetwork when !IsLinkLocalIPv4(address) =>
                new RawAddressOption(address.ToString(), IpVersion.IPv4),
            AddressFamily.InterNetworkV6 when IsUsableIPv6(address) =>
                new RawAddressOption(address.ToString(), IpVersion.IPv6),
            _ => null,
        };
    }

    private static bool IsUsableIPv6(IPAddress address) =>
        !address.IsIPv6LinkLocal &&
        !address.IsIPv6Multicast &&
        !address.IsIPv6SiteLocal &&
        !address.IsIPv6Teredo;

    private static bool IsLinkLocalIPv4(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return bytes is [169, 254, _, _];
    }

    private static bool IsAnyAddress(IPAddress address) =>
        address.Equals(IPAddress.Any) || address.Equals(IPAddress.IPv6Any);

    private static bool IsPrivateIPv4(string value)
    {
        if (!IPAddress.TryParse(value, out var address)) return false;
        var bytes = address.GetAddressBytes();
        return bytes.Length == 4 &&
               (bytes[0] == 10 ||
                bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31 ||
                bytes[0] == 192 && bytes[1] == 168);
    }

    private static IpVersion ParseIpVersion(string value) =>
        value == "IPv6" ? IpVersion.IPv6 : IpVersion.IPv4;

    private static bool IsVirtualAdapter(NetworkInterface networkInterface)
    {
        if (networkInterface.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel)
            return true;

        var text = $"{networkInterface.Name} {networkInterface.Description}".ToLowerInvariant();
        return text.Contains("virtual") ||
               text.Contains("vmware") ||
               text.Contains("virtualbox") ||
               text.Contains("hyper-v") ||
               text.Contains("wsl") ||
               text.Contains("vpn") ||
               text.Contains("tunnel") ||
               text.Contains("mihomo") ||
               text.Contains("clash") ||
               text.Contains("tap") ||
               text.Contains("tailscale") ||
               text.Contains("zerotier");
    }

    private sealed record RawAddressOption(string Address, IpVersion Version);

    private sealed record RawAdapterOption(
        string Id,
        string Name,
        string Description,
        bool IsUp,
        bool IsVirtual,
        bool HasGateway,
        NetworkInterfaceType InterfaceType,
        IReadOnlyList<RawAddressOption> Addresses)
    {
        public bool CanUseWhenBound =>
            IsUp &&
            InterfaceType is not NetworkInterfaceType.Loopback &&
            Addresses.Count > 0;

        public bool CanUseAutomatically =>
            CanUseWhenBound &&
            InterfaceType is not NetworkInterfaceType.Tunnel;
    }
}

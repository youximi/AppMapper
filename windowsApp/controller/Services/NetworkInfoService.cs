using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace AppMapper.Controller.Services;

public static class NetworkInfoService
{
    public static string GetPrimaryIPv4()
    {
        foreach (var networkInterface in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (networkInterface.OperationalStatus != OperationalStatus.Up) continue;
            if (networkInterface.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;

            foreach (var address in networkInterface.GetIPProperties().UnicastAddresses)
            {
                if (address.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !IPAddress.IsLoopback(address.Address))
                {
                    return address.Address.ToString();
                }
            }
        }

        return "127.0.0.1";
    }
}

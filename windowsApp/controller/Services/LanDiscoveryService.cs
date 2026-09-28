using System.Net;
using System.Net.Sockets;
using System.Text;

namespace AppMapper.Controller.Services;

/// <summary>Locates this computer after its LAN address changes. No credentials travel over UDP.</summary>
public sealed class LanDiscoveryService : IDisposable
{
    public const int DiscoveryPort = 8766;
    private readonly UdpClient socket;
    private readonly CancellationTokenSource cancellation = new();

    public LanDiscoveryService(string serverId, int tcpPort)
    {
        socket = new UdpClient(new IPEndPoint(IPAddress.Any, DiscoveryPort));
        _ = ReplyLoop(serverId, tcpPort);
    }

    private async Task ReplyLoop(string serverId, int tcpPort)
    {
        var query = $"appmapper-discover-v2|{serverId}";
        var reply = Encoding.ASCII.GetBytes($"appmapper-discovery-v2|{serverId}|{tcpPort}");
        while (!cancellation.IsCancellationRequested)
        {
            try
            {
                var request = await socket.ReceiveAsync(cancellation.Token);
                if (request.Buffer.Length <= 256 && Encoding.ASCII.GetString(request.Buffer) == query)
                    await socket.SendAsync(reply, reply.Length, request.RemoteEndPoint);
            }
            catch (OperationCanceledException) { return; }
            catch (ObjectDisposedException) when (cancellation.IsCancellationRequested) { return; }
            catch (SocketException) { return; }
        }
    }

    public void Dispose()
    {
        cancellation.Cancel();
        socket.Dispose();
        cancellation.Dispose();
    }
}

using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using AppMapper.Controller.Core;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.Services;

public sealed class TcpJsonServer
{
    private readonly LogService log;
    private TcpListener? listener;
    private CancellationTokenSource? cancellation;
    private int activeClients;

    public TcpJsonServer(LogService log) => this.log = log;

    public event Action<string, string, string>? HelloReceived;
    public event Action<string, AppInfo, long>? ActiveAppReceived;
    public event Action<string, string, long>? IdleReceived;
    public event Action<string>? Disconnected;

    public bool IsRunning => listener != null;

    public void Start(int port, Func<string?, bool> validateCode, int maxDevices)
    {
        Stop();
        cancellation = new CancellationTokenSource();
        listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        _ = AcceptLoop(validateCode, maxDevices, cancellation.Token);
        log.Info($"TCP server started on port {port}.");
    }

    public void Stop()
    {
        cancellation?.Cancel();
        listener?.Stop();
        listener = null;
    }

    private async Task AcceptLoop(Func<string?, bool> validateCode, int maxDevices, CancellationToken token)
    {
        while (!token.IsCancellationRequested && listener != null)
        {
            try
            {
                var client = await listener.AcceptTcpClientAsync(token);
                _ = HandleClient(client, validateCode, maxDevices, token);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception ex)
            {
                log.Warn($"Accept failed: {ex.Message}");
            }
        }
    }

    private async Task HandleClient(TcpClient client, Func<string?, bool> validateCode, int maxDevices, CancellationToken token)
    {
        string? deviceId = null;
        var countedClient = false;
        await using var stream = client.GetStream();
        using var reader = new StreamReader(stream, Encoding.UTF8, detectEncodingFromByteOrderMarks: false, bufferSize: 1024, leaveOpen: true);
        await using var writer = new StreamWriter(stream, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false), bufferSize: 1024, leaveOpen: true) { AutoFlush = true };

        try
        {
            var firstLine = await reader.ReadLineAsync(token);
            if (firstLine == null) return;

            using var helloDocument = JsonDocument.Parse(firstLine);
            var hello = helloDocument.RootElement;
            if (hello.GetProperty("type").GetString() != "hello")
            {
                await SendError(writer, "invalid_message", "First message must be hello.");
                return;
            }

            var code = hello.TryGetProperty("pairingCode", out var codeElement) ? codeElement.GetString() : null;
            if (!validateCode(code))
            {
                await SendError(writer, "invalid_pairing_code", "Pairing code is invalid or expired.");
                return;
            }

            if (Interlocked.Increment(ref activeClients) > maxDevices)
            {
                Interlocked.Decrement(ref activeClients);
                await SendError(writer, "max_devices_reached", "Maximum connected devices reached.");
                return;
            }
            countedClient = true;

            deviceId = hello.GetProperty("deviceId").GetString() ?? "";
            var deviceName = hello.TryGetProperty("deviceName", out var nameElement) ? nameElement.GetString() ?? deviceId : deviceId;
            HelloReceived?.Invoke(deviceId, deviceName, client.Client.RemoteEndPoint?.ToString() ?? "");
            await writer.WriteLineAsync(CreateHelloAck());

            while (!token.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(token);
                if (line == null) return;
                HandleMessage(deviceId, line);
            }
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            log.Warn($"Client error: {ex.Message}");
        }
        finally
        {
            if (countedClient) Interlocked.Decrement(ref activeClients);
            if (!string.IsNullOrWhiteSpace(deviceId)) Disconnected?.Invoke(deviceId);
            client.Close();
        }
    }

    private void HandleMessage(string deviceId, string line)
    {
        using var document = JsonDocument.Parse(line);
        var root = document.RootElement;
        var type = root.GetProperty("type").GetString();

        if (type == "active_app")
        {
            var app = root.GetProperty("app");
            var appInfo = new AppInfo
            {
                AppId = app.GetProperty("appId").GetString() ?? "",
                PackageName = app.GetProperty("packageName").GetString() ?? "",
                DisplayName = app.GetProperty("displayName").GetString() ?? "",
                IconPngBase64 = app.TryGetProperty("iconPngBase64", out var icon) ? icon.GetString() : null,
            };
            ActiveAppReceived?.Invoke(deviceId, appInfo, ReadSequence(root));
            return;
        }

        if (type == "idle")
        {
            var reason = root.TryGetProperty("reason", out var reasonElement) ? reasonElement.GetString() ?? "unknown" : "unknown";
            IdleReceived?.Invoke(deviceId, reason, ReadSequence(root));
            return;
        }
    }

    private static long ReadSequence(JsonElement root) =>
        root.TryGetProperty("sequence", out var sequence) ? sequence.GetInt64() : 0;

    private static string CreateHelloAck() =>
        JsonSerializer.Serialize(new
        {
            type = "hello_ack",
            protocolVersion = 1,
            serverName = Environment.MachineName,
            maxDevices = 3,
            accepted = true,
            message = "ok",
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
        });

    private static Task SendError(StreamWriter writer, string code, string message) =>
        writer.WriteLineAsync(JsonSerializer.Serialize(new
        {
            type = "error",
            code,
            message,
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
        }));
}

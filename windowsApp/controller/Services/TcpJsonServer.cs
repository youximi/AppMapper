using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using AppMapper.Controller.Core;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.Services;

public sealed class TcpJsonServer
{
    private readonly LogService log;
    private readonly PairingStore pairing;
    private readonly DeviceIdentityService identity;
    private readonly object sessionsLock = new();
    private readonly Dictionary<string, TcpClient> sessions = new();
    private readonly ConcurrentDictionary<string, (int Count, DateTimeOffset Since)> failedCodes = new();
    private TcpListener? listener;
    private CancellationTokenSource? cancellation;

    public TcpJsonServer(LogService log, PairingStore pairing, DeviceIdentityService identity)
    {
        this.log = log;
        this.pairing = pairing;
        this.identity = identity;
    }

    public event Action<string, string, string, bool>? HelloReceived;
    public event Action<string, AppInfo, long>? ActiveAppReceived;
    public event Action<string, string, long>? IdleReceived;
    public event Action<string>? Disconnected;
    public event Action? PairingChanged;

    public bool IsRunning => listener != null;

    public void Start(int port, Func<string?, bool> consumeManualCode,
        Func<string?, bool> consumeEnrollToken, int maxDevices)
    {
        Stop();
        cancellation = new CancellationTokenSource();
        listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        _ = AcceptLoop(listener, consumeManualCode, consumeEnrollToken, maxDevices, cancellation.Token);
        log.Info($"TCP server started on port {port}.");
    }

    public void Stop()
    {
        cancellation?.Cancel();
        listener?.Stop();
        listener = null;
        string[] disconnected;
        lock (sessionsLock)
        {
            disconnected = sessions.Keys.ToArray();
            foreach (var client in sessions.Values) client.Close();
            sessions.Clear();
        }
        foreach (var id in disconnected) Disconnected?.Invoke(id);
    }

    public bool RemovePairedDevice(string deviceId)
    {
        bool disconnected;
        lock (sessionsLock)
        {
            if (!pairing.Remove(deviceId)) return false;
            disconnected = sessions.Remove(deviceId, out var client);
            if (disconnected) client!.Close();
        }
        if (disconnected) Disconnected?.Invoke(deviceId);
        PairingChanged?.Invoke();
        return true;
    }

    private async Task AcceptLoop(TcpListener activeListener, Func<string?, bool> consumeManualCode,
        Func<string?, bool> consumeEnrollToken,
        int maxDevices, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var client = await activeListener.AcceptTcpClientAsync(token);
                _ = HandleClient(client, consumeManualCode, consumeEnrollToken, maxDevices, token);
            }
            catch (OperationCanceledException) { return; }
            catch (ObjectDisposedException) when (token.IsCancellationRequested) { return; }
            catch (SocketException) when (token.IsCancellationRequested) { return; }
            catch (Exception ex) { log.Warn($"Accept failed: {ex.GetType().Name}."); }
        }
    }

    private async Task HandleClient(TcpClient client, Func<string?, bool> consumeManualCode,
        Func<string?, bool> consumeEnrollToken,
        int maxDevices, CancellationToken token)
    {
        string? deviceId = null;
        var rememberedSession = false;
        var remote = (client.Client.RemoteEndPoint as IPEndPoint)?.Address.ToString() ?? "unknown";
        try
        {
            await using var ssl = new SslStream(client.GetStream(), leaveInnerStreamOpen: false);
            using var handshake = CancellationTokenSource.CreateLinkedTokenSource(token);
            handshake.CancelAfter(TimeSpan.FromSeconds(10));
            await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
            {
                ServerCertificate = identity.Certificate,
                EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
            }, handshake.Token);
            using var reader = new StreamReader(ssl, Encoding.UTF8, false, 1024, true);
            await using var writer = new StreamWriter(ssl, new UTF8Encoding(false), 1024, true) { AutoFlush = true };
            var firstLine = await reader.ReadLineAsync(handshake.Token);
            if (firstLine == null || firstLine.Length > 8192) return;
            using var helloDocument = JsonDocument.Parse(firstLine);
            var hello = helloDocument.RootElement;
            if (ReadString(hello, "type") != "hello" || ReadInt(hello, "protocolVersion") != 2)
            {
                await SendError(writer, "upgrade_required", "Please update both AppMapper apps.");
                return;
            }
            var requestedId = ReadString(hello, "deviceId");
            if (string.IsNullOrWhiteSpace(requestedId) || requestedId.Length > 100) return;
            var name = ReadString(hello, "deviceName") ?? requestedId;
            if (name.Length > 100) name = name[..100];

            var mode = ReadString(hello, "mode");
            if (mode is "pair_temporary" or "pair_remember")
            {
                var remember = mode == "pair_remember";
                var credential = ReadString(hello, remember ? "enrollToken" : "pairingCode");
                var invalidCredential = remember ? "invalid_enroll_token" : "invalid_pairing_code";
                if (!remember && !CanTryCode(remote))
                {
                    await SendError(writer, "invalid_pairing_code", "Pairing code is invalid or expired.");
                    return;
                }
                var publicKey = ReadString(hello, "publicKeySpki");
                if (remember && (publicKey == null || !DeviceIdentityService.IsValidPublicKey(publicKey)))
                {
                    await SendError(writer, "invalid_key", "Device key is invalid.");
                    return;
                }
                var failure = RegisterPair(requestedId, name, credential, publicKey,
                    remember, client, maxDevices, remember ? consumeEnrollToken : consumeManualCode,
                    invalidCredential);
                if (failure != null)
                {
                    if (failure == "invalid_pairing_code") RecordFailedCode(remote);
                    await SendError(writer, failure, failure == "max_devices_reached"
                        ? "Maximum connected devices reached." : "Pairing credential is invalid or expired.");
                    return;
                }
                deviceId = requestedId;
                rememberedSession = remember;
                if (remember) PairingChanged?.Invoke();
                await SendAck(writer);
            }
            else if (mode == "resume")
            {
                var saved = pairing.Find(requestedId);
                if (saved == null)
                {
                    await SendError(writer, "pairing_required", "Pair again on this computer.");
                    return;
                }
                var nonce = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
                await writer.WriteLineAsync(JsonSerializer.Serialize(new { type = "challenge", nonce }));
                var proofLine = await reader.ReadLineAsync(handshake.Token);
                if (proofLine == null || proofLine.Length > 8192) return;
                using var proofDocument = JsonDocument.Parse(proofLine);
                var proof = proofDocument.RootElement;
                var signature = ReadString(proof, "signature");
                if (ReadString(proof, "type") != "proof" || signature == null ||
                    !DeviceIdentityService.Verify(saved.PublicKeySpki,
                        DeviceIdentityService.ProofPayload(identity.ServerId, requestedId, nonce), signature))
                {
                    await SendError(writer, "authentication_failed", "Device authentication failed.");
                    return;
                }
                deviceId = requestedId;
                var failure = RegisterResume(deviceId, saved.PublicKeySpki, client, maxDevices);
                if (failure != null)
                {
                    await SendError(writer, failure, failure == "pairing_required"
                        ? "Pair again on this computer." : "Maximum connected devices reached.");
                    return;
                }
                rememberedSession = true;
                await SendAck(writer);
            }
            else
            {
                await SendError(writer, "invalid_message", "Unknown connection mode.");
                return;
            }

            HelloReceived?.Invoke(deviceId, name, remote, rememberedSession);
            while (!token.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(token);
                if (line == null) return;
                if (!IsCurrent(deviceId, client)) return;
                using var document = JsonDocument.Parse(line);
                var root = document.RootElement;
                switch (ReadString(root, "type"))
                {
                    case "active_app":
                        var app = root.GetProperty("app");
                        ActiveAppReceived?.Invoke(deviceId, new AppInfo
                        {
                            AppId = ReadString(app, "appId") ?? "",
                            PackageName = ReadString(app, "packageName") ?? "",
                            DisplayName = ReadString(app, "displayName") ?? "",
                            IconPngBase64 = ReadString(app, "iconPngBase64"),
                        }, ReadLong(root, "sequence"));
                        break;
                    case "idle":
                        IdleReceived?.Invoke(deviceId, ReadString(root, "reason") ?? "unknown",
                            ReadLong(root, "sequence"));
                        break;
                    case "heartbeat":
                        await writer.WriteLineAsync("{\"type\":\"heartbeat_ack\"}");
                        break;
                    case "forget":
                        if (rememberedSession) RemovePairedDevice(deviceId);
                        return;
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            log.Warn($"Client connection ended: {ex.GetType().Name}.");
        }
        finally
        {
            var disconnected = false;
            if (deviceId != null)
            {
                lock (sessionsLock)
                {
                    if (sessions.TryGetValue(deviceId, out var current) && ReferenceEquals(current, client))
                    {
                        sessions.Remove(deviceId);
                        disconnected = true;
                    }
                }
            }
            client.Close();
            if (disconnected) Disconnected?.Invoke(deviceId!);
        }
    }

    private string? RegisterPair(string id, string name, string? credential, string? publicKey,
        bool remember, TcpClient client, int maxDevices, Func<string?, bool> consumeCredential,
        string invalidCredential)
    {
        lock (sessionsLock)
        {
            if (!sessions.ContainsKey(id) && sessions.Count >= maxDevices) return "max_devices_reached";
            if (!consumeCredential(credential)) return invalidCredential;
            if (remember)
                pairing.Pair(new PairedDeviceRecord(id, name, publicKey!, DateTimeOffset.Now));
            if (sessions.TryGetValue(id, out var previous)) previous.Close();
            sessions[id] = client;
            return null;
        }
    }

    private string? RegisterResume(string id, string publicKey, TcpClient client, int maxDevices)
    {
        lock (sessionsLock)
        {
            if (pairing.Find(id)?.PublicKeySpki != publicKey) return "pairing_required";
            if (!sessions.ContainsKey(id) && sessions.Count >= maxDevices) return "max_devices_reached";
            if (sessions.TryGetValue(id, out var previous)) previous.Close();
            sessions[id] = client;
            return null;
        }
    }

    private bool IsCurrent(string id, TcpClient client)
    {
        lock (sessionsLock) return sessions.TryGetValue(id, out var current) && ReferenceEquals(current, client);
    }

    private bool CanTryCode(string address) =>
        !failedCodes.TryGetValue(address, out var attempts) ||
        DateTimeOffset.UtcNow - attempts.Since > TimeSpan.FromMinutes(1) || attempts.Count < 5;

    private void RecordFailedCode(string address) => failedCodes.AddOrUpdate(address,
        _ => (1, DateTimeOffset.UtcNow), (_, previous) =>
            DateTimeOffset.UtcNow - previous.Since > TimeSpan.FromMinutes(1)
                ? (1, DateTimeOffset.UtcNow) : (previous.Count + 1, previous.Since));

    private Task SendAck(StreamWriter writer) => writer.WriteLineAsync(JsonSerializer.Serialize(new
    {
        type = "hello_ack", accepted = true, serverId = identity.ServerId,
        serverName = Environment.MachineName,
    }));

    private static Task SendError(StreamWriter writer, string code, string message) =>
        writer.WriteLineAsync(JsonSerializer.Serialize(new { type = "error", code, message }));

    private static string? ReadString(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;
    private static int ReadInt(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.TryGetInt32(out var result) ? result : 0;
    private static long ReadLong(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) && value.TryGetInt64(out var result) ? result : 0;
}

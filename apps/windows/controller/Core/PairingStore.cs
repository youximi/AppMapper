using System.IO;
using System.Security.Cryptography;
using System.Text.Json;

namespace AppMapper.Controller.Core;

public sealed record PairedDeviceRecord(string DeviceId, string DeviceName, string PublicKeySpki, DateTimeOffset LastSeen);

/// <summary>One protected, durable source of truth for this computer's identity and paired phones.</summary>
public sealed class PairingStore
{
    private readonly string path;
    private readonly object gate = new();
    private PairingData data;

    public PairingStore(string baseDirectory)
    {
        var directory = Path.Combine(baseDirectory, "config");
        Directory.CreateDirectory(directory);
        path = Path.Combine(directory, "pairing.dat");
        if (File.Exists(path))
        {
            var protectedBytes = File.ReadAllBytes(path);
            var bytes = ProtectedData.Unprotect(protectedBytes, null, DataProtectionScope.CurrentUser);
            data = JsonSerializer.Deserialize<PairingData>(bytes)
                ?? throw new InvalidDataException("Pairing data is empty.");
            if (string.IsNullOrWhiteSpace(data.ServerId) || string.IsNullOrWhiteSpace(data.PrivateKeyPkcs8))
                throw new InvalidDataException("Pairing identity is incomplete.");
        }
        else
        {
            using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
            data = new PairingData(Guid.NewGuid().ToString("N"),
                Convert.ToBase64String(key.ExportPkcs8PrivateKey()), new Dictionary<string, PairedDeviceRecord>());
            Save();
        }
    }

    public string ServerId => data.ServerId;
    public byte[] PrivateKeyPkcs8 => Convert.FromBase64String(data.PrivateKeyPkcs8);

    public IReadOnlyList<PairedDeviceRecord> GetDevices()
    {
        lock (gate) return data.Devices.Values.ToList();
    }

    public PairedDeviceRecord? Find(string deviceId)
    {
        lock (gate) return data.Devices.GetValueOrDefault(deviceId);
    }

    public void Pair(PairedDeviceRecord device)
    {
        lock (gate)
        {
            var next = new Dictionary<string, PairedDeviceRecord>(data.Devices) { [device.DeviceId] = device };
            Save(new PairingData(data.ServerId, data.PrivateKeyPkcs8, next));
        }
    }

    public bool Remove(string deviceId)
    {
        lock (gate)
        {
            if (!data.Devices.ContainsKey(deviceId)) return false;
            var next = new Dictionary<string, PairedDeviceRecord>(data.Devices);
            next.Remove(deviceId);
            Save(new PairingData(data.ServerId, data.PrivateKeyPkcs8, next));
            return true;
        }
    }

    public void Seen(string deviceId)
    {
        lock (gate)
        {
            if (!data.Devices.TryGetValue(deviceId, out var device)) return;
            var next = new Dictionary<string, PairedDeviceRecord>(data.Devices)
            {
                [deviceId] = device with { LastSeen = DateTimeOffset.Now },
            };
            Save(new PairingData(data.ServerId, data.PrivateKeyPkcs8, next));
        }
    }

    private void Save(PairingData? next = null)
    {
        next ??= data;
        var encrypted = ProtectedData.Protect(JsonSerializer.SerializeToUtf8Bytes(next), null,
            DataProtectionScope.CurrentUser);
        var temporary = path + ".tmp";
        try
        {
            File.WriteAllBytes(temporary, encrypted);
            File.Move(temporary, path, true);
            data = next;
        }
        finally
        {
            if (File.Exists(temporary)) File.Delete(temporary);
        }
    }

    private sealed record PairingData(string ServerId, string PrivateKeyPkcs8,
        Dictionary<string, PairedDeviceRecord> Devices);
}

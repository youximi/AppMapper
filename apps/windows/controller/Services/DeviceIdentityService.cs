using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using AppMapper.Controller.Core;

namespace AppMapper.Controller.Services;

public sealed class DeviceIdentityService : IDisposable
{
    private readonly ECDsa privateKey;

    public DeviceIdentityService(PairingStore store)
    {
        ServerId = store.ServerId;
        privateKey = ECDsa.Create();
        privateKey.ImportPkcs8PrivateKey(store.PrivateKeyPkcs8, out _);
        Fingerprint = Convert.ToHexString(SHA256.HashData(privateKey.ExportSubjectPublicKeyInfo())).ToLowerInvariant();
        var request = new CertificateRequest("CN=AppMapper", privateKey, HashAlgorithmName.SHA256);
        using var generated = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(5));
        Certificate = new X509Certificate2(generated.Export(X509ContentType.Pkcs12), (string?)null,
            X509KeyStorageFlags.UserKeySet);
    }

    public string ServerId { get; }
    public string Fingerprint { get; }
    public X509Certificate2 Certificate { get; }

    public static byte[] ProofPayload(string serverId, string deviceId, string nonce) =>
        Encoding.UTF8.GetBytes($"appmapper-v2|{serverId}|{deviceId}|{nonce}");

    public static bool Verify(string publicKeySpki, byte[] payload, string signature)
    {
        using var key = ECDsa.Create();
        key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKeySpki), out _);
        return key.VerifyData(payload, Convert.FromBase64String(signature), HashAlgorithmName.SHA256,
            DSASignatureFormat.Rfc3279DerSequence);
    }

    public static bool IsValidPublicKey(string publicKeySpki)
    {
        try
        {
            using var key = ECDsa.Create();
            key.ImportSubjectPublicKeyInfo(Convert.FromBase64String(publicKeySpki), out _);
            return key.KeySize == 256;
        }
        catch (Exception ex) when (ex is FormatException or CryptographicException or ArgumentException)
        {
            return false;
        }
    }

    public void Dispose()
    {
        Certificate.Dispose();
        privateKey.Dispose();
    }
}

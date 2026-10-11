namespace AppMapper.Controller.Services;

public sealed class PairingCodeService : IDisposable
{
    private readonly System.Threading.Timer timer;
    private readonly object gate = new();
    private string currentCode;
    private string currentEnrollToken;

    public PairingCodeService()
    {
        currentCode = CreateCode();
        currentEnrollToken = CreateEnrollToken();
        timer = new System.Threading.Timer(_ => Refresh(), null, TimeSpan.FromMinutes(1), TimeSpan.FromMinutes(1));
    }

    public (string Code, string EnrollToken) Current
    {
        get { lock (gate) return (currentCode, currentEnrollToken); }
    }

    public event Action? Changed;

    public bool TryConsumeManual(string? code) => TryConsume(code, enrollment: false);

    public bool TryConsumeEnrollment(string? token) => TryConsume(token, enrollment: true);

    private bool TryConsume(string? value, bool enrollment)
    {
        lock (gate)
        {
            if (value == null || value != (enrollment ? currentEnrollToken : currentCode)) return false;
            Rotate();
        }
        Changed?.Invoke();
        return true;
    }

    public void Refresh()
    {
        lock (gate) Rotate();
        Changed?.Invoke();
    }

    public void Dispose() => timer.Dispose();

    private void Rotate()
    {
        currentCode = CreateCode();
        currentEnrollToken = CreateEnrollToken();
    }

    private static string CreateCode() =>
        System.Security.Cryptography.RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    private static string CreateEnrollToken() =>
        Convert.ToHexString(System.Security.Cryptography.RandomNumberGenerator.GetBytes(16)).ToLowerInvariant();
}

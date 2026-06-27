namespace AppMapper.Controller.Services;

public sealed class PairingCodeService : IDisposable
{
    private readonly System.Threading.Timer timer;
    private readonly Random random = new();

    public PairingCodeService()
    {
        CurrentCode = CreateCode();
        timer = new System.Threading.Timer(_ => Refresh(), null, TimeSpan.FromMinutes(1), TimeSpan.FromMinutes(1));
    }

    public string CurrentCode { get; private set; }

    public event Action<string>? CodeChanged;

    public bool IsValid(string? code) => code == CurrentCode;

    public void Refresh()
    {
        CurrentCode = CreateCode();
        CodeChanged?.Invoke(CurrentCode);
    }

    public void Dispose() => timer.Dispose();

    private string CreateCode() => random.Next(0, 1_000_000).ToString("D6");
}

namespace AppMapper.Controller.Models;

public sealed class DeviceState
{
    public required string DeviceId { get; init; }
    public string DeviceName { get; set; } = "";
    public string State { get; set; } = "Connected";
    public bool IsPaired { get; set; }
    public string CurrentApp { get; set; } = "";
    public DateTime LastSeen { get; set; } = DateTime.Now;
    public long LastSequence { get; set; }
}

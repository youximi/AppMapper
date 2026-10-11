namespace AppMapper.Controller.Models;

public sealed class AppInfo
{
    public required string AppId { get; init; }
    public required string PackageName { get; init; }
    public required string DisplayName { get; init; }
    public string? IconPngBase64 { get; init; }
}

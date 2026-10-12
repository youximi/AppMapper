namespace AppMapper.Controller.Models;

public enum UpdateCheckPhase { Idle, Checking, Current, Available, Failed }

public sealed record AppUpdateInfo(string VersionName, int BuildNumber, string Notes, string ReleaseUrl)
{
    public bool IsNewerThan(int? installedBuild) => installedBuild.HasValue && BuildNumber > installedBuild.Value;
}

public sealed record UpdateCheckState(
    UpdateCheckPhase Phase = UpdateCheckPhase.Idle,
    AppUpdateInfo? Release = null,
    string Message = "尚未检查更新")
{
    public bool IsChecking => Phase == UpdateCheckPhase.Checking;
    public bool HasUpdate => Phase == UpdateCheckPhase.Available;
}

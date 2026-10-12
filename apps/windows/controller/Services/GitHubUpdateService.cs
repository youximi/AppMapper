using System.IO;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Text.Json;
using System.Text.RegularExpressions;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.Services;

public sealed class GitHubUpdateService : IDisposable
{
    public static string InstalledVersion { get; } = typeof(GitHubUpdateService).Assembly
        .GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion ?? "0.1.0";

    public static string InstalledVersionName => typeof(GitHubUpdateService).Assembly
        .GetName().Version?.ToString(3) ?? "0.1.0";

    private readonly HttpClient http;
    private readonly CancellationTokenSource lifetime = new();
    private readonly object gate = new();
    private volatile UpdateCheckState state = new();
    private bool disposed;

    public UpdateCheckState State => state;
    public event Action<UpdateCheckState>? Changed;

    public GitHubUpdateService(HttpClient? client = null)
    {
        http = client ?? new HttpClient();
        http.Timeout = TimeSpan.FromSeconds(10);
        http.DefaultRequestHeaders.UserAgent.ParseAdd("AppMapper/" + InstalledVersion);
        http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        http.DefaultRequestHeaders.Add("X-GitHub-Api-Version", "2022-11-28");
    }

    public async Task<UpdateCheckState> CheckAsync()
    {
        UpdateCheckState checking;
        CancellationToken token;
        lock (gate)
        {
            if (disposed || state.IsChecking) return state;
            checking = state = state with { Phase = UpdateCheckPhase.Checking, Message = "正在检查更新…" };
            token = lifetime.Token;
        }
        Changed?.Invoke(checking);

        UpdateCheckState result;
        try
        {
            using var response = await http.GetAsync(
                "https://api.github.com/repos/youximi/AppMapper/releases/latest", token).ConfigureAwait(false);
            if (response.StatusCode == HttpStatusCode.NotFound)
                throw new HttpRequestException("尚未找到正式发布的版本，请稍后重试");
            if (response.StatusCode is HttpStatusCode.Forbidden or HttpStatusCode.TooManyRequests)
                throw new HttpRequestException("GitHub 请求受限，请稍后手动重试");
            if (!response.IsSuccessStatusCode)
                throw new HttpRequestException($"GitHub 请求失败（HTTP {(int)response.StatusCode}），请稍后重试");

            var release = ParseRelease(await response.Content.ReadAsStringAsync(token).ConfigureAwait(false));
            var installedBuild = ParseInstalledBuild(InstalledVersion);
            var newer = release.IsNewerThan(installedBuild);
            result = new(newer ? UpdateCheckPhase.Available : UpdateCheckPhase.Current, release,
                installedBuild is null ? $"开发构建：可查看最新正式版 {release.VersionName}" :
                newer ? $"发现新版本 {release.VersionName}" : "已是最新版本");
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { return state; }
        catch (OperationCanceledException)
        {
            result = new(UpdateCheckPhase.Failed, Message: "检查更新超时，请稍后重试");
        }
        catch (HttpRequestException ex)
        {
            result = new(UpdateCheckPhase.Failed, Message: ex.StatusCode is null &&
                ex.InnerException is null ? ex.Message : "无法连接 GitHub，请检查网络后重试");
        }
        catch (Exception ex) when (ex is JsonException or InvalidDataException or InvalidOperationException or FormatException or IOException or KeyNotFoundException)
        {
            result = new(UpdateCheckPhase.Failed, Message: "发布信息不完整或格式无法识别，请稍后重试");
        }

        lock (gate)
        {
            if (disposed) return state;
            state = result;
        }
        Changed?.Invoke(result);
        return result;
    }

    public static int? ParseInstalledBuild(string version)
    {
        var match = Regex.Match(version, @"^Version\.\d+\.\d+\.\d+\.(?:Release|Experiment_C)([1-9]\d*)(?:\+.*)?$");
        return match.Success && int.TryParse(match.Groups[1].Value, out var build) ? build : null;
    }

    public static AppUpdateInfo ParseRelease(string json)
    {
        using var document = JsonDocument.Parse(json);
        var release = document.RootElement;
        if (release.GetProperty("draft").GetBoolean() || release.GetProperty("prerelease").GetBoolean())
            throw new InvalidDataException("Not a published stable release.");
        var tag = release.GetProperty("tag_name").GetString() ?? "";
        var match = Regex.Match(tag, @"^Version\.(\d+\.\d+\.\d+)\.Release([1-9]\d*)$");
        if (!match.Success || !int.TryParse(match.Groups[2].Value, out var build))
            throw new InvalidDataException("Unknown release version.");
        var version = match.Groups[1].Value;
        var assetName = $"appmapper.windows.{version}.zip";
        var legacyAssetName = $"appmapper.windows.{version}.{build}.zip";
        if (!release.GetProperty("assets").EnumerateArray().Any(asset =>
        {
            var name = asset.GetProperty("name").GetString();
            return (name == assetName || name == legacyAssetName) &&
                asset.GetProperty("state").GetString() == "uploaded" && asset.GetProperty("size").GetInt64() > 0;
        }))
            throw new InvalidDataException("Windows package has not been uploaded.");
        var body = release.TryGetProperty("body", out var notes) && notes.ValueKind == JsonValueKind.String
            ? notes.GetString() ?? "" : "";
        return new(version, build, ExtractNotes(body), "https://github.com/youximi/AppMapper/releases/tag/" + tag);
    }

    public static string ExtractNotes(string body)
    {
        var normalized = body.Replace("\r\n", "\n").Trim();
        const string marker = "Changes since last build\n";
        var start = normalized.IndexOf(marker, StringComparison.Ordinal);
        var changes = start < 0 ? normalized : normalized[(start + marker.Length)..];
        var end = start < 0 ? -1 : changes.LastIndexOf("\nFull Comparison:", StringComparison.Ordinal);
        var text = (end < 0 ? changes : changes[..end]).Trim();
        return string.IsNullOrWhiteSpace(text) ? "此版本未提供更新说明" : text;
    }

    public void Dispose()
    {
        lock (gate)
        {
            if (disposed) return;
            disposed = true;
        }
        lifetime.Cancel();
        http.Dispose();
        lifetime.Dispose();
    }
}

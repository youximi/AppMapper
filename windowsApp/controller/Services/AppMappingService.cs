using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.RegularExpressions;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.Services;

public sealed class AppMappingService
{
    private readonly string appRoot;
    private readonly string templateExe;
    private Process? currentProcess;
    private string? currentAppId;

    public AppMappingService()
    {
        var baseDir = AppContext.BaseDirectory;
        appRoot = Path.Combine(baseDir, "apps");
        templateExe = Path.Combine(baseDir, "mapper-template.exe");
        Directory.CreateDirectory(appRoot);
    }

    public string AppsDirectory => appRoot;

    public bool RelaunchWhenClosed { get; set; } = true;

    public event Action<string>? Log;

    public void Show(AppInfo app)
    {
        var mapping = EnsureMapping(app);
        if (currentAppId == app.AppId && currentProcess is { HasExited: false })
        {
            ActivateMapperWindow(currentProcess);
            return;
        }

        CloseCurrent();
        currentAppId = app.AppId;
        currentProcess = Process.Start(new ProcessStartInfo
        {
            FileName = mapping.ExePath,
            WorkingDirectory = mapping.Directory,
            UseShellExecute = true,
        });

        if (currentProcess != null)
        {
            currentProcess.EnableRaisingEvents = true;
            currentProcess.Exited += (_, _) =>
            {
                if (RelaunchWhenClosed && currentAppId == app.AppId)
                {
                    Log?.Invoke($"Mapper closed by user; relaunching {app.DisplayName}.");
                    Show(app);
                }
            };

            ActivateMapperWindow(currentProcess);
        }

        Log?.Invoke($"Showing mapper for {app.DisplayName}.");
    }

    public void CloseCurrent()
    {
        currentAppId = null;
        if (currentProcess is { HasExited: false })
        {
            currentProcess.Kill(entireProcessTree: true);
            currentProcess.WaitForExit(1500);
        }

        currentProcess = null;
    }

    private MappingPaths EnsureMapping(AppInfo app)
    {
        var directoryName = SafeFileName(app.DisplayName);
        var directory = Path.Combine(appRoot, directoryName);
        Directory.CreateDirectory(directory);

        var exeName = $"{directoryName}.exe";
        var exePath = Path.Combine(directory, exeName);
        var pngPath = Path.Combine(directory, "icon.png");
        var icoPath = Path.Combine(directory, "icon.ico");
        var jsonPath = Path.Combine(directory, "app.json");

        if (!File.Exists(exePath))
        {
            if (!File.Exists(templateExe))
                throw new FileNotFoundException("mapper-template.exe not found. Build mapper first.", templateExe);
            File.Copy(templateExe, exePath, overwrite: true);
        }

        if (!string.IsNullOrWhiteSpace(app.IconPngBase64))
        {
            var pngBytes = Convert.FromBase64String(app.IconPngBase64);
            File.WriteAllBytes(pngPath, pngBytes);
            var icoBytes = IconResourceUpdater.CreateIcoFromPng(pngBytes);
            File.WriteAllBytes(icoPath, icoBytes);
            IconResourceUpdater.WriteIconResource(exePath, icoBytes);
        }

        File.WriteAllText(
            jsonPath,
            JsonSerializer.Serialize(
                new
                {
                    appId = app.AppId,
                    packageName = app.PackageName,
                    displayName = app.DisplayName,
                    exeName,
                    iconPath = "icon.ico",
                    createdAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                    updatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                },
                new JsonSerializerOptions { WriteIndented = true })
        );

        return new MappingPaths(directory, exePath);
    }

    private static void ActivateMapperWindow(Process process)
    {
        var hwnd = WaitForMainWindow(process, TimeSpan.FromSeconds(2));
        if (hwnd == IntPtr.Zero) return;

        var currentThread = GetCurrentThreadId();
        var foregroundWindow = GetForegroundWindow();
        var foregroundThread = foregroundWindow != IntPtr.Zero
            ? GetWindowThreadProcessId(foregroundWindow, out _)
            : 0;
        var attachedToForeground = foregroundThread != 0
            && foregroundThread != currentThread
            && AttachThreadInput(currentThread, foregroundThread, true);

        ShowWindow(hwnd, SwShownormal);
        SetWindowPos(hwnd, HwndTopmost, 0, 0, 0, 0, SwpNoMove | SwpNoSize | SwpShowWindow);
        BringWindowToTop(hwnd);
        SetActiveWindow(hwnd);
        SetFocus(hwnd);

        if (!SetForegroundWindow(hwnd))
        {
            SendAltKey();
            SetForegroundWindow(hwnd);
        }

        if (attachedToForeground)
        {
            AttachThreadInput(currentThread, foregroundThread, false);
        }
    }

    private static IntPtr WaitForMainWindow(Process process, TimeSpan timeout)
    {
        try
        {
            process.WaitForInputIdle(1000);
        }
        catch (InvalidOperationException)
        {
        }

        var deadline = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < deadline)
        {
            if (process.HasExited) return IntPtr.Zero;
            process.Refresh();
            if (process.MainWindowHandle != IntPtr.Zero) return process.MainWindowHandle;
            Thread.Sleep(50);
        }

        return IntPtr.Zero;
    }

    private static string SafeFileName(string value)
    {
        var invalid = Regex.Escape(new string(Path.GetInvalidFileNameChars()));
        var safe = Regex.Replace(value.Trim(), $"[{invalid}]+", "_");
        return string.IsNullOrWhiteSpace(safe) ? "AndroidApp" : safe;
    }

    private const int SwShownormal = 1;
    private const uint SwpNoSize = 0x0001;
    private const uint SwpNoMove = 0x0002;
    private const uint SwpShowWindow = 0x0040;
    private static readonly IntPtr HwndTopmost = new(-1);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int x, int y, int cx, int cy, uint flags);

    [DllImport("user32.dll")]
    private static extern bool BringWindowToTop(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("kernel32.dll")]
    private static extern uint GetCurrentThreadId();

    [DllImport("user32.dll")]
    private static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool attach);

    [DllImport("user32.dll")]
    private static extern IntPtr SetActiveWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern IntPtr SetFocus(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    private static void SendAltKey()
    {
        keybd_event(VkMenu, 0, 0, UIntPtr.Zero);
        keybd_event(VkMenu, 0, KeyEventFKeyUp, UIntPtr.Zero);
    }

    private const byte VkMenu = 0x12;
    private const uint KeyEventFKeyUp = 0x0002;

    private sealed record MappingPaths(string Directory, string ExePath);
}

using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.RegularExpressions;
using AppMapper.Controller.Core;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.Services;

public sealed partial class AppMappingService
{
    private readonly LogService log;
    private readonly string appRoot;
    private readonly string templateExe;
    private readonly object processLock = new();
    private Process? currentProcess;
    private string? currentAppId;
    private bool stopped;

    public AppMappingService(LogService log)
    {
        this.log = log;
        var baseDir = AppContext.BaseDirectory;
        appRoot = Path.Combine(baseDir, "apps");
        templateExe = Path.Combine(baseDir, "mapper-template.exe");
        Directory.CreateDirectory(appRoot);
    }

    public string AppsDirectory => appRoot;

    public bool RelaunchWhenClosed { get; set; } = true;

    public void Show(AppInfo app)
    {
        lock (processLock)
        {
            if (stopped) return;
            var mapping = EnsureMapping(app);
            if (currentAppId == app.AppId && currentProcess is { HasExited: false })
            {
                ActivateMapperWindow(currentProcess);
                return;
            }

            CloseCurrent();
            currentAppId = app.AppId;
            var process = Process.Start(new ProcessStartInfo
            {
                FileName = mapping.ExePath,
                WorkingDirectory = mapping.Directory,
                UseShellExecute = false,
                KillOnParentExit = true,
            });
            currentProcess = process;

            if (process != null)
            {
                // Process 在内部锁中触发 Exited，重开操作排队后再获取映射锁。
                process.Exited += (_, _) => ThreadPool.QueueUserWorkItem(_ =>
                {
                    lock (processLock)
                    {
                        if (!stopped && RelaunchWhenClosed && currentAppId == app.AppId
                            && ReferenceEquals(currentProcess, process))
                        {
                            log.Info($"Mapper closed by user; relaunching {app.DisplayName}.");
                            Show(app);
                        }
                    }
                });
                process.EnableRaisingEvents = true;

                ActivateMapperWindow(process);
            }

            log.Info($"Showing mapper for {app.DisplayName}.");
        }
    }

    public void CloseCurrent()
    {
        lock (processLock)
        {
            var process = currentProcess;
            currentProcess = null;
            currentAppId = null;
            if (process == null) return;

            using (process)
            {
                process.EnableRaisingEvents = false;
                if (!process.HasExited)
                {
                    try { process.Kill(entireProcessTree: true); }
                    catch (InvalidOperationException) when (process.HasExited) { }
                    if (!process.WaitForExit(1500))
                        throw new TimeoutException("Mapper process did not exit.");
                }
            }
        }
    }

    public void Stop()
    {
        lock (processLock)
        {
            stopped = true;
            CloseCurrent();
        }
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

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int x, int y, int cx, int cy, uint flags);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool BringWindowToTop(IntPtr hWnd);

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool SetForegroundWindow(IntPtr hWnd);

    [LibraryImport("user32.dll")]
    private static partial IntPtr GetForegroundWindow();

    [LibraryImport("user32.dll")]
    private static partial uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [LibraryImport("kernel32.dll")]
    private static partial uint GetCurrentThreadId();

    [LibraryImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static partial bool AttachThreadInput(uint idAttach, uint idAttachTo,
        [MarshalAs(UnmanagedType.Bool)] bool attach);

    [LibraryImport("user32.dll")]
    private static partial IntPtr SetActiveWindow(IntPtr hWnd);

    [LibraryImport("user32.dll")]
    private static partial IntPtr SetFocus(IntPtr hWnd);

    [LibraryImport("user32.dll", SetLastError = true)]
    private static partial uint SendInput(uint count,
        [In, MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 0)] Input[] inputs, int size);

    private static void SendAltKey()
    {
        Input[] inputs =
        [
            new() { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInput { VirtualKey = VkMenu } } },
            new() { Type = InputKeyboard, Data = new InputUnion { Keyboard = new KeyboardInput { VirtualKey = VkMenu, Flags = KeyEventFKeyUp } } },
        ];
        var size = Marshal.SizeOf<Input>();
        var sent = SendInput((uint)inputs.Length, inputs, size);
        // If only the key-down was inserted, release Alt before giving up.
        if (sent > 0 && sent < inputs.Length) SendInput(1, [inputs[1]], size);
    }

    private const uint InputKeyboard = 1;
    private const ushort VkMenu = 0x12;
    private const uint KeyEventFKeyUp = 0x0002;

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MouseInput Mouse;
        [FieldOffset(0)] public KeyboardInput Keyboard;
    }

    // MOUSEINPUT is the largest INPUT union member and determines its native size.
    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public nuint ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public nuint ExtraInfo;
    }

    private sealed record MappingPaths(string Directory, string ExePath);
}

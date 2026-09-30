using System.IO;
using System.Windows;
using System.Windows.Threading;
using AppMapper.Controller.Core;
using Application = System.Windows.Application;

namespace AppMapper.Controller;

/// <summary>
/// 应用入口。构造核心层（日志/配置/引擎）并暴露为静态 <see cref="Core"/> 供 UI 取用。
/// UI 与业务彻底分层：UI 只通过 <see cref="AppMapper.Core.AppMapperCoreEngine"/>（实现 ICoreFacade）访问业务。
/// 单 PID：核心跑在同进程后台线程，UI 卡死不影响业务，恢复后重取快照。
/// </summary>
public partial class App : Application
{
    private System.Threading.Mutex? singleInstance;
    private bool ownsSingleInstance;
    /// <summary>核心门面，UI 唯一业务入口。</summary>
    public static AppMapperCoreEngine Core { get; private set; } = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        // 全局异常兜底：任何未捕获异常写盘，便于自查（不弹窗，避免后台运行时打扰）。
        DispatcherUnhandledException += (_, args) => WriteCrash("Dispatcher", args.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            WriteCrash("AppDomain", args.ExceptionObject as Exception);
        System.Threading.Tasks.TaskScheduler.UnobservedTaskException += (_, args) =>
            WriteCrash("TaskScheduler", args.Exception);

        base.OnStartup(e);
        singleInstance = new System.Threading.Mutex(true, @"Local\AppMapper.Controller", out ownsSingleInstance);
        if (!ownsSingleInstance)
        {
            Shutdown();
            return;
        }

        var baseDir = AppContext.BaseDirectory;
        var log = new LogService(baseDir);
        var settings = new SettingsService(baseDir);

        try
        {
            Core = new AppMapperCoreEngine(log, settings);
            Core.StartAsync();
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show($"无法启动 AppMapper：{ex.Message}\n请检查软件目录中的 config/pairing.dat 及目录写入权限。不会自动清除现有配对。",
                "启动失败", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown();
            return;
        }

        var mainWindow = new MainWindow();
        mainWindow.Show();
        if (e.Args.Contains("--background")) mainWindow.Hide();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try
        {
            Core?.Dispose();
        }
        catch
        {
            // 退出时忽略清理异常。
        }

        if (ownsSingleInstance) singleInstance?.ReleaseMutex();
        singleInstance?.Dispose();
        base.OnExit(e);
    }

    private static void WriteCrash(string source, Exception? ex)
    {
        try
        {
            var path = Path.Combine(AppContext.BaseDirectory, "logs", "crash.log");
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] [{source}] {ex?.ToString()}{Environment.NewLine}";
            File.AppendAllText(path, line);
        }
        catch
        {
            // 兜底本身不能再抛。
        }
    }
}


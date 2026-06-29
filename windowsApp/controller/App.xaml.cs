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

        var baseDir = AppContext.BaseDirectory;
        var log = new LogService(baseDir);
        var settings = new SettingsService(baseDir);

        Core = new AppMapperCoreEngine(log, settings);
        Core.StartAsync();

        var mainWindow = new MainWindow();
        mainWindow.Show();
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


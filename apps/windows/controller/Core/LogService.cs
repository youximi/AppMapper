using System.Collections.Concurrent;
using System.IO;
using System.Text;
using AppMapper.Controller.Abstractions;
using Timer = System.Threading.Timer;

namespace AppMapper.Controller.Core;

/// <summary>
/// 核心层日志服务。负责：落盘到 <c>logs/appmapper-YYYYMMDD.log</c>（按天切 +
/// 单文件大小上限滚动）、维护内存最近 N 条环、抛 <see cref="LogEmitted"/> 事件供 UI 流式显示。
/// 日志由核心产生，UI 只读取（需求 8/10）。
/// </summary>
public sealed class LogService : IDisposable
{
    private const int MaxMemoryEntries = 1000;
    private const long MaxFileBytes = 5 * 1024 * 1024; // 5MB
    private const int MaxRetainedFiles = 10;

    private readonly string logsDirectory;
    private readonly ConcurrentQueue<LogEntry> memory = new();
    private readonly SemaphoreSlim fileLock = new(1, 1);
    private readonly StringBuilder buffer = new();
    private readonly Timer flushTimer;
    private bool disposed;

    public event Action<LogEntry>? LogEmitted;

    public LogService(string? baseDirectory = null)
    {
        logsDirectory = Path.Combine(baseDirectory ?? AppContext.BaseDirectory, "logs");
        Directory.CreateDirectory(logsDirectory);
        flushTimer = new Timer(_ => FlushAsync().Wait(), null, TimeSpan.FromSeconds(2), TimeSpan.FromSeconds(2));
    }

    public void Info(string message) => Log(LogLevel.Info, message);
    public void Warn(string message) => Log(LogLevel.Warn, message);
    public void Error(string message) => Log(LogLevel.Error, message);

    public void Log(LogLevel level, string message)
    {
        var entry = new LogEntry
        {
            Timestamp = DateTimeOffset.Now,
            Level = level,
            Message = message,
        };

        memory.Enqueue(entry);
        while (memory.Count > MaxMemoryEntries && memory.TryDequeue(out _)) { }

        var line = $"{entry.Timestamp:yyyy-MM-dd HH:mm:ss} [{level.ToString().ToUpperInvariant()}] {message}{Environment.NewLine}";
        buffer.AppendLine(line);

        LogEmitted?.Invoke(entry);
    }

    /// <summary>获取内存环的快照副本（最新在前）。</summary>
    public IReadOnlyList<LogEntry> GetRecent()
    {
        var snap = memory.ToArray();
        Array.Reverse(snap);
        return snap;
    }

    public async Task FlushAsync()
    {
        string[] lines;
        await fileLock.WaitAsync();
        try
        {
            if (buffer.Length == 0) return;
            lines = buffer.ToString().Split(Environment.NewLine, StringSplitOptions.RemoveEmptyEntries);
            buffer.Clear();
        }
        finally
        {
            fileLock.Release();
        }

        if (lines.Length == 0) return;
        await fileLock.WaitAsync();
        try
        {
            var path = Path.Combine(logsDirectory, $"appmapper-{DateTime.Now:yyyyMMdd}.log");
            await File.AppendAllLinesAsync(path, lines, Encoding.UTF8);
            RotateIfNeeded(path);
            PurgeOldFiles();
        }
        catch
        {
            // 日志落盘失败不应影响业务。
        }
        finally
        {
            fileLock.Release();
        }
    }

    private void RotateIfNeeded(string path)
    {
        try
        {
            var info = new FileInfo(path);
            if (info.Exists && info.Length > MaxFileBytes)
            {
                var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss");
                var renamed = Path.Combine(logsDirectory, $"appmapper-{stamp}.log");
                File.Move(path, renamed);
            }
        }
        catch { }
    }

    private void PurgeOldFiles()
    {
        try
        {
            var files = Directory.GetFiles(logsDirectory, "appmapper-*.log")
                .Select(f => new FileInfo(f))
                .OrderBy(f => f.LastWriteTime)
                .ToArray();
            for (var i = 0; i < files.Length - MaxRetainedFiles; i++)
            {
                files[i].Delete();
            }
        }
        catch { }
    }

    public void Dispose()
    {
        if (disposed) return;
        disposed = true;
        flushTimer.Dispose();
        FlushAsync().Wait();
        fileLock.Dispose();
    }
}

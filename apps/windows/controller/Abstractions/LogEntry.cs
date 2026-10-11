namespace AppMapper.Controller.Abstractions;

/// <summary>
/// 日志级别。
/// </summary>
public enum LogLevel
{
    Info,
    Warn,
    Error,
}

/// <summary>
/// 单条日志。由核心层 <c>LogService</c> 产生，UI 日志页只读取。
/// </summary>
public sealed class LogEntry
{
    public DateTimeOffset Timestamp { get; init; }
    public LogLevel Level { get; init; }
    public string Message { get; init; } = string.Empty;

    public string DisplayLine => $"[{Timestamp:HH:mm:ss}] {Message}";

    public string LevelText => Level switch
    {
        LogLevel.Info => "信息",
        LogLevel.Warn => "警告",
        LogLevel.Error => "错误",
        _ => Level.ToString(),
    };
}

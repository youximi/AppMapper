using System.Collections.ObjectModel;
using AppMapper.Controller.Abstractions;

namespace AppMapper.Controller.ViewModels;

/// <summary>日志页 VM：只读显示核心日志流。首次加载拉快照，之后订阅 LogEmitted 实时追加（最新在上）。</summary>
public sealed class LogsViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    public ObservableCollection<LogEntry> Logs { get; } = new();

    public LogsViewModel(ICoreFacade core)
    {
        this.core = core;

        foreach (var entry in core.GetStateSnapshot().RecentLogs)
            Logs.Add(entry);

        core.LogEmitted += OnLogEmitted;
    }

    private void OnLogEmitted(LogEntry entry)
    {
        Dispatch(() =>
        {
            Logs.Insert(0, entry);
            while (Logs.Count > 1000) Logs.RemoveAt(Logs.Count - 1);
        });
    }
}

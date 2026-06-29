using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows.Input;
using System.Windows.Threading;
using Application = System.Windows.Application;

namespace AppMapper.Controller.ViewModels;

/// <summary>轻量 ICommand 实现（UI 层专用）。</summary>
public sealed class RelayCommand(Action execute) : ICommand
{
    public event EventHandler? CanExecuteChanged;
    public bool CanExecute(object? parameter) => true;
    public void Execute(object? parameter) => execute();
    public void RaiseCanExecuteChanged() => CanExecuteChanged?.Invoke(this, EventArgs.Empty);
}

/// <summary>
/// 所有 ViewModel 的基类。提供 INPC 与跨线程封送助手。
/// 核心事件在后台线程触发，VM 用 <see cref="Dispatch"/> 切回 UI 线程更新绑定。
/// </summary>
public abstract class ViewModelBase : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected static Dispatcher Dispatcher => Application.Current.Dispatcher;

    /// <summary>把动作切到 UI 线程异步执行；UI 卡死时排队，不阻塞核心。</summary>
    protected static void Dispatch(Action action)
    {
        var dispatcher = Dispatcher;
        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(action);
    }

    protected bool SetField<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    protected void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

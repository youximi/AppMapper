using System.Windows.Input;
using AppMapper.Controller.Abstractions;

namespace AppMapper.Controller.ViewModels;

/// <summary>设置页 VM：端口 / 关闭映射窗口自动重开 / 关闭到托盘 / 显示侧栏文字。
/// 直接读写 <see cref="ICoreFacade.Settings"/>（核心自动落盘），并订阅其变更以同步外部改动（如主窗口折叠侧栏）。</summary>
public sealed class SettingsViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    private string port = "8765";
    private bool relaunchMapperWhenClosed = true;
    private bool closeToTray = true;
    private bool paneOpen = true;

    public SettingsViewModel(ICoreFacade core)
    {
        this.core = core;
        OpenAppsDirectoryCommand = new RelayCommand(core.OpenAppsDirectory);
        RestartServerCommand = new RelayCommand(RestartServer);

        var s = core.Settings;
        port = s.Port.ToString();
        relaunchMapperWhenClosed = s.RelaunchMapperWhenClosed;
        closeToTray = s.CloseToTray;
        paneOpen = s.PaneOpen;

        s.PropertyChanged += OnSettingChanged;
    }

    public ICommand OpenAppsDirectoryCommand { get; }
    public ICommand RestartServerCommand { get; }

    private void RestartServer()
    {
        if (!int.TryParse(port, out var p)) p = core.Settings.Port;
        core.RestartServer(p);
    }

    public string Port
    {
        get => port;
        set
        {
            if (SetField(ref port, value) && int.TryParse(value, out var p))
                core.Settings.Port = p;
        }
    }

    public bool RelaunchMapperWhenClosed
    {
        get => relaunchMapperWhenClosed;
        set
        {
            if (SetField(ref relaunchMapperWhenClosed, value))
                core.Settings.RelaunchMapperWhenClosed = value;
        }
    }

    public bool CloseToTray
    {
        get => closeToTray;
        set
        {
            if (SetField(ref closeToTray, value))
                core.Settings.CloseToTray = value;
        }
    }

    public bool PaneOpen
    {
        get => paneOpen;
        set
        {
            if (SetField(ref paneOpen, value))
                core.Settings.PaneOpen = value;
        }
    }

    private void OnSettingChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        Dispatch(() =>
        {
            var s = core.Settings;
            switch (e.PropertyName)
            {
                case nameof(Settings.Port):
                    port = s.Port.ToString();
                    OnPropertyChanged(nameof(Port));
                    break;
                case nameof(Settings.RelaunchMapperWhenClosed):
                    if (relaunchMapperWhenClosed != s.RelaunchMapperWhenClosed)
                    {
                        relaunchMapperWhenClosed = s.RelaunchMapperWhenClosed;
                        OnPropertyChanged(nameof(RelaunchMapperWhenClosed));
                    }
                    break;
                case nameof(Settings.CloseToTray):
                    if (closeToTray != s.CloseToTray)
                    {
                        closeToTray = s.CloseToTray;
                        OnPropertyChanged(nameof(CloseToTray));
                    }
                    break;
                case nameof(Settings.PaneOpen):
                    if (paneOpen != s.PaneOpen)
                    {
                        paneOpen = s.PaneOpen;
                        OnPropertyChanged(nameof(PaneOpen));
                    }
                    break;
            }
        });
    }
}

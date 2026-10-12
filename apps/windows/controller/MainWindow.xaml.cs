using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Core;
using Wpf.Ui.Controls;
using Application = System.Windows.Application;

namespace AppMapper.Controller;

/// <summary>
/// 主窗口。使用 <see cref="FluentWindow"/> + <see cref="TitleBar"/> 提供与 WPF-UI 一致的窗口外观，
/// 承载 <see cref="NavigationView"/>（左侧 4 页导航）与托盘 <see cref="Wpf.Ui.Tray.Controls.NotifyIcon"/>。
/// 点窗口 X 时按设置缩到托盘而非退出（需求 11）；侧栏折叠状态持久化；启动默认打开配对页。
/// </summary>
public partial class MainWindow : FluentWindow
{
    private bool forceExit;

    public MainWindow()
    {
        InitializeComponent();

        var settings = App.Core.Settings;
        RootNavigation.IsPaneOpen = settings.PaneOpen;
        settings.PropertyChanged += OnSettingChanged;
    }

    /// <summary>启动后默认打开配对页（避免空白）。</summary>
    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (RootNavigation.SelectedItem is null)
            RootNavigation.Navigate(typeof(Views.PairingPage), null);
    }

    private void OnSettingChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName != nameof(Settings.PaneOpen)) return;
        var open = App.Core.Settings.PaneOpen;
        if (RootNavigation.IsPaneOpen != open)
            Dispatcher.Invoke(() => RootNavigation.IsPaneOpen = open);
    }

    private void OnPaneOpened(object sender, RoutedEventArgs e) => App.Core.Settings.PaneOpen = true;
    private void OnPaneClosed(object sender, RoutedEventArgs e) => App.Core.Settings.PaneOpen = false;

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        if (forceExit) return;
        if (App.Core.Settings.CloseToTray)
        {
            e.Cancel = true;
            Hide();
        }
    }

    private void OnTrayDoubleClick(object sender, RoutedEventArgs e) => ShowMainWindow();
    private void OnShowMainWindow(object sender, RoutedEventArgs e) => ShowMainWindow();

    private void OnExit(object sender, RoutedEventArgs e)
    {
        forceExit = true;
        Close();
        Application.Current.Shutdown();
    }

    private void ShowMainWindow()
    {
        Show();
        WindowState = WindowState.Normal;
        Activate();
    }
}

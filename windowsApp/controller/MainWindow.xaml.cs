using System.ComponentModel;
using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Core;
using Wpf.Ui.Controls;
using Application = System.Windows.Application;
using Brushes = System.Windows.Media.Brushes;
using Color = System.Windows.Media.Color;
using FlowDirection = System.Windows.FlowDirection;
using FontFamily = System.Windows.Media.FontFamily;
using Point = System.Windows.Point;

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

        // 托盘图标：无外部资源，运行时用 RenderTargetBitmap 渲染一个矢量图标。失败不阻断启动。
        try { TrayIcon.Icon = CreateTrayIcon(); }
        catch { /* 托盘不可用时主窗口仍可用 */ }

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

    /// <summary>运行时生成托盘图标（圆角蓝底 + 白色 A 字）。</summary>
    private static ImageSource CreateTrayIcon()
    {
        const int size = 32;
        var visual = new DrawingVisual();
        using (var dc = visual.RenderOpen())
        {
            var background = new SolidColorBrush(Color.FromRgb(0x25, 0x63, 0xEB));
            background.Freeze();
            dc.DrawGeometry(background, null, new RectangleGeometry(new Rect(2, 2, size - 4, size - 4), 8, 8));

            var typeface = new Typeface(new FontFamily("Segoe UI"), FontStyles.Normal, FontWeights.Bold, FontStretches.Normal);
            var glyph = new FormattedText("A", CultureInfo.InvariantCulture, FlowDirection.LeftToRight, typeface, 18, Brushes.White, 1.0);
            dc.DrawText(glyph, new Point((size - glyph.Width) / 2, (size - glyph.Height) / 2));
        }

        var bitmap = new RenderTargetBitmap(size, size, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(visual);
        bitmap.Freeze();
        return bitmap;
    }
}

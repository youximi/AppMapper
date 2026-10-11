using System.Diagnostics;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller.Views;

public partial class SettingsPage : Page
{
    public SettingsPage()
    {
        InitializeComponent();
        DataContext = new SettingsViewModel(App.Core);
        var version = typeof(App).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
        VersionText.Text = $"版本 {version ?? "0.1.0"}";
    }

    private void OnOpenProject(object sender, RoutedEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo("https://github.com/youximi/AppMapper") { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show(Window.GetWindow(this), $"无法打开项目主页：{ex.Message}",
                "AppMapper", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnOpenLicenses(object sender, RoutedEventArgs e) =>
        new OpenSourceLicensesWindow { Owner = Window.GetWindow(this) }.ShowDialog();
}

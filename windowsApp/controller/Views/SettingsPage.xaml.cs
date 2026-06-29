using System.Windows.Controls;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller.Views;

public partial class SettingsPage : Page
{
    public SettingsPage()
    {
        InitializeComponent();
        DataContext = new SettingsViewModel(App.Core);
    }
}

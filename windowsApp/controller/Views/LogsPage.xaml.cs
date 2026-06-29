using System.Windows.Controls;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller.Views;

public partial class LogsPage : Page
{
    public LogsPage()
    {
        InitializeComponent();
        DataContext = new LogsViewModel(App.Core);
    }
}

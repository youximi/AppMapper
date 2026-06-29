using System.Windows.Controls;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller.Views;

public partial class DevicesPage : Page
{
    public DevicesPage()
    {
        InitializeComponent();
        DataContext = new DevicesViewModel(App.Core);
    }
}

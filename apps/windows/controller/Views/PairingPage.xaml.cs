using System.Windows.Controls;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller.Views;

public partial class PairingPage : Page
{
    public PairingPage()
    {
        InitializeComponent();
        DataContext = new PairingViewModel(App.Core);
    }
}

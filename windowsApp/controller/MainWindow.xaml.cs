using System.Windows;
using AppMapper.Controller.ViewModels;

namespace AppMapper.Controller;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        DataContext = new MainViewModel();
    }
}

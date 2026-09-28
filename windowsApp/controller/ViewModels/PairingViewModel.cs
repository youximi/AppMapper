using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Models;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.ViewModels;

/// <summary>配对页 VM：显示本机地址 / 验证码 / 二维码 / 已连接设备。端口设置移至设置页。</summary>
public sealed class PairingViewModel : ViewModelBase
{
    private string serverAddress = "";
    private string pairingCode = "";
    private string networkWarning = "";
    private BitmapImage? qrImage;
    private DeviceState? selectedDevice;
    private readonly ICoreFacade core;

    public PairingViewModel(ICoreFacade core)
    {
        this.core = core;
        RemoveSelectedCommand = new RelayCommand(RemoveSelected);
        ApplySnapshot(core.GetStateSnapshot());
        core.PairingChanged += OnPairingChanged;
        core.DevicesChanged += OnDevicesChanged;
    }

    public ObservableCollection<DeviceState> Devices { get; } = new();
    public ICommand RemoveSelectedCommand { get; }
    public DeviceState? SelectedDevice { get => selectedDevice; set => SetField(ref selectedDevice, value); }
    public string ServerAddress { get => serverAddress; private set => SetField(ref serverAddress, value); }
    public string PairingCode { get => pairingCode; private set => SetField(ref pairingCode, value); }
    public string NetworkWarning { get => networkWarning; private set => SetField(ref networkWarning, value); }
    public bool HasNetworkWarning => !string.IsNullOrWhiteSpace(networkWarning);
    public BitmapImage? QrImage { get => qrImage; private set => SetField(ref qrImage, value); }

    private void ApplySnapshot(CoreStateSnapshot snap)
    {
        ServerAddress = snap.ServerAddress;
        PairingCode = snap.PairingCode;
        NetworkWarning = snap.NetworkWarning;
        QrImage = GenerateQr(snap.PairingUri);
        ReplaceDevices(snap.Devices);
    }

    private void OnPairingChanged(PairingInfo info)
    {
        Dispatch(() =>
        {
            ServerAddress = info.ServerAddress;
            PairingCode = info.Code;
            NetworkWarning = info.NetworkWarning;
            QrImage = GenerateQr(info.PairingUri);
        });
    }

    private void OnDevicesChanged(IReadOnlyList<DeviceState> snapshot) =>
        Dispatch(() => ReplaceDevices(snapshot));

    protected override void OnPropertyChanged(string? propertyName = null)
    {
        base.OnPropertyChanged(propertyName);
        if (propertyName == nameof(NetworkWarning))
            base.OnPropertyChanged(nameof(HasNetworkWarning));
    }

    private void RemoveSelected()
    {
        var device = SelectedDevice;
        if (device?.IsPaired != true) return;
        if (System.Windows.MessageBox.Show($"移除 {device.DeviceName}？手机需要重新扫码配对。", "移除已配对手机",
                MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes)
            core.RemovePairedDevice(device.DeviceId);
    }

    private static BitmapImage? GenerateQr(string uri) =>
        string.IsNullOrEmpty(uri) ? null : QrCodeService.Generate(uri);

    private void ReplaceDevices(IReadOnlyList<DeviceState> snapshot)
    {
        Devices.Clear();
        foreach (var d in snapshot)
            Devices.Add(d);
    }
}

using System.Collections.ObjectModel;
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
    private string pairingUri = "";
    private string networkWarning = "";
    private BitmapImage? qrImage;

    public PairingViewModel(ICoreFacade core)
    {
        ApplySnapshot(core.GetStateSnapshot());
        core.PairingChanged += OnPairingChanged;
        core.DevicesChanged += OnDevicesChanged;
    }

    public ObservableCollection<DeviceState> Devices { get; } = new();
    public string ServerAddress { get => serverAddress; private set => SetField(ref serverAddress, value); }
    public string PairingCode { get => pairingCode; private set => SetField(ref pairingCode, value); }
    public string PairingUri { get => pairingUri; private set => SetField(ref pairingUri, value); }
    public string NetworkWarning { get => networkWarning; private set => SetField(ref networkWarning, value); }
    public bool HasNetworkWarning => !string.IsNullOrWhiteSpace(networkWarning);
    public BitmapImage? QrImage { get => qrImage; private set => SetField(ref qrImage, value); }

    private void ApplySnapshot(CoreStateSnapshot snap)
    {
        ServerAddress = snap.ServerAddress;
        PairingCode = snap.PairingCode;
        PairingUri = snap.PairingUri;
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
            PairingUri = info.PairingUri;
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

    private static BitmapImage? GenerateQr(string uri) =>
        string.IsNullOrEmpty(uri) ? null : QrCodeService.Generate(uri);

    private void ReplaceDevices(IReadOnlyList<DeviceState> snapshot)
    {
        Devices.Clear();
        foreach (var d in snapshot)
            Devices.Add(d);
    }
}

using System.Windows.Media.Imaging;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.ViewModels;

/// <summary>配对页 VM：显示本机地址、网络提示、验证码和二维码。</summary>
public sealed class PairingViewModel : ViewModelBase
{
    private string serverAddress = "";
    private string pairingCode = "";
    private string networkWarning = "";
    private BitmapImage? qrImage;

    public PairingViewModel(ICoreFacade core)
    {
        ApplySnapshot(core.GetStateSnapshot());
        core.PairingChanged += OnPairingChanged;
    }

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

    protected override void OnPropertyChanged(string? propertyName = null)
    {
        base.OnPropertyChanged(propertyName);
        if (propertyName == nameof(NetworkWarning))
            base.OnPropertyChanged(nameof(HasNetworkWarning));
    }

    private static BitmapImage? GenerateQr(string uri) =>
        string.IsNullOrEmpty(uri) ? null : QrCodeService.Generate(uri);
}

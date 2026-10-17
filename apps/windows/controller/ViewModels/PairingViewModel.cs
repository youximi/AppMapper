using System.Windows.Media.Imaging;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.ViewModels;

/// <summary>配对页 VM：显示本机地址、网络提示、验证码和二维码。</summary>
public sealed class PairingViewModel : ViewModelBase
{
    public PairingViewModel(ICoreFacade core)
    {
        ApplySnapshot(core.GetStateSnapshot());
        core.PairingChanged += OnPairingChanged;
    }

    public string ServerAddress { get; private set => SetField(ref field, value); } = "";
    public string PairingCode { get; private set => SetField(ref field, value); } = "";
    public string NetworkWarning { get; private set => SetField(ref field, value); } = "";
    public bool HasNetworkWarning => !string.IsNullOrWhiteSpace(NetworkWarning);
    public BitmapImage? QrImage { get; private set => SetField(ref field, value); }

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

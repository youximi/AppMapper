using System.Windows.Media.Imaging;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Services;

namespace AppMapper.Controller.ViewModels;

/// <summary>配对页 VM：显示本机地址 / 验证码 / 二维码 / 配对链接。端口设置移至设置页。</summary>
public sealed class PairingViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    private string serverAddress = "";
    private string pairingCode = "";
    private string pairingUri = "";
    private BitmapImage? qrImage;

    public PairingViewModel(ICoreFacade core)
    {
        this.core = core;
        ApplySnapshot(core.GetStateSnapshot());
        core.PairingChanged += OnPairingChanged;
    }

    public string ServerAddress { get => serverAddress; private set => SetField(ref serverAddress, value); }
    public string PairingCode { get => pairingCode; private set => SetField(ref pairingCode, value); }
    public string PairingUri { get => pairingUri; private set => SetField(ref pairingUri, value); }
    public BitmapImage? QrImage { get => qrImage; private set => SetField(ref qrImage, value); }

    private void ApplySnapshot(CoreStateSnapshot snap)
    {
        ServerAddress = snap.ServerAddress;
        PairingCode = snap.PairingCode;
        PairingUri = snap.PairingUri;
        QrImage = GenerateQr(snap.PairingUri);
    }

    private void OnPairingChanged(PairingInfo info)
    {
        Dispatch(() =>
        {
            ServerAddress = info.ServerAddress;
            PairingCode = info.Code;
            PairingUri = info.PairingUri;
            QrImage = GenerateQr(info.PairingUri);
        });
    }

    private static BitmapImage? GenerateQr(string uri) =>
        string.IsNullOrEmpty(uri) ? null : QrCodeService.Generate(uri);
}

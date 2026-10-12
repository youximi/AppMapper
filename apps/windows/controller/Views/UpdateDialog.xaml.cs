using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using AppMapper.Controller.Models;
using AppMapper.Controller.Services;
using Wpf.Ui.Controls;

namespace AppMapper.Controller.Views;

public partial class UpdateDialog : ContentDialog
{
    private readonly AppUpdateInfo release;

    public UpdateDialog(ContentDialogHost host, AppUpdateInfo release, bool newer)
    {
        InitializeComponent();
        this.release = release;
        DialogHostEx = host;
        Title = newer ? "发现新版本" : "更新说明";
        CloseButtonText = newer ? "稍后" : "关闭";
        VersionSummary.Text = $"当前版本 {GitHubUpdateService.InstalledVersionName}\n最新版本 {release.VersionName}";
        NotesText.Text = release.Notes;
    }

    protected override void OnButtonClick(ContentDialogButton button)
    {
        if (button == ContentDialogButton.Primary)
        {
            try
            {
                Process.Start(new ProcessStartInfo(release.ReleaseUrl) { UseShellExecute = true });
            }
            catch (Exception ex) when (ex is Win32Exception or InvalidOperationException)
            {
                DownloadError.Text = "无法打开下载页面，请检查默认浏览器后重试";
                DownloadError.Visibility = Visibility.Visible;
                return;
            }
        }
        base.OnButtonClick(button);
    }
}

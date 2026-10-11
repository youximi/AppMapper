using System.IO;
using System.Windows;
using Wpf.Ui.Controls;

namespace AppMapper.Controller.Views;

public partial class OpenSourceLicensesWindow : FluentWindow
{
    private readonly string projectLicense;
    private readonly string notices;

    public OpenSourceLicensesWindow()
    {
        InitializeComponent();
        projectLicense = ReadResource("LICENSE");
        notices = ReadResource("THIRD_PARTY_LICENSES.md");

        // Reuse the Windows table in the bundled notices as the single library list.
        var table = notices.Split("## Windows 运行时组件\n", 2)[1].Split("\n### ", 2)[0];
        var libraries = new List<LicensedLibrary>();
        foreach (var row in table.Split('\n').Where(line => line.StartsWith("| ")).Skip(2))
        {
            var columns = row.Split('|').Select(column => column.Trim()).ToArray();
            var names = columns[1].Split('、');
            var licenseHeading = names[0] switch
            {
                "WPF-UI" => "### WPF UI MIT 许可（上述三个包）",
                "QRCoder" => "### QRCoder MIT 许可",
                "System.Drawing.Common" => "### .NET Runtime MIT 许可（上述两个包）",
                _ => throw new InvalidOperationException($"未找到 {names[0]} 的许可原文。"),
            };
            var licenseText = ReadCodeBlock(licenseHeading);
            foreach (var name in names)
            {
                var noticeHeading = name.StartsWith("WPF-UI", StringComparison.Ordinal)
                    ? $"### WPF UI {columns[2]} — ThirdPartyNotices.txt"
                    : name is "System.Drawing.Common" or "Microsoft.Win32.SystemEvents"
                        ? $"### {name} {columns[2]} — THIRD-PARTY-NOTICES.TXT"
                        : null;
                var text = licenseText + (noticeHeading is null ? "" : $"\n\n{ReadCodeBlock(noticeHeading)}");
                libraries.Add(new LicensedLibrary(name, columns[2], columns[3], text));
            }
        }

        LibraryList.ItemsSource = libraries.OrderBy(library => library.Name, StringComparer.OrdinalIgnoreCase).ToList();
        LibraryCountText.Text = $"共包含 {libraries.Count} 个库";
    }

    private static string ReadResource(string name)
    {
        using var stream = typeof(OpenSourceLicensesWindow).Assembly.GetManifestResourceStream($"AppMapper.Licenses.{name}")
            ?? throw new InvalidOperationException($"缺少内置许可文件：{name}");
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd().Replace("\r\n", "\n");
    }

    private string ReadCodeBlock(string heading) =>
        notices.Split(heading + "\n", 2)[1].Split("```text\n", 2)[1].Split("\n```", 2)[0];

    private void OnOpenProjectLicense(object sender, RoutedEventArgs e) => ShowDocument("项目许可", projectLicense);

    private void OnOpenNotices(object sender, RoutedEventArgs e) => ShowDocument("完整第三方声明", notices);

    private void OnOpenLibrary(object sender, RoutedEventArgs e)
    {
        var library = (LicensedLibrary)((FrameworkElement)sender).Tag;
        ShowDocument(library.Name, $"{library.Name}\n\n版本 {library.Version}\n\n{library.License}\n\n{library.Text}");
    }

    private void ShowDocument(string title, string text)
    {
        DocumentTitle.Text = title;
        DocumentText.Text = text;
        DocumentText.ScrollToHome();
        LibraryPanel.Visibility = Visibility.Collapsed;
        DocumentPanel.Visibility = Visibility.Visible;
    }

    private void OnBack(object sender, RoutedEventArgs e)
    {
        DocumentPanel.Visibility = Visibility.Collapsed;
        LibraryPanel.Visibility = Visibility.Visible;
    }

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    private sealed record LicensedLibrary(string Name, string Version, string License, string Text);
}

using AppMapper.Controller.Models;

namespace AppMapper.Controller.Abstractions;

/// <summary>
/// 配对信息（核心实时事件载荷）。二维码图片由 UI 层依据 <see cref="PairingUri"/>
/// 自行生成（<c>BitmapImage</c> 是 WPF 类型，不进入核心层）。
/// </summary>
public sealed record PairingInfo(string Code, string ServerAddress, string PairingUri, int Port, string NetworkWarning);

/// <summary>
/// 核心层在某一时刻的状态快照。UI 在启动或从卡死/隐藏恢复时调用
/// <see cref="ICoreFacade.GetStateSnapshot"/> 重取全部状态，保证 UI 与核心一致。
/// </summary>
public sealed class CoreStateSnapshot
{
    public string PairingCode { get; init; } = string.Empty;
    public string ServerAddress { get; init; } = string.Empty;
    public string PairingUri { get; init; } = string.Empty;
    public string NetworkWarning { get; init; } = string.Empty;
    public int Port { get; init; }
    public string CurrentStatus { get; init; } = string.Empty;
    public IReadOnlyList<DeviceState> Devices { get; init; } = Array.Empty<DeviceState>();
    public IReadOnlyList<LogEntry> RecentLogs { get; init; } = Array.Empty<LogEntry>();
}

using AppMapper.Controller.Models;

namespace AppMapper.Controller.Abstractions;

/// <summary>
/// 核心层对外门面——UI 层的唯一入口。UI 只依赖此接口，不直接引用
/// <c>TcpJsonServer</c> 等业务服务。核心跑在后台线程，所有事件在后台线程触发，
/// UI 侧自行 <c>Dispatcher.InvokeAsync</c> 切回 UI 线程。
/// </summary>
public interface ICoreFacade
{
    /// <summary>当前配置（INPC，可双向绑定）。变更由核心自动落盘。</summary>
    Settings Settings { get; }

    /// <summary>当前状态文本（如「当前映射：XXX」）。</summary>
    string CurrentStatus { get; }

    /// <summary>启动核心（TCP 服务、配对码定时器等）。</summary>
    Task StartAsync();

    /// <summary>停止核心并释放资源。</summary>
    Task StopAsync();

    /// <summary>按指定端口重启 TCP 服务。</summary>
    void RestartServer(int port);

    /// <summary>打开 apps 目录。</summary>
    void OpenAppsDirectory();

    /// <summary>获取核心当前完整状态快照（供 UI 启动/恢复时重取）。</summary>
    CoreStateSnapshot GetStateSnapshot();

    /// <summary>实时日志流（后台线程触发）。</summary>
    event Action<LogEntry>? LogEmitted;

    /// <summary>配对信息变化（验证码刷新 / 端口重启 / 启动）。</summary>
    event Action<PairingInfo>? PairingChanged;

    /// <summary>设备列表变化（全量快照，后台线程触发）。</summary>
    event Action<IReadOnlyList<DeviceState>>? DevicesChanged;

    /// <summary>状态文本变化。</summary>
    event Action<string>? StatusChanged;
}

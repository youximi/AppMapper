using System.Collections.ObjectModel;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.ViewModels;

/// <summary>设备页 VM：展示已连接设备列表。核心抛 DevicesChanged（全量快照），VM 整表替换。</summary>
public sealed class DevicesViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    public ObservableCollection<DeviceState> Devices { get; } = new();

    public DevicesViewModel(ICoreFacade core)
    {
        this.core = core;

        foreach (var d in core.GetStateSnapshot().Devices)
            Devices.Add(d);

        core.DevicesChanged += OnDevicesChanged;
    }

    private void OnDevicesChanged(IReadOnlyList<DeviceState> snapshot)
    {
        Dispatch(() =>
        {
            Devices.Clear();
            foreach (var d in snapshot)
                Devices.Add(d);
        });
    }
}

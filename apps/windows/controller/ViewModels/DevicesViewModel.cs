using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Input;
using AppMapper.Controller.Abstractions;
using AppMapper.Controller.Models;

namespace AppMapper.Controller.ViewModels;

/// <summary>设备页 VM：显示设备状态并移除已配对手机。</summary>
public sealed class DevicesViewModel : ViewModelBase
{
    private readonly ICoreFacade core;

    public DevicesViewModel(ICoreFacade core)
    {
        this.core = core;
        RemoveDeviceCommand = new Wpf.Ui.Input.RelayCommand<DeviceState?>(
            RemoveDevice, device => device?.IsPaired == true);
        ReplaceDevices(core.GetStateSnapshot().Devices);
        core.DevicesChanged += OnDevicesChanged;
    }

    public ObservableCollection<DeviceState> Devices { get; } = new();
    public ICommand RemoveDeviceCommand { get; }

    private void OnDevicesChanged(IReadOnlyList<DeviceState> snapshot) =>
        Dispatch(() => ReplaceDevices(snapshot));

    private void ReplaceDevices(IReadOnlyList<DeviceState> snapshot)
    {
        Devices.Clear();
        foreach (var device in snapshot)
            Devices.Add(device);
    }

    private void RemoveDevice(DeviceState? device)
    {
        if (device?.IsPaired != true) return;
        if (System.Windows.MessageBox.Show($"移除 {device.DeviceName}？手机需要重新扫码配对。", "移除已配对手机",
                MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes)
            core.RemovePairedDevice(device.DeviceId);
    }
}

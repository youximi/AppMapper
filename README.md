# AppMapper

把 Android 手机上**当前正在使用的前台 App**，实时映射成一个 Windows 桌面普通窗口，让现有的电脑端计时、番茄钟或软件使用统计工具把它当成真实的桌面软件来记录使用时长。

> **状态**：早期开发中，尚未完善。

## 项目介绍

大多数桌面计时软件只统计"当前前台窗口"，并不会开放接口给第三方。所以 AppMapper 没有去 Hook 它们，而是直接生成一个标题、图标、进程都贴近真实 App 的小窗口，让计时软件用自己原本的逻辑去识别和记录。

每个手机 App 对应一个独立的 Windows 映射 exe（从模板复制 + 写入图标），比单一动态窗口更容易被各种识别逻辑接受。

## 目录结构

```text
AppMapper/
├── androidApp/              Android 客户端（Kotlin + Compose）
├── windowsApp/
│   ├── controller/          Windows 总控端（C# WPF）
│   ├── mapper/              映射小程序模板（C++ Win32）
│   └── common/              预留的 Windows 公共工具
├── shared/                  协议示例与共享说明
```

## 技术栈

- **Android 客户端**：Kotlin、Jetpack Compose、Material3、前台服务、`UsageStatsManager`。最低 Android 13。
- **Windows 总控端**：C# WPF（.NET 8）、TCP 服务器、二维码配对、映射进程管理。
- **Windows 映射端**：C++ Win32，128×128 分层置顶窗口，不联网。
- **传输协议**：局域网 TCP + JSON Lines（每条消息一行 JSON）。

## 配对方式

总控端启动后会显示：

- 本机局域网 IP
- 端口（默认 `8765`）
- 6 位数字验证码（每 60 秒刷新一次，只用于新连接）
- 包含连接信息的二维码

二维码内容格式：

```text
appmapper://connect?host=192.168.1.10&port=8765&code=123456
```

Android 端可以直接扫码，也可以手动输入 IP、端口、验证码连接。

## 前置环境

- **Android 客户端**：JDK 17、Android SDK（API 33+）、Android Studio（或直接用项目自带的 Gradle Wrapper）。
- **Windows 总控端**：.NET 8 SDK。
- **Windows 映射端**：Visual Studio Build Tools，需勾选"使用 C++ 的桌面开发"和 Windows SDK。

## 构建

### Android 客户端

```powershell
gradle :androidApp:app:assembleDebug
```

### Windows 总控端

```powershell
dotnet build windowsApp\controller\AppMapper.Controller.csproj
```

### Windows 映射端模板

```powershell
msbuild windowsApp\mapper\AppMapper.Mapper.vcxproj /p:Configuration=Release /p:Platform=x64
```

映射端构建完成后，把 `windowsApp\mapper\bin\Release\mapper-template.exe` 复制到总控端可执行文件的同级目录。总控端运行时会从这里找exe模板。

## 快速开始

1. 在 Windows 上运行总控端，主窗口会显示本机 IP、端口、验证码和二维码。
2. 确认 `mapper-template.exe` 已放在总控端 exe 同级目录（见上节构建说明）。
3. 手机和电脑接入**同一局域网**。
4. 在手机上安装并打开 Android 客户端，按提示授予"使用情况访问"权限（`PACKAGE_USAGE_STATS`）。
5. 扫描总控端二维码，或手动输入 IP、端口、验证码完成配对。
6. 在手机上切换到任意前台 App，电脑任务栏会出现对应的映射窗口；回到桌面 / 锁屏 / 熄屏时窗口自动关闭。

## 权限说明

Android 客户端需要以下权限，全部用于核心功能：

| 权限                                                    | 用途               |
|-------------------------------------------------------|------------------|
| `INTERNET` / `ACCESS_NETWORK_STATE`                   | 局域网 TCP 连接       |
| `CAMERA`                                              | 扫描配对二维码          |
| `PACKAGE_USAGE_STATS`                                 | 读取当前前台 App（核心功能） |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 保持后台同步           |
| `POST_NOTIFICATIONS`                                  | 前台服务通知           |


## 隐私与安全

- 全程**仅局域网通信**，Android 端连接的目标地址完全由用户在界面上输入或扫码得到，代码里没有任何硬编码的服务器地址。
- Windows 总控端是 TCP 服务端，只监听局域网，不会主动外联任何网络。
- 映射小程序是纯本地窗口程序，不联网。

## License

本项目采用 [GPL-3.0-only](./LICENSE) 协议。



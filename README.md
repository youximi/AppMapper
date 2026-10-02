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

- **Android 客户端**：Kotlin、Jetpack Compose、Material3、前台服务、`UsageStatsManager`。最低 Android 8.0。
- **Windows 总控端**：C# WPF（.NET 8）、TLS 服务器、二维码配对、映射进程管理。
- **Windows 映射端**：C++ Win32，128×128 分层置顶窗口，不联网。
- **传输协议**：局域网 TLS + JSON Lines（每条消息一行 JSON），协议版本 2。

## 配对方式

总控端启动后会显示：

- 本机局域网 IP
- 端口（默认 `8765`）
- 6 位数字验证码（每 60 秒刷新一次，只用于临时连接）
- 用于记住设备的二维码

二维码内容格式：

```text
appmapper://connect?host=192.168.1.10&port=8765&serverId=<电脑ID>&fingerprint=<64位公钥指纹>&enrollToken=<32位一次性令牌>
```

Android 端勾选「记住设备」后扫描二维码绑定；二维码中的公钥指纹用于验证电脑身份，一次性令牌授权长期配对。手机使用 Android Keystore 中的设备密钥应答电脑的一次性挑战；验证码刷新、短暂断网、电脑重启后会自动重连。没有摄像头时，可取消「记住设备」，手动输入 IP、端口和 6 位验证码进行临时连接。临时连接不会保存配对，断线后需重新输入验证码，也不会清除已有的长期配对。手机需保持同步前台服务运行，电脑需要运行总控端；电脑端可在设置中开启「开机自启」（Windows 登录后启动到托盘）。手机重启后需打开 App 并启动同步，本次没有加入手机开机自启。

电脑「配对」页可以移除手机，手机「设置」页可以忘记电脑；之后要重新扫码。断线后手机只会按已保存的地址重试。电脑 IP 或端口变化时，请在手机上重新扫描电脑当前的配对二维码。

## 前置环境

- **Android 客户端**：JDK 17/21、Android SDK Platform 37.0、Build Tools 36.0.0、Android Studio Panda 3（2025.3.3 Patch 1）或更高版本（也可直接使用项目自带的 Gradle Wrapper）。最低运行系统为 Android 8.0。
- **Windows 总控端**：.NET 8 SDK。
- **Windows 映射端**：Visual Studio Build Tools，需勾选"使用 C++ 的桌面开发"和 Windows SDK。

## 构建

### Android 客户端

标准构建方式：

```powershell
.\gradlew.bat :androidApp:app:assembleDebug
```

Windows 下 `gradlew.bat` 会优先使用 JDK 17/21；如果系统默认 JDK 过新，会自动尝试使用 Android Studio 自带的 JBR。

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
5. 勾选「记住设备」并扫描总控端二维码完成绑定；无摄像头时取消勾选，手动输入 IP、端口、验证码进行临时连接。Android 17 及以上会在扫码或连接时申请局域网访问权限，请允许后继续。
6. 在手机上切换到任意前台 App，电脑任务栏会出现对应的映射窗口；回到桌面 / 锁屏 / 熄屏时窗口自动关闭。

## 权限说明

Android 客户端需要以下权限，全部用于核心功能：

| 权限                                                    | 用途               |
|-------------------------------------------------------|------------------|
| `INTERNET` / `ACCESS_NETWORK_STATE`                   | 局域网 TLS 连接       |
| `ACCESS_LOCAL_NETWORK`                               | Android 17 及以上的局域网访问（运行时授权） |
| `CAMERA`                                              | 扫描配对二维码          |
| `PACKAGE_USAGE_STATS`                                 | 读取当前前台 App（核心功能） |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 保持设备连接和后台同步 |
| `CHANGE_WIFI_MULTICAST_STATE` | 满足 Android 连接设备前台服务的权限要求 |
| `POST_NOTIFICATIONS`                                  | Android 13 及以上的通知运行时权限；拒绝不阻止前台服务启动 |


## 隐私与安全

- 全程**仅局域网通信**。扫码绑定与后续按已保存地址重连使用二维码里的电脑公钥指纹验证电脑身份。手动临时连接不验证电脑身份，局域网内的攻击者可能冒充目标电脑；仅在应急时使用。
- 电脑身份和已配对手机公钥保存在软件目录 `config/pairing.dat`，由当前 Windows 用户的 DPAPI 加密。手机配对记录在 App 私有 no-backup 目录 `config/paired-computer.json`；私钥由 Android Keystore 管理。不要将 Windows 数据文件移到另一 Windows 用户下期待可以解密。
- 六位码只能授权临时连接；二维码中的随机令牌才能授权长期绑定。两者都会在使用后或每 60 秒失效，不作为重连凭据存储或在日志中记录。
- Windows 总控端仅监听用于配对和同步的 TCP 端口。
- 映射小程序是纯本地窗口程序，不联网。

## License

本项目采用 [GPL-3.0-only](./LICENSE) 协议。Windows 与 Android 发行版所用组件的许可和版权声明见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。Android APK 内置这两份文件，可在「设置 → 开源许可」中离线查看；Windows 压缩包内也包含两份文件。GitHub Release 提供对应构建提交的源码下载入口。



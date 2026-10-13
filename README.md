# AppMapper

<img src="assets/branding/appmapper-icon.svg" width="96" height="96" alt="AppMapper 图标" />

AppMapper 把 Android 手机的前台 App 实时映射成一个 Windows 普通窗口，让电脑端番茄钟或软件使用统计工具也能按桌面软件统计它的使用时长。

> 项目仍在早期开发阶段，尚未完善。

## 项目介绍

大多数桌面计时软件只统计“当前前台窗口”，不向第三方开放接口。AppMapper 不 Hook 这些软件，而是生成标题、图标和进程都贴近手机 App 的小窗口，让计时软件按原有逻辑识别和记录。

每个手机 App 对应一个独立的 Windows 映射 exe，由模板复制并写入图标。与单个动态窗口相比，这种方式更容易被各种识别逻辑接受。

## 目录结构

```text
AppMapper/
├── apps/
│   ├── android/             Android 工程（Gradle Wrapper 与构建配置）
│   │   └── app/             Android 客户端（Kotlin + Compose）
│   └── windows/
│       ├── controller/      Windows 总控端（C# WPF）
│       └── mapper/          映射小程序模板（C++ Win32）
└── protocol/                协议示例与共享说明
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

Android 端扫描PC上的二维码即可绑定电脑。二维码包含此电脑的公钥指纹用于验证电脑身份，一次性令牌用于授权长期配对。手机用 Android Keystore 中的设备密钥应答电脑的一次性挑战；验证码刷新、短暂断网或电脑重启后，会自动重连。

没有摄像头时，可使用手动输入 IP、端口和 6 位验证码，建立临时连接。临时连接不保存配对，也不会清除已有的长期配对；断线后需要重新输入验证码。

同步时，手机需保持前台服务运行，电脑需运行总控端。电脑端可在设置中开启「开机自启」，Windows 登录后会启动到托盘。手机目前不支持开机自启，重启后需打开 App 并启动同步。

电脑「设备」页可查看设备状态、移除手机。手机「设置 → 设备管理」支持保存多台电脑，卡片显示电脑名称、IP 地址、端口和添加时间。旧版配对记录没有添加时间，会显示「未记录」。

点击「添加电脑」扫码，配对成功后会立即连接这台电脑，并将它设为默认目标，原有电脑继续保留。再次扫描同一台电脑会更新地址和配对信息，保留最初的添加时间。

手机一次只向一台电脑同步。在设备管理中点击「连接此电脑」，会立即切换，并将这台电脑设为下次默认目标。

首次连接失败时，会停止同步并显示原因，可手动重试。连接成功后遇到短暂断网，会按该电脑保存的地址自动重连。电脑 IP 或端口变化时，请重新扫描当前的配对二维码。

点击「忘记电脑」并确认后，下次连接需要重新扫码。忘记当前连接目标会停止同步、清除默认目标，需手动选择其他电脑；忘记其他电脑不影响当前同步。

忘记当前已连接的电脑时，手机会同时通知电脑端移除配对。未连接时只移除手机端记录，电脑端残留的记录可在其「设备」页移除。临时连接不加入已保存的电脑列表，也不会改变默认目标。

## 前置环境

- **Android 客户端**：JDK 17/21、Android SDK Platform 37.0、Build Tools 36.0.0、Android Studio Panda 3（2025.3.3 Patch 1）或更高版本，最低运行系统为 Android 8.0。
- **Windows 总控端**：.NET 8 SDK。
- **Windows 映射端**：Visual Studio Build Tools，需勾选「使用 C++ 的桌面开发」和 Windows SDK。

## 构建

### Android 客户端

标准构建方式：

```powershell
.\apps\android\gradlew.bat -p apps/android :app:assembleDebug
```

Windows 下，`apps/android/gradlew.bat` 优先使用 JDK 17/21。系统默认 JDK 过新时，会自动尝试使用 Android Studio 自带的 JBR。

### Windows 总控端

```powershell
dotnet build apps\windows\controller\AppMapper.Controller.csproj
```

### Windows 映射端模板

```powershell
msbuild apps\windows\mapper\AppMapper.Mapper.vcxproj /p:Configuration=Release /p:Platform=x64
```

映射端构建完成后，将 `apps\windows\mapper\bin\Release\mapper-template.exe` 复制到总控端可执行文件的同级目录。总控端运行时会从该目录读取 exe 模板。

## 快速开始

1. 在 Windows 上启动总控端，主窗口会显示本机 IP、端口、验证码和二维码。
2. 确认 `mapper-template.exe` 已放在总控端可执行文件的同级目录（见上节构建说明）。
3. 手机和电脑接入**同一局域网**。
4. 在手机上安装并打开 Android 客户端，按提示授予「使用情况访问」权限（`PACKAGE_USAGE_STATS`）。
5. 扫描总控端二维码完成绑定。没有摄像头时，取消勾选，手动输入 IP、端口和验证码，建立临时连接。Android 17 及以上会在扫码或连接时申请局域网访问权限，请允许后继续。
6. 在手机上切换到任意前台 App，电脑任务栏会出现对应的映射窗口。手机回到桌面、锁屏或熄屏时，窗口自动关闭。

## 检查更新

Android 和 Windows 客户端每次进程启动后，都会自动检查一次 GitHub 正式版。返回前台、恢复托盘窗口或重建 Android 页面时，不会重复检查。

也可随时在 Android 的「设置 → 关于应用」或 Windows 的设置中点击「检查更新」，通过「更新说明」查看最新版本的 Git 提交标题、作者和完整正文。

发现新版本后，会弹出更新提示，同一构建只自动提醒一次。Android 在首页可见且权限流程结束后提示；Windows 在主窗口可见并激活后提示，托盘运行时会保留检查结果。

点击「前往下载」会打开该版本的 GitHub Release 页面，需手动下载安装 Android APK 或 Windows ZIP。Android 覆盖安装须保持相同包名和发行签名。

检查更新按 CI 构建号比较版本，正式版说明记录相对上一份可达正式版的提交。默认不提示实验版。断网、无法访问 GitHub 或请求受限时，会显示检查失败，可稍后手动重试。

CI 用基础版本的主版本、次版本和运行序号生成显示版本，例如 `0.1.17`。Android 和 Windows 使用相同的显示版本，发布附件分别为 `appmapper.android.0.1.17.apk` 和 `appmapper.windows.0.1.17.zip`，每个平台只发布一份。新客户端也能识别此前带有额外构建号的旧附件名。

`0.1.16` 的检查更新只识别旧附件名，无法发现采用新名称的后续版本。请先到 [GitHub Releases](https://github.com/youximi/AppMapper/releases) 手动安装一次新版，之后即可继续检查更新。不含检查更新功能的更早版本，也需要手动安装新版。

## 权限说明

Android 客户端需要以下权限，全部用于核心功能：

| 权限                                                    | 用途               |
|-------------------------------------------------------|------------------|
| `INTERNET` / `ACCESS_NETWORK_STATE`                   | 局域网 TLS 连接与 GitHub 更新检查 |
| `ACCESS_LOCAL_NETWORK`                               | Android 17 及以上的局域网访问（运行时授权） |
| `CAMERA`                                              | 扫描配对二维码          |
| `PACKAGE_USAGE_STATS`                                 | 读取当前前台 App |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 保持设备连接和后台同步 |
| `CHANGE_WIFI_MULTICAST_STATE` | 满足 Android 连接设备前台服务的权限要求 |
| `POST_NOTIFICATIONS`                                  | Android 13 及以上的通知运行时权限；拒绝不阻止前台服务启动 |


## 隐私与安全

- 前台应用映射和配对只通过局域网通信。检查更新通过 HTTPS 访问 GitHub，并发送软件版本标识，不上传配对记录、前台应用信息或日志；GitHub 会收到请求方的公网 IP。扫码绑定及后续按已保存地址重连时，通过二维码里的电脑公钥指纹验证电脑身份。手动临时连接不验证电脑身份，局域网内的攻击者可能冒充目标电脑，仅在应急时使用。
- 电脑身份和已配对手机公钥保存在软件目录 `config/pairing.dat`，由当前 Windows 用户的 DPAPI 加密。手机配对记录在 App 私有 no-backup 目录 `config/paired-computer.json`；私钥由 Android Keystore 管理。不要将 Windows 数据文件移到另一 Windows 用户下期待可以解密。
- 六位码只用于临时连接，长期绑定只能使用二维码中的随机令牌。两者都会在使用后或每 60 秒失效，不作为重连凭据保存，也不会记入日志。
- Windows 总控端仅监听用于配对和同步的 TCP 端口。
- 映射小程序是纯本地窗口程序，不联网。

## License

本项目采用 [GPL-3.0-only](./LICENSE) 协议，Windows 和 Android 发行版所用组件的许可和版权声明见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。

Windows 程序和 Android APK 均内置这两份文件，可分别在「设置 → 开源许可」和「设置 → 关于应用 → 开源许可」中离线查看。GitHub Release 提供对应构建提交的源码下载入口。

## 鸣谢

感谢以下开源项目的作者和维护者：

- [Jetpack Compose](https://developer.android.com/compose) 与 [Material 3](https://developer.android.com/jetpack/androidx/releases/compose-material3)：Android 界面、组件和主题。
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)：Android 端的异步任务和后台同步。
- [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) 与 [ZXing](https://github.com/zxing/zxing)：手机端配对二维码的扫描和识别。
- [WPF UI](https://github.com/lepoco/wpfui)：Windows 界面控件、主题和托盘支持。
- [QRCoder](https://github.com/Shane32/QRCoder)：电脑端配对二维码的生成。

完整的第三方组件清单和许可见 [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)。



# AppMapper

<img src="assets/branding/appmapper-icon.svg" width="96" height="96" alt="AppMapper 图标" />

AppMapper 将 Android 手机上的前台 App 实时映射为一个 Windows 普通窗口，让电脑端番茄钟或软件使用统计工具也能按桌面软件统计它的使用时长。

> 项目仍在早期开发阶段，尚未完善。

## 项目介绍

大多数桌面计时软件只统计“当前前台窗口”，不向第三方开放接口。AppMapper 不 Hook 这些软件，而是生成标题、图标和进程都贴近手机 App 的小窗口，让计时软件按原有逻辑识别和记录。

每个手机 App 都有独立的 Windows 映射 exe，通过复制模板、写入图标生成。与单个动态窗口相比，这种方式更容易被各种识别逻辑接受。

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
- **Windows 总控端**：C# WPF（.NET 11 RC2）、TLS 服务器、二维码配对、映射进程管理。
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

没有摄像头时，可手动输入 IP、端口和 6 位验证码建立临时连接。临时连接不保存配对，也不会清除已有的长期配对；断线后需要重新输入验证码。

同步时，手机的前台服务和电脑上的总控端都需要保持运行。电脑端可在设置中开启「开机自启」，Windows 登录后总控端会启动到托盘。手机目前不支持开机自启，重启后需打开 App 并启动同步。

电脑「设备」页可查看设备状态、移除手机。手机「设置 → 设备管理」可保存多台电脑，卡片显示电脑名称、IP 地址、端口和添加时间。旧版配对记录没有添加时间，会显示「未记录」。

点击「添加电脑」扫码。配对成功后会立即连接这台电脑，并将它设为默认目标，同时保留原有电脑记录。再次扫描同一台电脑会更新地址和配对信息，但保留最初的添加时间。

手机一次只向一台电脑同步。在设备管理中点击「连接此电脑」，会立即切换同步目标，并将这台电脑设为下次默认目标。

首次连接失败时，同步会停止并显示失败原因，可手动重试。连接成功后若遇到短暂断网，会按已保存的电脑地址自动重连。电脑 IP 或端口变化时，请重新扫描当前的配对二维码。

点击「忘记电脑」并确认后，下次连接需要重新扫码。忘记当前连接目标会停止同步并清除默认目标，之后需手动选择其他电脑；忘记其他电脑不影响当前同步。

忘记当前已连接的电脑时，手机会同时通知电脑端移除配对。未连接时只移除手机端记录，电脑端残留的记录可在其「设备」页移除。临时连接不加入已保存的电脑列表，也不会改变默认目标。

## 使用环境
- **Android 客户端**：Android 8.0 及以上。
- **Windows 总控端**：windows 10 22h2以上，并安装 [.NET 11 RC2 桌面运行时（x64）]。CPU 需支持 x86-64-v2（含 SSE4.2、POPCNT）。

## 开发环境 
- **Android 客户端**：Android Studio Quail 4（2026.1.4）或更高版本、JDK 27、Android SDK Platform 37.2、Build Tools 37.0.0 和 Command-line Tools 23.0。
- **Windows 总控端**：.NET SDK `11.0.100-rc.2.26504.105`，与根目录 `global.json` 保持一致。
- **Windows 映射端**：Visual Studio Build Tools，勾选“使用 C++ 的桌面开发”，安装 MSVC v143 和 Windows SDK。

## 构建

### Android 客户端

标准构建方式：

```powershell
.\apps\android\gradlew.bat -p apps/android :app:assembleDebug
```

安装 JDK 27，并将 `JAVA_HOME` 和 IDE 的 Gradle JDK 指向它。工程通过 `gradle-daemon-jvm.properties` 和 `jvmToolchain(27)` 固定构建 JDK；Wrapper 使用标准 Gradle 启动脚本，不再自动切换到旧版 JBR。Linux/macOS 下可执行 `./apps/android/gradlew -p apps/android :app:assembleDebug`。可运行 `:app:test :app:lintDebug :app:lintRelease` 检查。

### Windows 总控端

```powershell
dotnet build apps\windows\controller\AppMapper.Controller.csproj -c Release
```

### Windows 映射端模板

```powershell
msbuild apps\windows\mapper\AppMapper.Mapper.vcxproj /p:Configuration=Release /p:Platform=x64
```

映射端构建完成后，将 `apps\windows\mapper\bin\Release\mapper-template.exe` 复制到总控端可执行文件所在根目录。总控端运行时会从该目录读取 exe 模板。

### Windows 发布包

在仓库根目录完成上述映射端构建后，再发布总控端并复制模板：

```powershell
dotnet publish apps\windows\controller\AppMapper.Controller.csproj -c Release -r win-x64 --self-contained false -o dist\windows
Copy-Item apps\windows\mapper\bin\Release\mapper-template.exe dist\windows\
```

升级时，请保留原目录中的 `config/settings.json`、`config/pairing.dat` 和 `apps`。配对记录继续使用原 Windows 用户的 DPAPI，电脑身份和已配对设备无需重置。



## 快速开始

1. 在 Windows 上启动总控端，主窗口会显示本机 IP、端口、验证码和二维码。
2. 确认 `mapper-template.exe` 已放在总控端可执行文件的同级目录（见上节构建说明）。
3. 手机和电脑接入**同一局域网**。
4. 在手机上安装并打开 Android 客户端，按提示授予「使用情况访问」权限（`PACKAGE_USAGE_STATS`）。
5. 扫描总控端二维码完成绑定。没有摄像头时，取消勾选，手动输入 IP、端口和验证码，建立临时连接。Android 17 及以上会在扫码或连接时申请局域网访问权限，请允许后继续。
6. 在手机上切换到任意前台 App，电脑任务栏会出现对应的映射窗口。手机回到桌面、锁屏或熄屏时，窗口自动关闭。

## 检查更新

Android 和 Windows 客户端每次启动后，会按所选通道自动检查一次更新。返回前台或恢复托盘窗口不会重复检查。
也可在 Android 的「设置 → 关于应用」或 Windows 客户端设置中点击「检查更新」，通过「更新说明」查看版本改动。
「检查更新」下方可选择更新通道：

- **正式版（默认）**：只接收正式版更新。
- **测试版**：接收正式版和测试版中的最新适用版本。测试版极不稳定，仅供测试。

- 通道设置会在重启和覆盖升级后保留，安装测试版不会自动切换通道。切换通道会重新检查更新，并重置提醒记录。
发现新版本后，客户端会弹出提示，同一版本默认只自动提醒一次。点击「前往下载」打开对应的 GitHub Release 页面，手动下载并安装 Android APK 或 Windows ZIP。Android 覆盖安装需与已安装版本的包名、发行签名一致。
切回正式版通道不会提示降级。如果已安装版本高于最新正式版，会显示「当前通道暂无更新」。检查失败时，可稍后手动重试。

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

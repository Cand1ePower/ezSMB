# ezSMB

[官网](https://ezsmb.candlepower.cool/) · [下载 APK](https://github.com/Cand1ePower/ezSMB/releases) · [连接指南](https://ezsmb.candlepower.cool/guide/) · [Privacy](https://ezsmb.candlepower.cool/privacy/)

免费开源的 **Android 原生局域网 SMB 文件工具**。手机直接访问 Windows 共享文件夹、Linux Samba 和 NAS，采用 Kotlin、Jetpack Compose 与 Material 3。无需电脑端伴侣程序，无云账户、广告或统计 SDK。

**当前：0.3.1 公开预览版。** Android 10+；应用 ID `com.ezsmb.app`。国内商店和 Google Play 尚未上架。服务器兼容性和测试范围请先看 [验证与限制](docs/public/testing.md)。

## 能做什么

- 历史设备快速探测、mDNS 和有界 TCP 445 发现；真实 SMB 协商验证，始终支持手动连接。
- SMB2/SMB3 账户认证，Android Keystore 加密记住凭据；探测可用时提供明确的匿名入口。
- 通过 IPC$/SRVSVC 真正枚举服务器共享；服务端限制枚举时手动输入共享名或 SMB 路径。
- 文件夹浏览、逐级系统返回、目录收藏；图片、视频关键帧与文件夹媒体封面预览。
- 下载到 `Download/ezSMB`，通过系统选择器多文件上传，真实进度、队列、取消和从头重试。
- 同名冲突处理、安全临时文件及后台传输通知。动态配色、深色模式和原生 Android 交互。

## 开始使用

1. 电脑开启 SMB 共享，为账户授予文件夹权限；手机与电脑处于可互访的局域网。
2. 在 ezSMB 选择设备，使用电脑账户密码连接。Windows Hello PIN 不是账户密码。
3. 打开共享文件夹，点文件下载；点「上传」选择手机文件。服务器不允许列举共享时，从菜单手动填写共享路径。
4. 下载完成后可打开文件或所在目录。收藏目录后可从首页直达。

不要启用 SMB1、关闭系统签名或关闭整个防火墙。更多排错见 [Windows / NAS 设置指南](https://ezsmb.candlepower.cool/guide/)。

## 构建

需要 JDK 17+（验证环境使用 21）、Android SDK Platform 36 / Build Tools 35.0.0。Gradle Wrapper 已包含。将 SDK 路径写入未提交的 `local.properties`，或设置 `ANDROID_HOME`。

### EndeavourOS / Linux

安装 JDK 与 Android SDK 命令行工具，接受 SDK licenses 后安装 `platforms;android-36`、`build-tools;35.0.0`、`platform-tools`：

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Windows

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# 可选：处理 Windows JDK Unix-domain 临时目录兼容性
.\tools\build.ps1
```

当前本地构建在 Windows 验证，Linux 路径仍需 CI 验证。脚本不会修改系统代理、防火墙或共享设置。

### Release

通过环境变量设置 `EZSMB_KEYSTORE`、`EZSMB_STORE_PASSWORD`、`EZSMB_KEY_ALIAS`、`EZSMB_KEY_PASSWORD`，然后运行 `./gradlew assembleRelease bundleRelease`。不要提交签名密钥或密码。未配置密钥时生成的 Release 产物不能作为签名发布包。

Release APK：`app/build/outputs/apk/release/app-release.apk`。Play AAB：`app/build/outputs/bundle/release/app-release.aab`。Debug 与 Release 签名不同，不能互相覆盖安装；卸载会清除应用私有配置，迁移前请谨慎处理。

## 技术与边界

使用 SMBJ 0.14.0 + Rapid7 DCE/RPC 0.12.13 实现一套 SMB 传输引擎与服务器共享枚举。详见 [架构](docs/public/architecture.md)、[测试与已知限制](docs/public/testing.md)、[商店准备材料](docs/public/store-readiness.md)。

不支持断点续传。视频预览是关键帧，不是播放器。文件夹封面只检查有限数量的直接子文件。NAS、强制加密、域/DFS 等兼容性尚未完成全面验证。DHCP 地址改变可按服务器 GUID 更新，但服务器重启可能改变 GUID，需要重新关联收藏；路由器地址保留可减少此问题。

## English

**ezSMB** is an MIT-licensed native Android SMB2/SMB3 client for local network file browsing and transfer. Discover computers, enumerate server shares, browse folders, preview images/video frames, bookmark directories, download to Android Downloads and upload using the system document picker. Built with Kotlin, Jetpack Compose and Material You. No ads, analytics or developer cloud file relay.

This is a public preview, not a universal NAS compatibility guarantee. See the verification report for tested environments and limitations. Bug reports and reproducible server/device compatibility results are welcome.

## 反馈与许可证

[GitHub Issues](https://github.com/Cand1ePower/ezSMB/issues) 或 candlepower1112@gmail.com。请提供脱敏复现步骤，不要提交密码、私人文件或完整私人路径。

Copyright © 2026 CandlePower. 本项目采用 [MIT](LICENSE)，第三方组件遵守各自许可证，见 [NOTICE](NOTICE)。

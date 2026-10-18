# 应用商店提交准备

更新：2026-10-18。当前为公开预览版，尚未向国内商店或 Google Play 提交审核。

## 固定信息

| 字段 | 内容 |
|---|---|
| 应用名称 | ezSMB |
| 开发者展示名 | CandlePower（主体法律信息待开发者后台核验） |
| 支持邮箱 | candlepower1112@gmail.com |
| 包名 | com.ezsmb.app |
| 版本 | 0.3.1 / versionCode 6 |
| 最低系统 / target | Android 10 / API 36 |
| 类别 | 工具 / 文件传输 |
| 官网 | https://ezsmb.candlepower.cool/ |
| 隐私政策 | https://ezsmb.candlepower.cool/privacy/ |
| 源码许可证 | MIT，第三方组件另遵其许可证 |

## 可提交构建产物

- 国内商店：`app/build/outputs/apk/release/app-release.apk`，非 Debug、长期签名。
- Google Play：`app/build/outputs/bundle/release/app-release.aab`。
- 签名材料独立保存在开发机受限目录，未纳入源码。必须离线备份。后续同渠道更新须保持应用签名一致，版本号递增。
- Google Play App Signing 需在后台确定密钥管理方式。若希望国内 APK 与 Play 安装互相覆盖，应按 Play 支持流程保留同一应用签名，而不是误把上传密钥当成最终分发签名。
- 现有 Debug 版与 Release 签名不同，不能直接覆盖。不要为了测试自动卸载用户现有应用；卸载会清除私有配置和凭据。

## 商店文案

**简短介绍（中文）**：原生 Android 局域网 SMB 文件工具，轻松访问电脑与 NAS 的共享文件。

**Short description**: Native Android SMB client for local file browsing and transfers.

**详细介绍**：

ezSMB 让 Android 手机通过 SMB2/SMB3 直接访问局域网电脑与 NAS。自动发现设备，认证后浏览共享文件夹，点击文件下载到 Download/ezSMB，或使用系统文件选择器批量上传。

支持图片、视频关键帧和文件夹封面预览，收藏常用目录，查看真实传输进度与失败原因。采用原生 Material 3，支持动态配色及深色模式。无广告、无统计、无云账户，文件不经开发者服务器中转。

需要手机与服务器可互访，服务器已开启 SMB 共享并授予账户权限。自动发现或枚举被限制时，可手动输入地址和共享路径。当前为公开预览版，不支持断点续传；不同服务器和媒体格式的兼容性仍在完善。

## Google Play 待完成

- 开发者账户、身份、付款及联系信息核验（尚无后台操作权限/信息）；选择 Play App Signing 方案。
- 上传 AAB 到内部测试，完成商店预发布报告、设备兼容性和签名更新验证。
- 当前官方要求核对：[target API](https://developer.android.google.cn/google/play/requirements/target-sdk?hl=en)。本项目 target 36，提交当天再核查。
- 对适用的新个人账户，生产权限前需至少 12 名测试者连续参与 14 天封闭测试，随后申请生产访问；详见 [官方规则](https://support.google.com/googleplay/android-developer/answer/14151465)。构建通过不能代替这一要求。
- 完成 Data safety、广告（无）、内容分级、目标受众、应用访问说明和 dataSync 前台服务用途说明/演示视频。
- Data safety 草案：开发者不收集遥测或文件内容；账号与文件发送到用户选择的 SMB 服务器。应按 [官方定义](https://support.google.com/googleplay/android-developer/answer/10787469) 核对用户主动传输和第三方共享的适用例外，不能仅凭「无云服务」直接勾选所有否。SMB 签名不等于加密，不应一概声明全部数据传输加密。
- 准备 512×512 图标、1024×500 宣传图、实际应用手机截图（至少 2 张；以控制台最新要求为准）。不得把网站示意图当成应用截图。
- 审核人员需要在可控局域网测试 SMB 功能，提供自建测试服务器配置说明；不要公开真实家庭共享账号，也不要开放公网 445。

## 国内商店待完成

- vivo、小米、OPPO、华为、应用宝等分别核实个人/企业主体准入、软件著作权或权属证明、应用备案适用性和材料要求。开源 MIT 不自动替代商店权属或主体材料。
- 按 [工信部 APP 备案通知](https://www.miit.gov.cn/jgsj/xgj/wjfb/art/2023/art_dd783a581c9644a4aee10afa582811db.html) 及对应商店要求核实本局域网工具的适用流程；此文档不把未确认的豁免或备案状态写成事实。
- 核对官网在当前托管地域的备案及展示要求。当前没有提供备案号，不编造。
- 隐私首次提示、拒绝路径、权限最小化、开发者和联系方式、公开政策已经实现，仍需商店隐私检测和正式签名真机验收。
- 分别按后台规格提交图标、真实截图、简介、更新日志、隐私链接、权限用途和第三方组件清单。参考 [vivo 开发者文档](https://dev.vivo.com.cn/documentCenter/doc/12)，最终以登录后台的当前要求为准。

## 提交阻断清单

开发者账户与主体信息、备案/权属适用材料、真实商店截图与宣传素材、正式签名新装验收、审核可复现环境、Google Play 适用的封闭测试与生产访问申请。以上未完成，不代表已上架或必定过审。

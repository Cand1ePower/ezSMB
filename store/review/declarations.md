# 商店声明填写稿

适用代码版本：0.3.1 / 6。以下按实际实现整理；主体身份和控制台最终分类由开发者核对，不是已提交记录。

## 基本字段

应用名 `ezSMB`；包名 `com.ezsmb.app`；工具类；免费；无应用内购买；无广告；无广告 ID；开发者展示名 CandlePower；支持邮箱 candlepower1112@gmail.com。

隐私政策 `https://ezsmb.candlepower.cool/privacy/`，网站 `https://ezsmb.candlepower.cool/`。不提供 ezSMB 账户注册，因此没有 ezSMB 云账户删除流程；SMB 账户由用户自己的服务器管理。清除应用数据不能删除用户服务器上的账号。

## 权限用途（可复制）

| 权限 | 用途 |
|---|---|
| INTERNET | 连接用户选择的 SMB 服务器，发现和访问局域网共享 |
| ACCESS_NETWORK_STATE | 判断网络状态并选择局域网发现接口 |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC | 用户主动上传、下载时运行前台传输服务 |
| POST_NOTIFICATIONS | 显示用户发起的文件传输状态 |
| WAKE_LOCK | 文件传输期间降低设备休眠造成中断的风险 |

不申请定位、相机、麦克风、通讯录、全盘文件管理或广告标识符。上传由系统文件选择器授权具体 URI，下载使用 MediaStore。

## Google Play 前台服务声明

类型：dataSync。用途：Network transfer / Upload or download。

功能说明（英文）：

The user explicitly taps a remote file to download it or selects local documents to upload to an SMB share. A dataSync foreground service processes the transfer queue and displays an ongoing notification. The user can view progress and cancel tasks from the app. The service stops when the queue finishes and handles Android's foreground-service timeout.

延迟或中断的影响（英文）：

Delaying the task prevents the user's requested file from being available when expected. Interrupting it leaves an incomplete transfer, which is reported as failed or interrupted rather than complete. The app uses temporary upload files and pending download entries. Retry starts from the beginning; resumable transfers are not implemented.

演示视频要实际录制：进入共享目录 → 点一个较大的测试文件下载 → 显示传输进度 → 回桌面并展开通知 → 回到 app 取消或等待完成。再演示系统选择器发起上传。只录专用测试文件，不出现密码。需要后台能访问的视频链接，不能填写示意视频或不存在的链接。

来源：[Google 前台服务声明要求](https://support.google.com/googleplay/android-developer/answer/13392821)。

## Data safety 事实底稿

| 信息 | 实际去向与用途 |
|---|---|
| 设备名、IP、共享路径、收藏 | 本机持久化，用于连接与浏览；访问请求包含目标路径 |
| SMB 用户名/密码 | 用于用户指定服务器的 SMB 认证；记住时本机 Keystore 加密；不发送给开发者 |
| 上传/下载文件（可包括文档、照片、视频、音频） | 手机与用户指定的服务器直接传输，应用功能用途 |
| 缩略图 | 有界内存缓存，不发给开发者 |
| 传输任务状态 | 本机持久化，用于显示完成、失败或中断 |
| 统计、广告 ID、崩溃遥测 | 没有相应 SDK 或开发者收集端 |

不要凭“开发者不收集”就机械选择“没有数据离开设备”。先按 [官方定义和例外](https://support.google.com/googleplay/android-developer/answer/10787469) 核对用户指定服务器传输是否需要按 collection/sharing 申报；如果控制台无法明确对应，请向 Play 支持提交上述事实底稿。若需申报，文件类型按实际支持选择、目的为应用功能，传输由用户操作发起；不要选择广告或分析用途。所有连接并非都保证内容加密，不能一概勾选“全部传输加密”。

## 内容分级与受众

应用本身不提供内容库、聊天、社交、广告或购买；用户访问自己有权限的服务器文件。按 IARC 实际问卷如实填写，不能自行指定一个预设评级。目标年龄和是否面向儿童需要开发者按实际发布对象决定，不能为省步骤任意勾选。

# 审核复现说明 / App access

ezSMB does not provide a developer-hosted account service. It connects to an SMB2/SMB3 server chosen by the user. Server credentials belong to that server, not to an ezSMB account. Core functionality requires a reachable SMB share with read/write permissions; an empty review network cannot demonstrate file transfers.

## 可直接提交的英文说明

This app is a local-network SMB file client. There is no ezSMB registration or cloud login. To review file access, connect an Android device and an SMB2/SMB3 server to a mutually reachable private network. Create a test share containing non-sensitive sample files and grant a test account read/write access. Enter the server's address in Manual connection if automatic discovery is unavailable, then enter that server account's credentials. Open the share, tap a file to download it to Download/ezSMB, and use Upload to select a document with Android's system picker. Do not enable SMB1 or disable SMB signing. The server may deny share enumeration; in that case enter its share name through the manual share option. Please contact candlepower1112@gmail.com if your review environment cannot provide a local SMB server.

## 开发者可复现的 USB 隔离环境（不是审核已接受的证明）

此方案使用真实 TCP/SMB2 测试服务，只绑定电脑回环地址，不更改系统共享和防火墙。普通 Windows/NAS 兼容验证仍应另做。

1. 安装 Python 3.11+ 和 Android platform-tools，USB 连接已授权的 Android 测试设备。
2. 在源码目录建立独立虚拟环境：`python -m venv .review-venv`。Windows 用 `.review-venv/Scripts/python.exe -m pip install impacket==0.13.1`；Linux 用 `.review-venv/bin/python -m pip install impacket==0.13.1`。
3. 用该 Python 运行 `tools/smb_test_server.py`。保持终端打开。它生成隔离的 0B、1KB、100MB 文件，并把随机测试账号写入 `.tools/test-login.properties`。该文件不要公开或提交审核资料。
4. 执行 `adb reverse tcp:1445 tcp:1445`。ezSMB「手动连接」填 `127.0.0.1`，更多选项端口填 `1445`。输入上一步生成的账号密码，共享名 `EZTEST`。USB 回环地址不会被局域网自动发现，这是正常的。
5. 打开 EZTEST，下载测试文件；完成后点「打开目录」。上传一个专用测试文件到该共享，再下载读回并在电脑比对 SHA-256。
6. 先等待任务结束，退出测试连接；Ctrl+C 停止服务，执行 `adb reverse --remove tcp:1445`。

商店审核不一定允许 USB/ADB 或自行搭建 SMB。提交前应通过审核后台确认可复现方式，不能把家庭账号或公网 445 当成解决方案。后台的“应用访问”应如实说明核心功能依赖服务器认证，而不是直接填写全部功能无需访问条件。

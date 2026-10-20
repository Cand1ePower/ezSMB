# 界面截图

ezSMB 0.3.1，采集日期 2026-10-20。PNG 原图 1080 × 2400，使用 ADB 从运行中的应用直接采集，未重绘或修改画面。

| 文件 | 页面 |
| --- | --- |
| `files-v0.3.1.png` | 浅色文件浏览 |
| `transfers-v0.3.1.png` | 100MB 文件下载中的任务弹层 |
| `files-dark-v0.3.1.png` | 深色文件浏览与迷你传输条 |

画面中的目录与文件来自专用本地 SMB2 测试共享。进度与速度为当时实际传输状态，不代表设备或网络的性能承诺。

## 更新方法

1. 安装待展示版本，准备不含私人信息的测试共享。
2. 在设备上打开目标页面；文件与传输截图应使用真实 SMB 连接和任务。
3. 运行截图脚本，逐张检查设备名、路径和通知，更新根 README 的图片链接与版本。

```sh
python tools/capture-review.py --adb adb --serial <设备序列号> --output docs/screenshots/<页面>-<版本>.png
```

脚本检查 ezSMB 是否位于前台，并拒绝覆盖已有文件。无效设备、其他前台应用或非 PNG 输出均不会保存。

维护者可用 `tools/screenshot-fixture.py` 创建隔离测试共享（需要 Python 和 impacket），只监听 `127.0.0.1:1445`，不改变系统共享。它在被 Git 忽略的 `output/screenshot-share/` 中生成公开样例，并生成一次性测试账户。通过 `adb reverse tcp:1445 tcp:1445` 连接后，在 App 中输入 `smb://127.0.0.1:1445`，使用 `output/screenshot-login.json` 中的临时账户。结束后停止服务、移除 reverse 映射并删除临时账户文件；不要提交凭据或原始日志。

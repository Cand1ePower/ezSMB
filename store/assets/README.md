# 商店图片

- `icon-512.png`：512×512、8-bit RGBA PNG，图案取自应用自身的 launcher vector，商店版不预裁圆角。
- `feature-1024x500-zh.png`：1024×500、8-bit RGB PNG，无 alpha。为商店宣传图，不是应用界面截图。
- HTML 是可编辑排版源；通过浏览器渲染导出。素材已检查尺寸、颜色类型和画面。
- 图标替代文字：电脑屏幕中显示一个共享文件夹。
- 宣传图替代文字：ezSMB 支持在手机上浏览电脑共享目录、上传下载和收藏常用文件夹。

真实界面截图待连接授权手机后采集。不要使用内部测试证据中的私人设备名、账户或目录。建议采集：设备首页、含媒体的文件夹、传输队列，至少两张真实页面。只保留 app 当前版本实际提供的功能。

可运行：`python tools/capture-review.py --adb <adb路径> --output output/store-kit/02-artwork/screenshots/home.png`。先在手机打开目标 ezSMB 页面；脚本拒绝截图其他前台应用和覆盖旧图。拍摄前开启免打扰，并使用专用测试目录。

规格来源：https://support.google.com/googleplay/android-developer/answer/9866151

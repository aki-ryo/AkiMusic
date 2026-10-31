# AkiMusic

一个 **iOS 27 Liquid Glass** 风格的音乐播放器，信息架构照着 Apple Music 来，数据与在线播放走 **网易云音乐** 接口。

三个客户端：浏览器版（同一套前端）、**Jetpack Compose 原生 Android**、**SwiftUI 原生 iOS**。

<p>
  <img alt="version" src="https://img.shields.io/badge/version-0.1.3--beta-fa2d48" />
  <img alt="android" src="https://img.shields.io/badge/Android-8.0%2B-7c7aff" />
  <img alt="ios" src="https://img.shields.io/badge/iOS-16.0%2B-8e8e93" />
  <img alt="use" src="https://img.shields.io/badge/for-learning%20only-lightgrey" />
</p>

<a href="https://github.com/aki-ryo/AkiMusic/releases/latest"><b>⬇ 下载 APK / IPA</b></a>

---

## 运行（桌面 / 浏览器）

**方式一（Windows）**：双击 `start.bat`，浏览器会自动打开。

**方式二**：命令行里执行

```bash
python server.py          # 默认 8777 端口，被占用会自动 +1
```

然后打开 <http://localhost:8777>。只需要 Python 3.9+，没有任何第三方依赖。

> 网易云的 Web API 不返回 CORS 头，浏览器直接 fetch 会被拦。`server.py` 负责：`/api/*` 转发（补 Referer / UA / Cookie，5 分钟内存缓存）、`/img?u=` 图片代理（让 canvas 能取封面像素）、`/` 托管 `public/`。接口白名单写在 `ALLOW_PATTERNS`，避免变成任意代理。

---

## 设计：Liquid Glass

iOS 26 引入、iOS 27 强化的材质。这一版把原来的 Material 3 Expressive 全部撤掉重做。

**玻璃由三层合成**

| 层 | 作用 |
| --- | --- |
| `backdrop-filter: blur(34px) saturate(190%)` | 背景内容扩散、提饱和，玻璃后面的东西还在 |
| 1px 内描边（暗） | iOS 27 新增的 **darkened edge**，把控件和内容拉开层次 |
| 顶部 0.5px 白高光 | **specular highlight**，模拟玻璃边缘的镜面反射 |

再叠一层极淡的投影，让它「浮」在内容之上而不是贴在上面。

**几个标志性行为**

- **悬浮胶囊标签栏**：底部导航是一枚离边 12px 的玻璃胶囊，向下滚动收缩成纯图标（62px → 52px），向上滚动展开。
- **顶栏吸附**：大标题随内容滚走，顶栏渐变成玻璃条并浮出页面标题；移动端这一步会把搜索框让位给标题，搜索交给底部标签页。
- **歌词模糊动效**：当前行清晰、放大、纯白；其余行 `blur(2.4px)`、`scale(.955)`、40% 不透明度，切换用 `cubic-bezier(.32,.72,0,1)` 过渡。
- **玻璃透明度滑块**：侧栏底部那枚滑块对应 iOS 27 设置里的「超清 ↔ 完全着色」，0–100 连续调节，实时改写所有玻璃的 alpha。
- **动态色相**：从封面用 OKLab 聚类取主色，只用来染环境光与高光，不像上一版那样染色整个界面——玻璃应该是中性的。

---

## 三种形态

**桌面 / 浏览器**：侧栏 → 顶栏搜索 → Hero 轮播 + 横向书架 → 悬浮播放条 → 全屏正在播放。

**Android**：`android-native/` 是 **Jetpack Compose 原生应用**（非 WebView）。数据层直连网易云接口（Android 无 CORS 限制），`MediaPlayer` 播放，Compose 自绘液态玻璃材质，全局使用 Inter 字体。构建：`gradle :app:assembleRelease`。

**iOS**：`ios/` 是独立的 **SwiftUI 原生工程**，不是 WebView。用 XcodeGen 生成工程文件，玻璃材质由自定义 `ViewModifier` 合成（`.ultraThinMaterial` + 双层描边 + 高光），窗口控件、标签栏、播放器、歌词全部原生。

```bash
# 在 Mac 上
cd ios && brew install xcodegen && xcodegen generate
open AkiMusic.xcodeproj
```

`.ipa` 由 GitHub Actions 的 macOS runner 编译（见 `.github/workflows/ios.yml`），产物为**未签名**包，需要 Sideloadly / AltStore 用自己的 Apple ID 侧载。

---

## 功能

- 发现页：轮播精选榜单、继续聆听、新歌速递、推荐歌单、排行榜
- 搜索：单曲 / 歌单 / 专辑 / 歌手，实时搜索、热门词
- 排行榜、歌单详情、专辑详情、歌手页
- 播放：队列、上一首 / 下一首、随机、列表循环 / 单曲循环、进度拖拽、音量、静音
- 全屏播放页：封面取色、LRC 歌词逐行模糊/高亮并自动滚动、点歌词跳转、播放队列
- 资料库：我喜欢的音乐、最近播放，全部存在本地
- 收藏歌单：侧边栏「+」粘贴链接或 ID 即可加入
- 深浅主题、`MediaSession` 支持系统媒体键
- 快捷键：`Space` 播放/暂停，`Alt + ←/→` 切歌，`Esc` 关闭弹层

---

## 关于版权与可播放性

接口只返回**可公开试听**的音源：

- 免费曲目：320kbps 直接播放
- VIP 曲目：多数返回 30 秒试听，界面会提示
- 完全无音源的曲目：自动跳过并提示，不会卡住队列

仅供学习与个人使用，音乐版权归网易云音乐及各版权方所有。

---

## 目录

```
.
├─ server.py               本地服务：静态资源 + 接口代理 + 图片代理
├─ start.bat               Windows 一键启动
├─ public/
│  ├─ index.html           结构 + SVG 图标精灵
│  ├─ css/
│  │  ├─ tokens.css        iOS 设计令牌（语义色 / 玻璃参数 / 圆角 / 弹簧曲线）
│  │  └─ app.css           玻璃材质、布局、歌词动效
│  └─ js/
│     ├─ app.js            路由、事件委托、滚动吸附、透明度滑块
│     ├─ api.js            网易云接口封装 + 数据归一化 + LRC 解析
│     ├─ player.js         播放引擎（音源解析、跳过、歌词、MediaSession）
│     ├─ views.js          首页 / 搜索 / 歌单 / 专辑 / 歌手 / 资料库视图
│     ├─ theme.js          OKLab 取色引擎
│     ├─ store.js          localStorage 状态
│     └─ util.js           工具函数
├─ android-native/         Jetpack Compose 原生 Android 工程（Kotlin）
│  └─ app/src/main/java/com/akiryo/akimusic/
│     ├─ data/             数据模型、网易云接口、播放引擎、资料库
│     └─ ui/               主题（Inter 字体）、玻璃材质、页面、底栏、播放页
├─ ios/                    SwiftUI 原生工程（XcodeGen）
├─ dist/                   签名好的 APK
└─ docs/                   截图
```

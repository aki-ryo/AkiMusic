# AkiMusic · iOS

一个仿 Apple Music 的原生 SwiftUI 音乐播放器，数据来自**网易云音乐公开 Web 接口**（无需登录），视觉遵循 **Liquid Glass（液态玻璃）** 设计语言：半透明材质、会折射背景的卡片、玻璃边缘的暗描边 + 顶部高光、悬浮胶囊标签栏、滚动收缩、歌词模糊过渡。

- 部署目标：**iOS 16.0**
- 纯原生：SwiftUI + AVFoundation + URLSession，**零第三方依赖**
- 工程由 **XcodeGen** 生成（仓库里没有 `.xcodeproj`）
- 未签名 IPA 由 GitHub Actions 在 `macos-14` 上产出

---

## 目录

```
ios/
├─ project.yml                     XcodeGen 工程描述（Info.plist 也在这里生成）
├─ AkiMusic/
│  ├─ App/
│  │  ├─ AkiMusicApp.swift         入口，注入 AudioPlayer / LibraryStore
│  │  └─ RootView.swift            悬浮标签栏 + 迷你播放条 + Toast
│  ├─ Core/
│  │  ├─ Models.swift              Song / 排行榜 / 歌单 / 专辑 / 歌手 + 容错解码
│  │  ├─ NetEaseAPI.swift          网易云接口封装（URLSession + async/await）
│  │  ├─ AudioPlayer.swift         AVPlayer 队列 / 随机 / 循环 / 歌词同步 / 跳过无音源
│  │  ├─ Lyrics.swift              LRC 解析（支持逐行翻译）
│  │  └─ LibraryStore.swift        UserDefaults 持久化「我喜欢」「最近播放」
│  ├─ Design/
│  │  ├─ Theme.swift               颜色 / 字体 / 圆角 / 动效 / 时间格式化
│  │  └─ Glass.swift               玻璃材质 ViewModifier、玻璃按钮、按压反馈
│  ├─ Views/
│  │  ├─ HomeView.swift            现在就听：精选榜单轮播 + 继续聆听 / 新歌 / 推荐
│  │  ├─ ChartsView.swift          排行榜
│  │  ├─ SearchView.swift          玻璃搜索框 + 防抖 + 四类结果
│  │  ├─ LibraryView.swift         资料库
│  │  ├─ PlaylistView.swift        歌单详情 + 专辑详情
│  │  ├─ ArtistView.swift          歌手页
│  │  ├─ NowPlayingView.swift      全屏播放页（模糊背景、逐行歌词、进度拖拽、队列）
│  │  ├─ MiniPlayer.swift          迷你播放条
│  │  └─ Components.swift          封面 / 歌曲行 / 书架卡片 / 骨架屏 / 空状态
│  └─ Resources/Assets.xcassets    AppIcon / AccentColor 占位
└─ README.md
```

`.github/workflows/ios.yml` 在仓库根目录。

---

## 在 Mac 上打开

需要 macOS + Xcode 15 或更新版本，以及 [XcodeGen](https://github.com/yonaskolb/XcodeGen)。

```bash
brew install xcodegen

cd ios
xcodegen generate      # 生成 AkiMusic.xcodeproj
open AkiMusic.xcodeproj
```

然后在 Xcode 里：

1. 选中 `AkiMusic` target → Signing & Capabilities → 勾选 **Automatically manage signing**，选择自己的 Team；
2. 选一台 iOS 16 及以上的真机或模拟器，⌘R 运行。

> 模拟器可以直接播放（AVPlayer 走系统网络），但音源 CDN 偶尔会对模拟器 UA 限流，真机更稳。

### 改了 `project.yml` 之后

重新执行 `xcodegen generate`。**不要手写或提交 `.xcodeproj`**，它是生成物，CI 也会重新生成。

---

## 在 CI 里出包

`.github/workflows/ios.yml`：

- 触发：`workflow_dispatch`（手动）或推送 `v*` tag；
- 环境：`macos-14`；
- 步骤：`brew install xcodegen` → `cd ios && xcodegen generate` → `xcodebuild` 未签名构建 → 打包 `Payload/` 成 IPA → 上传 artifact → `gh release upload` 到 `v0.1.1-beta`（`--clobber` 覆盖旧包）。

手动出包：GitHub 仓库 → **Actions** → 左侧 **iOS** → **Run workflow**。

打 tag 出包：

```bash
git tag v0.1.1-beta
git push origin v0.1.1-beta
```

构建完成后，Release 页面会有 `AkiMusic-0.1.1-beta-unsigned.ipa`，同时 Actions 的 artifact 里也有一份。

---

## 侧载安装（未签名 IPA）

IPA 没有签名，不能直接双击安装，需要自签工具：

- **Sideloadly**（Windows / macOS）：连上 iPhone，把 IPA 拖进去，填 Apple ID，Start。免费账号签名的 App 有效期 7 天，到期重签。
- **AltStore / SideStore**：装一次 AltServer，之后在手机上刷新即可。
- **TrollStore**（仅部分系统版本）：可直接安装未签名 IPA，永久有效。

安装前需要在 iPhone 上信任开发者证书：设置 → 通用 → VPN 与设备管理。

---

## 实现要点

### Liquid Glass 是自己写的

iOS 26 才有的 `.glassEffect()` / `GlassEffectContainer` **没有使用**，全部基于 iOS 15 起可用的 `Material` 手搓，保证 iOS 16 部署目标能编译：

```
.ultraThinMaterial 底座
+ 斜向白色高光渐变（0.16 → 0.02）
+ 外侧 1pt 暗描边 Color.black.opacity(0.28)
+ 内侧 1pt 顶部亮边（白 0.45 → 0.06 → clear）
+ 极淡投影（black 0.18 / radius 24 / y 10）
```

对应 `Design/Glass.swift` 的 `GlassSurface` / `GlassBackground` / `.glass(cornerRadius:)` / `GlassCapsuleButton`。标签栏、迷你播放条、搜索框、分段控件、资料库卡片都用它。

### 标签栏

`RootView` 底部是**悬浮玻璃胶囊**（左右各 16pt、底部 safeArea + 8pt），不贴边。各页面用 `ScrollOffsetObserver` 把「是否向下滚动超过 34pt」写进 `PreferenceKey`，`RootView` 收到后把高度从 64 收到 50、图标缩小、隐藏文字，`.spring` 过渡。

### 正在播放

- 背景：封面放大 + `blur(60)` + 黑色 0.55 遮罩 + 上下渐变，文字永远可读；
- 封面 300×300（小屏自适应到 220），圆角 14，投影 `black 0.35 / 28 / y14`；
- 歌词：`ScrollViewReader` 居中滚动，当前行 22 semibold 纯白，其它行 20 regular、`white.opacity(0.35)`、**`blur(2.5)`**、`scaleEffect(0.96)`，0.35s ease-in-out；点歌词可 seek；
- 进度条是自绘 4pt 细条，支持拖拽预览时间；
- 播放暂停按钮是实心白色圆（58pt，图标黑色），不套玻璃——和 Apple Music 一样作为对比。

### 播放器

`AudioPlayer` 是 `@MainActor` 单例，`AVPlayer` + `AVAudioSession(.playback)`：

- 队列、上一首 / 下一首、随机（真实播放顺序表，切歌不重复）、顺序 / 列表循环 / 单曲循环；
- `addPeriodicTimeObserver` 每 0.25s 同步进度与歌词行；
- 拉不到音源（VIP / 下架）自动跳过，**连续 5 首**失败就停下并提示，避免死循环；
- 音源 URL 如果是 `http://` 会先替换成 `https://` 再交给 AVPlayer。

### 接口

`NetEaseAPI` 统一加 `Referer: https://music.163.com/` 与 iPhone UA，直连 `music.163.com`（iOS 没有 CORS，不需要网页版那个 Python 代理）。

Info.plist 里开了 `NSAppTransportSecurity → NSAllowsArbitraryLoads = true`，因为部分音源 CDN 只给 http。

### 容错

`Song` 的自定义 `init(from:)` 同时兼容两种字段命名（`ar`/`al`/`dt` 与 `artists`/`album`/`duration`），所有字段缺失都有默认值；`PlaylistSummary` / `PlaylistDetail` 也兼容 `coverImgUrl` / `picUrl` / `desc` / `description`。任何一条脏数据都不会让整页解析失败。

---

## 已知限制

- **未签名 IPA**：免费 Apple ID 签名 7 天有效，需重签；不能上架。
- **VIP 曲目**：接口通常只返回 30 秒试听片段，或直接没有 `url`，界面会提示并自动跳过。
- **专辑页**：网易云公开接口没有「按专辑取曲目」的端点，`AlbumDetailView` 用「专辑名 + 歌手」搜索后按专辑名过滤，个别专辑可能不全。
- **歌手页**：同理，用歌手名搜索热门单曲与专辑。
- 本工程仅供学习与个人试听，音乐版权归网易云音乐及各版权方所有。

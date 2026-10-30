# Tandroid Launcher

一个基于 **Jetpack Compose for TV** 的 Android TV / Google TV 启动器。目标设备：Sony A80J（Google TV，Android 10）。

## 环境

- Android Studio（最新稳定版，自带 JDK 21 即可）
- SDK：compileSdk 35，minSdk 29
- 模拟器：Device Manager → TV → *Television (1080p)* → 系统镜像选 **Google TV, arm64-v8a**

## 本地预览（推荐工作流）

1. **Compose Preview**：打开 `ui/HomeScreen.kt`，右侧 Split/Design 视图就能看到 1080p 的电视画面（假数据），改完代码会自动刷新。
2. **模拟器 + Live Edit**：Settings → Editor → Live Edit 选 *Push edits automatically*，Run 到 Google TV 模拟器后，改 Compose 代码会实时生效。
   - 键盘方向键对应遥控器方向键，回车 = 确认，Esc = 返回
3. **真机**：只在阶段性验收时用（见下文）。

## 编译与运行（速查）

### 在 Android Studio 里跑模拟器（日常）

1. 顶部设备下拉框选 Google TV 模拟器
2. **⌥⌘Y**（File → Reload All from Disk）—— 代码是在 Studio 外面改的时候一定先做这步
3. 点绿色 **▶ Run**（或 **⌃R**）：自动编译、安装、启动
4. 模拟器里按 Home 键回到桌面（第一次会让你选默认桌面，选 Tandroid Launcher）

> ⚠️ 外部（比如 Claude）改代码时，先**关掉 Studio 里对应文件的标签页**（问是否保存选 Don't Save）。
> 否则 Studio 自动保存时会把编辑器里的旧内容写回磁盘，改动就丢了。
> 确认改动还在：`grep -n "关键字" <文件路径>`

### 命令行打包（发给别人 / 装到电视）

```bash
cd ~/Documents/projects/Tandroid-Lanucher
./gradlew assembleRelease   # 正式版：测试开关永远关闭
cp app/build/outputs/apk/release/app-release.apk ~/Downloads/Tandroid-Launcher-<版本号>.apk
```

版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`，每次发新版加一。

### 装到电视

```bash
adb connect <电视IP>:5555
adb install -r ~/Downloads/Tandroid-Launcher-<版本号>.apk   # -r 覆盖安装，保留排序、城市等设置
```

### 提交代码

```bash
git add -A && git commit -m "说明" && git push
```

`*.apk` 已在 .gitignore 里，内置的 `assets/store/bbll.apk` 不会被推到 GitHub。

### local.properties 开关（不进 git）

| 配置 | 作用 |
|---|---|
| `tmdb.token=…` | TMDB 读取令牌，影视背景图需要 |
| `weather.city=杭州` | 固定天气城市（也可以在电视上 天气 → 切换城市） |
| `debug.demoApps=true` | 调试版加一批假应用：测背景图来源、系统应用文件夹 |
| `debug.tvBrand=sony` | 调试版假装成某个电视品牌，测文件夹 Logo（sony / hisense / tcl / xiaomi / samsung / lg / philips / sharp / skyworth / google） |

改了 `local.properties` 要重新 ▶ Run 才生效；`debug.*` 只影响调试版，打包给别人的版本不受影响。

### 排查问题

```bash
adb logcat -s Weather Backdrops                              # 天气、背景图为什么没出来
adb shell dumpsys gfxinfo com.tangtongda.tlauncher reset     # 清零帧统计，然后操作 10 秒
adb shell dumpsys gfxinfo com.tangtongda.tlauncher | grep -E "Total frames|Janky|percentile"   # 卡不卡
```

## 真机调试（Sony A80J）

```bash
# 电视：设置 → 系统 → 关于 → 连续点击「版本号」7 次 → 开发者选项里打开 USB 调试
adb connect <电视IP>:5555      # 首次连接需要在电视上点允许
# 之后直接在 Android Studio 里 Run 到这台设备
```

## 设为默认启动器（Google TV）

Google TV 把 Home 键锁定在官方启动器上。功能稳定后可以禁用官方启动器：

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
# 恢复：
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

> 系统更新后官方启动器可能会被重新启用，再执行一次禁用命令即可。建议先在模拟器里演练一遍。

## 项目结构

```
app/src/main/java/com/tangtongda/tlauncher/
├── MainActivity.kt          # 入口，拦截返回键
├── data/
│   ├── AppInfo.kt           # 应用模型（banner / icon）
│   └── AppRepository.kt     # 查询应用、启动应用、监听安装和卸载
└── ui/
    ├── HomeViewModel.kt
    ├── HomeScreen.kt        # 首页、应用卡片、时钟，以及所有 @Preview
    └── theme/Theme.kt
```

## 设计规范

UI 参考 tvOS HIG skill，换算到 Android TV。1080p 电视是 xhdpi，屏幕为 960×540dp，1dp = 2px；tvOS 的 pt 在 1080p 下等于 px，所以 tvOS 的 29pt 约等于这里的 15sp。

| 规则 | 落地 |
|---|---|
| FOCUS-01 焦点态明显 | 放大 1.1 倍 + 白色描边 + 蓝色辉光，标签变亮加粗 |
| FOCUS-05 焦点目标够大 | 卡片 176×99dp（352×198px），大于 250×150 的下限 |
| FOCUS-06 默认焦点 | 启动时聚焦第一张卡片 |
| FOCUS-07 焦点记忆 | `focusRestorer`，回到这一行时恢复上次的位置 |
| REMOTE-07 点击反馈 | 按下缩小到 0.95 |
| DISTANCE-01 / 07 字号字重 | 标签 16sp、标题 24sp，不用 Light 字重 |
| DISTANCE-05 留白与露出 | 一屏约 4.4 张卡片，下一张露出一截，不需要滚动条 |
| DISTANCE-06 安全区 | 水平 48dp、垂直 27dp，比 tvOS 的 60pt 更保守 |

不适用于 Android 启动器的部分：LSR 视差图层、Top Shelf、Siri、播放器相关规则。

> Preview 用的是自定义 spec `960dp×540dp @320dpi`，和真机一致。`Devices.TV_1080p` 的宽度是按 1920dp 定义的，用它预览的话，元素看起来会比真机上小一半。

## 首页背景图

每个应用按下面的顺序找自己的背景图，找到就停：

1. **应用自己的数据，或对应品类的榜单**：
   - B 站：官方热门（无需 key）
   - YouTube：配置了 `youtube.apiKey` 用官方热门视频，否则用 TMDB 热门剧集 + 热门电影；YouTube Kids 用动画 / 合家欢
   - 音乐应用（声明 `CATEGORY_AUDIO`，或 Spotify、网易云、QQ 音乐等）：Apple Music 最多播放专辑榜，封面显示在右侧
2. **TMDB 上属于该平台的内容**：按 TMDB 的「观看平台」筛选，例如 Netflix 只显示 Netflix 上的剧
3. **TMDB 本周热门**：只给视频类应用（应用声明了 `CATEGORY_VIDEO`，或 Plex / Kodi / Jellyfin）
4. **都没有**：显示应用自己的横幅图或图标主色（Play 商店、设置、工具类应用）

焦点停留时每 12 秒换一张。日志可在 Logcat 里搜 `Backdrops` 查看。

`local.properties` 配置（文件已被 git 忽略）：

```properties
tmdb.token=eyJhbGciOi...          # TMDB API Read Access Token（必填）
tmdb.region=HK                    # 可选：匹配观看平台用的地区，默认 HK
youtube.apiKey=AIza...            # 可选：YouTube Data API v3
# tmdb.apiBase=https://your-proxy.example.com/3/      可选：反向代理
# tmdb.imageBase=https://your-proxy.example.com/t/p/
```

> 本产品使用 TMDB API，但未获得 TMDB 的认可或认证。显示 TMDB 图片时界面会附带来源说明。

## 天气

导航栏右侧显示当前天气，数据来自 [Open-Meteo](https://open-meteo.com)（免费、无需 key，数据按 CC BY 4.0 授权：Weather data by Open-Meteo.com）。每 30 分钟刷新，失败时保留上一次的结果。

位置默认按电视的公网 IP 估算（ipwho.is），也可以在 `local.properties` 里指定：

```properties
weather.city=上海
# 或者精确坐标
# weather.lat=31.23
# weather.lon=121.47
```

## Roadmap

- [ ] 顶部 Tab 栏：应用 / 输入源 / 设置（TAB-01、TAB-04）
- [ ] 收藏 / 排序（DataStore）
- [ ] 壁纸与背景模糊
- [ ] 信号源（HDMI）快捷入口
- [ ] 天气 / 网络状态
- [ ] Home 键行为与无障碍服务兜底

## 致谢

第三方图标、品牌标志与数据来源的声明见 [CREDITS.md](CREDITS.md)。

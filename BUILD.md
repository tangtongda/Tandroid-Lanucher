# 编译、打包与安装（速查）

## 在 Android Studio 里跑模拟器（日常）

1. 顶部设备下拉框选 Google TV 模拟器
2. **⌥⌘Y**（File → Reload All from Disk）—— 代码是在 Studio 外面改的时候一定先做这步
3. 点绿色 **▶ Run**（或 **⌃R**）：自动编译、安装、启动
4. 模拟器里按 Home 键回到桌面（第一次会让你选默认桌面，选 Tandroid Launcher）

> ⚠️ 外部（比如 Claude）改代码时，先**关掉 Studio 里对应文件的标签页**（问是否保存选 Don't Save）。
> 否则 Studio 自动保存时会把编辑器里的旧内容写回磁盘，改动就丢了。
> 确认改动还在：`grep -n "关键字" <文件路径>`

## 打包成 APK（发给别人 / 装到电视）

**方式一：命令行**

```bash
cd ~/Documents/projects/Tandroid-Lanucher
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ~/Downloads/Tandroid-Launcher-<版本号>.apk
```

> 一定用 **assembleRelease**：正式版里 `debug.demoApps`、`debug.tvBrand` 等测试开关永远关闭，
> 不管 local.properties 怎么写。assembleDebug 打出来的是调试版，测试开关会生效。
> 正式版用本机的调试签名，能直接覆盖安装之前装过的版本。

**方式二：Android Studio 菜单**

Build → Select Build Variant，把 app 切到 **release**，再 Build → Generate App Bundles or APKs → Build APK(s)，
完成后右下角弹窗点 **locate**（`app/build/outputs/apk/release/app-release.apk`）。用完记得切回 debug。

版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`，每次发新版加一。

## 装到电视

```bash
adb connect <电视IP>:5555
adb install -r ~/Downloads/Tandroid-Launcher-<版本号>.apk   # -r 覆盖安装，保留排序、城市等设置
```

## 提交代码

```bash
git add -A && git commit -m "说明" && git push
```

`*.apk` 已在 .gitignore 里，内置的 `assets/store/bbll.apk` 不会被推到 GitHub。

## local.properties 开关（不进 git）

| 配置 | 作用 |
|---|---|
| `tmdb.token=…` | TMDB 读取令牌，影视背景图需要 |
| `weather.city=杭州` | 固定天气城市（也可以在电视上 天气 → 切换城市） |
| `debug.demoApps=true` | 调试版加一批假应用：测背景图来源、系统应用文件夹 |
| `debug.tvBrand=sony` | 调试版假装成某个电视品牌，测文件夹 Logo（sony / hisense / tcl / xiaomi / samsung / lg / philips / sharp / skyworth / google） |

改了 `local.properties` 要重新 ▶ Run 才生效；`debug.*` 只影响调试版，打包给别人的版本不受影响。

## 排查问题

```bash
adb logcat -s Weather Backdrops                              # 天气、背景图为什么没出来
adb shell dumpsys gfxinfo com.tangtongda.tlauncher reset     # 清零帧统计，然后操作 10 秒
adb shell dumpsys gfxinfo com.tangtongda.tlauncher | grep -E "Total frames|Janky|percentile"   # 卡不卡
```


# 致谢与第三方资源声明

Tandroid Launcher 是个人、非商业项目。以下资源归各自所有者所有，按其许可使用。

## 图标

- HDMI 输入弹窗中的 PlayStation、Xbox、macOS、Windows 图标来自 [Icons8](https://icons8.com)（免费许可，需署名）：
  - PlayStation icon by [Icons8](https://icons8.com)
  - Xbox icon by [Icons8](https://icons8.com)
  - macOS icon by [Icons8](https://icons8.com)
  - Windows 11 icon by [Icons8](https://icons8.com)

  文件：`app/src/main/res/drawable/ic_input_*.png`（已统一处理为白色）。

- 系统快捷方式与天气定位的图标（应用管理、网络、原桌面、定位）来自 [Icons8](https://icons8.com)（免费许可，需署名）。
  文件：`app/src/main/res/drawable/ic8_*.png`。

- 「系统应用」文件夹使用的电视品牌标志（Sony、Hisense、TCL、Xiaomi、Samsung、LG、Philips、Sharp、Skyworth、Google）
  为各公司的注册商标，仅用于在对应品牌的电视上标识其预装应用，不代表与这些公司有任何关联。
  文件：`app/src/main/res/drawable/banner_brand_*.png`。

- 其余图标（设置、HDMI 接口、游戏手柄、媒体库等）为本项目自绘。

## 音效

- 界面音效（`app/src/main/res/raw/ui_*.wav`）为本项目原创合成，未使用任何采样素材，
  由 `tools/make_sounds.py` 生成，可自由修改。

## 数据与内容

- 影视背景图与元数据：[TMDB](https://www.themoviedb.org)。This product uses the TMDB API but is not endorsed or certified by TMDB.
- 天气：[Open-Meteo](https://open-meteo.com)（CC BY 4.0，"Weather data by Open-Meteo.com"）、[中国气象局](https://weather.cma.cn)。
- 音乐背景：Apple Music 公开排行榜（[Apple Marketing Tools RSS](https://rss.applemarketingtools.com)）。
- B 站热门 / 影视榜：[哔哩哔哩](https://www.bilibili.com) 公开接口。

## 内置 / 推荐应用

- BBLL（第三方哔哩哔哩 TV 客户端，[xiaye13579/BBLL](https://github.com/xiaye13579/BBLL)）：安装包不随仓库分发（`*.apk` 已忽略）。
- Kodi：从 [Kodi 官方镜像](https://kodi.tv/download) 下载安装。

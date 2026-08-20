# 予愿安洁莉娜桌宠（安卓版）

![予愿安洁莉娜](../../README.md) 的安卓移植版。

> 一只住在你安卓设备上的安洁莉娜：走来走去、坐下休息、回应你的点击、和你聊天。基于 [AstrariaX/Angelina-pet](https://github.com/AstrariaX/Angelina-pet) 深度重做，特此向原作者致谢。本项目为粉丝同人作品，素材版权归《明日方舟》/ 鹰角网络所有。

## 目录

- [这是什么？](#这是什么)
- [一、环境准备](#一环境准备)
- [二、构建安装（手把手）](#二构建安装手把手)
- [三、怎么和她玩](#三怎么和她玩)
- [四、常见问题（FAQ）](#四常见问题faq)
- [五、给开发者](#五给开发者)
- [致谢与版权](#致谢与版权)

---

## 这是什么？

Windows 版予愿安洁莉娜桌宠的**安卓原生移植**（Kotlin + Jetpack Compose）。同样的安洁莉娜、同样的骨骼动画（v3.0 基线：官方 spine-ts 3.8 裁剪管线），在安卓手机上运行。包含完整功能：桌宠悬浮窗、聊天终端（对话树导航）、语音、战斗动画等。

## 一、环境准备

1. 下载安装 **Android Studio**（<https://developer.android.com/studio>）
2. 首次启动时按向导安装 Android SDK（保持默认即可）
3. 准备一台安卓手机（开 USB 调试），或用模拟器（推荐 MuMu 模拟器，Windows 下运行流畅）

## 二、构建安装（手把手）

1. 点击本仓库绿色 **`<> Code`** 按钮 → **Download ZIP**，解压
2. 打开 Android Studio → **Open**，选择解压出来的文件夹
3. 等待 Gradle 同步完成（首次会下载依赖，需要几分钟）
4. 用数据线连接手机（手机上允许 USB 调试），或启动模拟器
5. 点击顶部工具栏的绿色 **Run ▶** 按钮
6. 安装完成，她来了！

## 三、怎么和她玩

- **点击角色**：互动动作 + 语音
- **聊天终端**：对话树导航（浮窗跳转、历史轮次、分支切换）
- **菜单**：形态切换、战斗动画、设置等

## 四、常见问题（FAQ）

### Gradle 同步失败 / 下载慢

网络问题居多：给 Gradle 配置国内镜像（阿里云 maven 仓库），或开启代理后重试 File → Sync Project。

### 手机无法安装

确认手机开启开发者选项和 USB 调试；部分手机需在开发者选项里打开"USB 安装"。

### 桌宠窗口不显示

安卓悬浮窗需要"显示在其他应用上层"权限——设置里给应用开启悬浮窗权限。

## 五、给开发者

### 三端开源

| 平台 | 仓库 |
|---|---|
| Windows | [Arknights-Angelina-Pet-YuYuan](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan) |
| 鸿蒙 | [Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT) |
| 安卓（本仓库） | [JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android](https://github.com/JNGKZbird/JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android) |

> 未来**可能**推出 iOS 版本，敬请期待。

### 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **渲染**：自研 spine38 运行时 + GLES3 掩码纹理管线（60fps 动画）
- **架构**：桌宠悬浮窗 + 聊天终端（导航轨对话树）

### 命令行构建

```bash
cd D:\Angelina-pet-芋圆-安卓版
./gradlew assembleDebug
```

### 目录结构

```
app/src/main/java/com/jngkzbird/arknights_angelina_pet/
├── spine38/          # 自研 Spine 3.8 运行时（加载/骨骼/裁剪/渲染）
├── gl/               # GLES3 渲染
├── ui/               # Compose 界面（桌宠浮窗/聊天终端）
└── model/            # 数据层（设置/聊天存储）
app/src/main/assets/  # 素材（spine/语音/Skill）
```

---

## 致谢与版权

- **原作者**：基于 [AstrariaX/Angelina-pet](https://github.com/AstrariaX/Angelina-pet) 深度重做，感谢原作者。
- **素材版权**：角色素材版权归 **Hypergryph / 鹰角网络** 所有。
- **许可证**：MIT — 为爱发电，随便用，出事了别找我。

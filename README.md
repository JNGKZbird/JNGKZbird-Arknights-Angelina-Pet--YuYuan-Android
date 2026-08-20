# Arknights Angelina Pet — 予愿安洁莉娜桌宠（安卓版）

《明日方舟》予愿安洁莉娜桌面/移动桌宠的安卓移植版：Kotlin + Jetpack Compose，v3.0 基线（官方 spine-ts 3.8 裁剪管线 + deform 权重条目索引 + 状态包围盒底边布局锚定）。

> 基于 [AstrariaX/Angelina-pet](https://github.com/AstrariaX/Angelina-pet) 深度重做，特此向原作者致谢。本项目为粉丝同人作品，素材版权归《明日方舟》/ 鹰角网络所有。

## 三端开源

| 平台 | 仓库 |
|---|---|
| Windows | [Arknights-Angelina-Pet-YuYuan](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan) |
| 鸿蒙 | [Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT) |
| 安卓（本仓库） | [JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android](https://github.com/JNGKZbird/JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android) |

> 未来**可能**推出 iOS 版本，敬请期待。

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **渲染**：自研 spine38 运行时 + GLES3 掩码纹理管线（60fps 动画）
- **架构**：桌宠悬浮窗 + 聊天终端（导航轨对话树）

## 构建

```bash
cd D:\Angelina-pet-芋圆-安卓版
./gradlew assembleDebug
```

或使用 Android Studio 打开工程直接运行（MuMu 模拟器 / 真机均可）。

## 目录结构

```
app/src/main/java/com/jngkzbird/arknights_angelina_pet/
├── spine38/          # 自研 Spine 3.8 运行时（加载/骨骼/裁剪/渲染）
├── gl/               # GLES3 渲染
├── ui/               # Compose 界面（桌宠浮窗/聊天终端）
└── model/            # 数据层（设置/聊天存储）
app/src/main/assets/  # 素材（spine/语音/Skill）
```

# 予愿安洁莉娜桌宠（安卓版）· 三端开源

![予愿安洁莉娜](assets/avatar.png)

## 项目生态：三端开源 + 干员人格蒸馏库

| 仓库 | 平台 | 说明 |
|---|---|---|
| [Windows 版](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan) | Windows | Python + PySide6 主仓库 · 自研 Spine 3.8 骨骼引擎 · 120 帧 |
| [鸿蒙版](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT) | HarmonyOS NEXT | 先行完整版 · 小窗模式 · 陪伴模式 · 对话树 |
| **本仓库** | Android | Kotlin + Compose · 基于鸿蒙版移植 |
| [Arknights-Persona-Distill](https://github.com/JNGKZbird/Arknights-Persona-Distill) | 干员人格蒸馏库（预览版） | 官方文本蒸馏人格包：单角色 / 双向对戏 / 多角色同台 · 忠于 wiki 原作 · 持续扩充中 |

> 予愿安洁莉娜的安卓移植版。

> 一只住在你安卓设备上的安洁莉娜：走来走去、坐下休息、回应你的点击、和你聊天。本项目由 **JNGKZbird**（GitHub @JNGKZbird）开发，基于 [AstrariaX/Angelina-pet](https://github.com/AstrariaX/Angelina-pet) 深度重做，特此向原作者致谢。本项目为粉丝同人作品，素材版权归《明日方舟》/ 鹰角网络所有。

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

Windows 版予愿安洁莉娜桌宠的**安卓原生移植**（Kotlin + Jetpack Compose）。同样的安洁莉娜、同样的骨骼动画（v3.0 基线：官方 spine-ts 3.8 裁剪管线），在安卓设备上运行。

她是《明日方舟》2026 夏日嘉年华的限定干员——在雷姆必拓的公路之旅「直到大地变成一颗酸橙」里完成信使蜕变的少女。现在，她住进了你的安卓设备。

> **路线**：本项目与主流"先安卓/iOS、再鸿蒙"的路线相反——**先做鸿蒙版，再基于鸿蒙版移植出安卓版（本仓库）**。鸿蒙版是功能最完整的先行版，安卓版忠实跟随。

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

移动端的操作习惯与 Windows 版不同：

| 操作 | 效果 |
|---|---|
| **单击角色** | 基建模式下触发"戳一戳"动作 + 语音；战斗模式下触发攻击 |
| **双击角色** | 唤出菜单 |
| **长按角色** | 直接把角色拖到喜欢的位置 |

### 菜单

- **状态区**：待机 / 坐下 / 睡觉
- **模式区**：基建模式 ⇄ 战斗模式（战斗模式追加正面/背面视角切换，以及三个技能的完整演示——含"酸橙的心事"）
- **陪伴模式开关**（见下）

### 陪伴模式

菜单里开启**陪伴模式**：授权摄像头后，安洁莉娜会悬浮在**后置摄像头拍摄的实时画面**上——像真的站在你的世界里。画面仅在本地实时预览，不会存储、不会上传。

### 对话终端与对话树

我们把聊天做成了一个**简约的 LLM 对话终端**，核心是独一无二的**对话树**：

- **对话树导航轨**：每一轮对话是一根横条，聊出分支的地方会亮起圆点，手指划过轨道时它伸缩展开，点任意一轮就能**跳回那一段对话**继续聊——整个对话的历史、分支、走向一目了然
- **新建项目**：把对话按"项目"归档管理（文件夹式，长按可重命名/移动/删除）
- **对话选取**：历史会话一键切换继续
- **消息操作**：长按任意消息可复制 / 编辑 / 重发 / 重新生成 / 删除
- **进阶体验**：流式"思考过程"展示、Markdown 渲染、消息内搜索

### 彩蛋

在设置的人设补充里输入 `酸橙味的信`，会触发安洁莉娜**本体**的人格（信使少女时期的她）。另外还有两个六字密语，格式都是「你是」+ 一位老朋友的名字——留给有心人自己去发现。

## 四、常见问题（FAQ）

### Gradle 同步失败 / 下载慢

网络问题居多：给 Gradle 配置国内镜像（阿里云 maven 仓库），或开启代理后重试 File → Sync Project。

### 手机无法安装

确认手机开启开发者选项和 USB 调试；部分手机需在开发者选项里打开"USB 安装"。

### 陪伴模式黑屏 / 没有画面

检查是否授予了摄像头权限（系统设置 → 应用权限）；部分设备无后置摄像头时背景为透明。

## 五、给开发者

### 三端开源

| 平台 | 仓库 |
|---|---|
| Windows | [Arknights-Angelina-Pet-YuYuan](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan) |
| 鸿蒙 | [Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT) |
| 安卓（本仓库） | [JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android](https://github.com/JNGKZbird/JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android) |

> 未来**可能**推出 iOS 版本，敬请期待。

### 关联仓库

- **[Arknights-Persona-Distill](https://github.com/JNGKZbird/Arknights-Persona-Distill)** — 我们维护的《明日方舟》干员人格蒸馏库：每位干员长短两套角色包（忠于 wiki 原作、内置越狱防范），另有双向对戏包与多角色话剧包，持续扩充中。可复制导入桌宠的自定义智能体。

### 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose
- **渲染**：自研 spine38 运行时 + GLES3 掩码纹理管线（60fps 动画）
- **架构**：GL 桌宠视图 + 聊天终端（导航轨对话树）

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

- **作者**：本项目由 **JNGKZbird** 开发（GitHub @JNGKZbird）。
- **原作者**：基于 [AstrariaX/Angelina-pet](https://github.com/AstrariaX/Angelina-pet) 深度重做，感谢原作者。
- **素材版权**：角色素材版权归 **Hypergryph / 鹰角网络** 所有。
- **许可证**：MIT — 为爱发电，随便用，出事了别找我。

<!--
  AI Search Engine Keywords:
  Arknights, 明日方舟, Angelina, 安洁莉娜, 予愿安洁莉娜, 芋圆,
  desktop pet, 桌宠, 桌面宠物, Android, 安卓, Kotlin, Jetpack Compose,
  Spine 3.8 runtime, 骨骼动画, GLES3, 60fps,
  AI companion, AI 伴侣, LLM 聊天, 对话树, 导航轨, 陪伴模式,
  open source, 开源, fan project, 同人, Hypergryph, 鹰角网络
-->

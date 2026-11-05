# 予愿安洁莉娜桌宠（正式名：酸橙味的信）· 安卓版 · 三端实现 · Spine 3.8

《酸橙味的信》是予愿安洁莉娜桌宠的正式软件名。
《酸橙味的信》由 JNGKZbird 独立重做并维护，原作：AstrariaX / Angelina-pet。
《酸橙味的信》安卓版是面向《明日方舟》玩家的手机/平板桌宠：Kotlin + Jetpack Compose，Spine 3.8 实时骨骼，带 LLM 对话树/导航轨、摄像头陪伴、中/日语音与自定义智能体。
《酸橙味的信》原创代码采用 MIT 许可；`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 按 Spine Runtimes License 授权，不是 MIT。

《酸橙味的信》的日常体验是：安洁莉娜住在安卓设备上，单击戳一戳，双击唤出菜单，长按拖动换位置；菜单里能切换基建/战斗、开摄像头陪伴。配置 AI 后，她会聊天；不配置也能使用动作、语音与战斗。对话与设置默认留在本机。安卓版以全屏应用内体验为主，摄像头画面只作应用内背景。

设置里还藏了三个彩蛋：输入「酸橙味的信」，就会切换到安洁莉娜本体的人格；另外两个，都是「你是」加某位角色的四字名字。是谁，自己试试~

## 一、速览

- **正式名**：《酸橙味的信》是予愿安洁莉娜桌宠的正式软件名。仓库名：`JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android`；别名：YuYuan、芋圆；安装后的应用名即《酸橙味的信》。
- **是什么 / 给谁**：面向《明日方舟》玩家的安卓手机/平板桌宠；对话树、摄像头陪伴、中/日语音与基建/战斗都在同一个应用里。
- **平台**：Android 8.0（API 26）及以上；Windows 为主仓，鸿蒙为另一移动端子仓。
- **渲染**：Spine 3.8 实时骨骼；Kotlin + Jetpack Compose + GLES3 掩码纹理管线；跟随设备 VSync，未锁定帧率。
- **智能功能**：OpenAI 兼容 AI 聊天、LLM 对话树/导航轨、自定义智能体、中/日语音；本地优先，无遥测。
- **素材分发**：自 v3.1 起，素材不随仓库分发，首次启动时由程序下载。首次启动需要联网。
- **许可**：原创代码 MIT；`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 按 Spine Runtimes License（非 MIT）；项目持有 Spine Editor 许可（Spine Essential）。

## 二、三端与关联项目

> “从古老的祖先学会使用工具与火至今，人类唯一的壮举即是‘传承’。”
>
> ——《明日方舟》「孤星」旁白

《酸橙味的信》支持 Windows、鸿蒙 NEXT、安卓三端，鸿蒙与安卓为子仓库。本页是安卓子仓。

- **Windows 主仓**：[Arknights-Angelina-Pet-YuYuan](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan) — Python + PySide6。
  渲染核心衍生自官方 spine-ts 3.8（按 Spine Runtimes License，非 MIT），Python 运行时自研；Windows 端最高 120fps。
- **鸿蒙子仓**：[Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT](https://github.com/JNGKZbird/Arknights-Angelina-Pet-YuYuan-HarmonyOS-NEXT) — ArkTS + C++ GLES3；移动端方案与系统小窗。
- **安卓（本仓库）**：[JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android](https://github.com/JNGKZbird/JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android) — Kotlin + Compose；移动端衍生实现。
- **人格蒸馏库**：[Arknights-Persona-Distill](https://github.com/JNGKZbird/Arknights-Persona-Distill) — 《明日方舟》干员人格包，可导入桌宠的自定义智能体。
- **能天使桌宠 · 捣蛋鬼**：[lemuel-scamp](https://github.com/JNGKZbird/lemuel-scamp) — 另一款桌面宠物，主角是能天使和她的异格「新约能天使」。
- **素材档案库**：[Arknights-Desktop-Pets-Archive](https://github.com/JNGKZbird/Arknights-Desktop-Pets-Archive) — 素材 zip 与下载包。

三端三套独立代码，都实现同一套 Spine 渲染管线语义；安卓版基于鸿蒙版衍生实现，平台差异见第四节。

## 三、她能做什么

安卓版把角色、聊天与摄像头陪伴放在同一个应用里。下表只列已实装的能力。

| 功能 | 安卓说明 |
|---|---|
| Spine 3.8 实时骨骼 | Kotlin + Compose；GLES3 掩码纹理管线；实时骨骼动画，非逐帧序列；跟随设备 VSync，未锁定帧率 |
| AI 聊天 | OpenAI 兼容；可接 DeepSeek、Moonshot、智谱等；地址、密钥、模型、上下文条数可配 |
| LLM 对话终端 | 流式思考过程、Markdown 渲染、消息内搜索 |
| 对话树 + 导航轨 | 每轮一根横条；分支亮圆点；点任意一轮跳回；导航轨在横屏显示 |
| 项目 / 文件夹归档 | 对话按项目归档；项目可重命名/删除，会话可移动到项目 |
| 自定义智能体 / 干员来信 | 不预设角色列表；默认只有安洁莉娜与直接对话的「陌生的友人」；自建智能体以“来信”形式提示回复 |
| 中 / 日语音 | 中文/日文切换；默认关闭，需在设置开启 |
| 戳一戳 | 基建模式点击触发动作；开启语音后播放语音；战斗模式触发攻击 |
| 基建 / 战斗模式 | 普攻连击、技能 1/2/3 完整动画、正/背面视角 |
| 摄像头陪伴 | 后置摄像头实时画面作应用内背景；仅本地预览，不存储、不上传，无拍照/录像 |
| 人格 / 彩蛋 | 设置“额外提示词”可切换人格；输入「酸橙味的信」切本体人格 |
| 素材首启下载 | v3.1 起素材不随 APK 分发；首次启动由程序下载，也可手动导入官方 zip |

### 对话树与导航轨

对话按轮次组织成树：重新生成或编辑重发，会在同一父节点下开兄弟分支，活跃路径高亮。横屏时显示导航轨，每轮一根横条，分支处亮圆点；点任意一轮就能跳回那一段继续聊。竖屏不显示导航轨，用历史对话切换。

### 摄像头陪伴

在菜单里开启陪伴模式，授权相机后，安洁莉娜会站在后置摄像头的实时画面上。画面只作为应用内背景，仅本地预览；不存储、不上传，没有拍照/录像功能。没有可用后置摄像头时不显示实时画面。

### 全屏与横屏

安卓版使用全屏沉浸式布局，隐藏状态栏与导航栏；方向跟随系统旋转锁定，横屏时才显示对话导航轨。

## 四、平台差异

《酸橙味的信》支持 Windows、鸿蒙 NEXT、安卓三端，鸿蒙与安卓为子仓库。

| 功能 | Windows | 鸿蒙 | 安卓 |
|---|---|---|---|
| LLM 对话树 + 导航轨 | — | ✓ | ✓ |
| 小窗模式 | — | ✓（鸿蒙独有） | — |
| 摄像头陪伴模式 | — | ✓ | ✓ |
| 记忆管理 | ✓ | — | — |
| 全屏自动隐藏 | ✓ | — | — |

上表只列差异。安卓版没有小窗模式；摄像头画面只作应用内背景，不是系统悬浮窗。

## 五、许可与素材边界

《酸橙味的信》原创代码采用 MIT 许可，见 LICENSE。`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 衍生自官方 spine-ts 3.8，按 Spine Runtimes License 授权，**不是 MIT**。项目持有 Esoteric Software 的 Spine Editor 许可（Spine Essential）。《明日方舟》游戏素材版权归上海鹰角网络有限公司，不随本仓库分发；本项目为非官方二创。本项目与 Esoteric Software 无官方合作或背书关系。

该许可依据 Spine Editor License Agreement 第 2 节，将 Spine Runtimes 集成进本产品。终端用户直接使用《酸橙味的信》不需要购买 Spine；第三方修改、适配或衍生包含 Spine Runtimes 的代码或产品，需要自行取得许可。

游戏素材不适用 MIT；仓库保留代码、角色清单、Skill 包与许可文件，游戏素材不在仓库内。应用启动图标（`app/src/main/res/drawable-nodpi/avatar.png`、`mipmap-*/ic_launcher*`）是例外，随仓库分发，仅用于标识本应用。

许可原文：[LICENSE](LICENSE) · [NOTICE.md](NOTICE.md) · [LICENSES/README.md](LICENSES/README.md)。分发本仓库时，请保留 LICENSE、NOTICE.md 与 LICENSES/ 中的许可原文。

**English summary (license & provenance)**

- Original code is MIT. `app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` is a derivative of the official spine-ts 3.8 runtime, used under the Spine Runtimes License, not MIT.
- The project holds a Spine Editor license (Spine Essential) from Esoteric Software.
- Game assets are © Shanghai Hypergryph Network Technology Co., Ltd.; they are not distributed with this repository, except the launcher icon used to identify the app.
- There is no official cooperation or endorsement from Esoteric Software.

## 六、快速开始

安卓版需要 Android 8.0（API 26）及以上的手机或平板。可以下载 Release 的 APK，也可以用 Android Studio 从源码构建。

> 自 v3.1 起，素材不随仓库分发，首次启动时由程序下载。

首次启动需要联网；如果自动下载失败，可从[素材档案库](https://github.com/JNGKZbird/Arknights-Desktop-Pets-Archive)下载官方 zip，在安装页手动导入。程序会先校验整包 SHA-256，再逐文件校验大小与 SHA-256。

### 方式一：安装 Release APK

1. 打开 [Release v3.1](https://github.com/JNGKZbird/JNGKZbird-Arknights-Angelina-Pet--YuYuan-Android/releases/tag/v3.1)，下载 `AngelinaPet-v3.1-Android-release.apk`（约 9 MB，不含素材）。
2. 在系统设置里允许“安装未知应用”，然后安装 APK。
3. 首次启动按提示下载素材；完成后进入桌宠界面。

Release 页还提供 `AngelinaPet-v3.1-Android-debug.apk`（约 13 MB），用于测试或模拟器。debug 与 release 的签名不同，不能互相覆盖安装；从 debug 切到 release（或反向）需要先卸载，应用数据会丢失。

### 方式二：从源码构建

1. 安装 [Android Studio](https://developer.android.com/studio)，按向导安装 Android SDK（API 35）与 JDK 17。
2. 在仓库页点 **Code → Download ZIP**，解压；Android Studio → **File → Open** 选择解压目录。
3. 等待 Gradle 同步完成；首次会下载依赖。
4. 连接开启 USB 调试的手机，或启动模拟器。
5. 点击顶部 **Run ▶**；首次启动按提示下载素材。

### 命令行构建

```bash
./gradlew assembleDebug
```

Windows 命令提示符用 `gradlew.bat assembleDebug`；产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### 构建失败时

- Gradle 同步慢或失败：多为网络问题；给 Gradle 配置国内镜像，或开启代理后重试。
- 手机不识别：确认开发者选项与 USB 调试已开；部分手机还需要打开“USB 安装”。
- 安装提示签名冲突：先卸载旧的应用，再装另一个签名的 APK；应用数据会丢失。
- 陪伴模式黑屏：检查相机权限；设备没有可用后置摄像头时不显示实时画面。
- 素材下载失败：检查网络，或从素材档案库手动导入官方 zip。

## 七、操作

安卓版的操作集中在单击、双击、长按与菜单。

| 操作 | 效果 |
|---|---|
| 单击角色 | 基建模式触发戳一戳动作与语音；战斗模式触发攻击 |
| 双击角色 | 唤出菜单 |
| 长按角色 | 拖动换位置 |
| 菜单 | 待机/坐下/睡觉；基建 ⇄ 战斗；战斗时正面/背面视角；陪伴模式 |
| 对话 | 进入聊天终端；横屏显示导航轨 |

终端里，长按消息可复制、编辑、编辑并重发、重新生成或删除；会话可移动到项目。

## 八、AI 配置

《酸橙味的信》的 AI 聊天走 OpenAI 兼容接口；不配置也能使用动作、语音与战斗。

1. 在服务商注册账号并创建 API Key。
2. 打开聊天终端 → 设置，填写 API Key、接口地址与模型名。
3. 还可以设置玩家称呼、上下文条数（1–100）、时间感知、语音、语音语言与额外提示词。
4. 点“测试连接”确认接口可用，保存后开始对话。

默认接口地址是 `https://api.deepseek.com`，默认模型是 `deepseek-v4-flash`；以你账号实际可用的模型为准。Moonshot、智谱等 OpenAI 兼容服务同样可用，把地址、密钥与模型换成对应的即可。

API 密钥只保存在你本地的设置里，不会被上传。

> 《酸橙味的信》本地优先：除用户主动配置的 AI 接口请求与素材下载外，聊天记录、设置与运行数据都保存在本机；没有遥测，没有上报端点。摄像头画面仅本地预览，不存储、不上传，无拍照/录像。

未填 API Key 时，聊天走本地演示，不会发出网络请求。聊天没有反应时，依次检查：接口地址是否填写正确，API 密钥是否完整，账户是否还有余额，模型名是否可用。

## 九、常见问题

### 《酸橙味的信》是什么？

《酸橙味的信》是予愿安洁莉娜桌宠的正式软件名。安装后的应用名就是《酸橙味的信》；设置“额外提示词”输入“酸橙味的信”会切换到安洁莉娜本体的人格。

### 谁开发的？和 AstrariaX / Angelina-pet 是什么关系？

《酸橙味的信》由 JNGKZbird 独立重做并维护，原作：AstrariaX / Angelina-pet。原作的创意与工作为《酸橙味的信》提供了起点。

### 支持哪些平台？

《酸橙味的信》支持 Windows、鸿蒙 NEXT、安卓三端，鸿蒙与安卓为子仓库。本页是安卓子仓；Windows 与鸿蒙分别在各自仓库维护。

### 下载后为什么没有角色素材？

自 v3.1 起，素材不随仓库分发，首次启动时由程序下载。首次启动需要联网；如果自动下载失败，可从素材档案库下载官方 zip，在安装页手动导入。程序会先校验整包 SHA-256，再逐文件校验大小与 SHA-256。

### 安卓版有小窗、碎碎念或记忆管理吗？

安卓版没有小窗模式。碎碎念只有设置项（主动性发言频率低频/中频/高频），触发逻辑尚未实装。记忆管理与全屏自动隐藏仅 Windows 版提供；导航轨只在横屏显示。

### 需要购买 Spine 吗？

直接使用《酸橙味的信》不需要购买 Spine。第三方修改、适配或衍生包含 Spine Runtimes 的代码或产品，需要自行取得许可。`spine38/` 按 Spine Runtimes License 授权，不是 MIT。

### 素材可以商用吗？

不可以。《明日方舟》游戏素材版权归上海鹰角网络有限公司，仅限个人非商业使用，不适用 MIT，也不随仓库分发。

### 聊天记录会上传吗？摄像头画面会被保存吗？

不会。《酸橙味的信》本地优先：除用户主动配置的 AI 接口请求与素材下载外，聊天记录、设置与运行数据都保存在本机；没有遥测，没有上报端点。摄像头画面仅本地预览。

### 有现成 APK 吗？需要什么环境？

有。Release v3.1 提供 release APK（约 9 MB，不含素材）与 debug APK（约 13 MB，测试用）；安装需要 Android 8.0 及以上。也可以从源码构建，需要 Android Studio、Android SDK 35 与 JDK 17。

### 可以换人格或角色包吗？

可以。安卓版用自定义智能体加载人格提示词；设置里的“额外提示词”可切换人格。

一共有三个彩蛋：输入「酸橙味的信」切换到安洁莉娜本体人格；输入「你是」加某位角色的四字名字，切换到对应人格，这类六字彩蛋有两个。关键词必须完整输入，多一个字都不会触发。具体是谁，留给你自己试~

更多干员人格可从[人格蒸馏库](https://github.com/JNGKZbird/Arknights-Persona-Distill)获取。

### 帧率是多少？

渲染循环跟随屏幕 VSync，未锁定帧率；实际帧率随设备刷新率、性能与场景变化。

### 支持平板或横屏吗？

支持手机与平板。界面跟随系统的旋转锁定；横屏时显示对话导航轨，竖屏时用历史对话切换。

## 十、技术亮点（给开发者）

安卓版使用 Kotlin + Jetpack Compose；渲染核心与 Windows 版同源，衍生自官方 spine-ts 3.8。

- Kotlin 2.1.0 + Jetpack Compose（BOM 2024.12.01）；Gradle 8.11.1、AGP 8.7.3；compileSdk/targetSdk 35，minSdk 26。
- `spine38/` 是 Spine 3.8 运行时的衍生/集成实现，按 Spine Runtimes License 授权，不是 MIT；GLES3 掩码纹理管线。
- 渲染路径：CPU 三角形 → VBO → 奇偶规则掩膜 → 着色器丢弃 → 预乘 alpha 混合；S-H 裁剪语义衍生自官方 spine-ts 3.8。
- 渲染循环由 `Choreographer` 跟随屏幕 VSync；未锁定帧率。
- 对话树用 `RoundNode` + 活跃路径；重新生成或编辑重发会在同一父节点下开兄弟分支。
- 导航轨在横屏渲染，一轮一根横条，按高斯衰减伸缩，分支圆点/数字，点击跳转。
- 摄像头用 CameraX `Preview`，绑定后置摄像头，画面只送显示表面；应用不申请悬浮窗权限。
- 素材安装时校验整包与逐文件 SHA-256；后续启动只检查文件存在与大小。

### 目录结构

```text
app/src/main/java/com/jngkzbird/arknights_angelina_pet/
├── spine38/          # Spine 3.8 运行时（衍生自 spine-ts 3.8；加载/骨骼/裁剪/渲染）
├── gl/               # GLES3 渲染
├── ui/               # Compose 界面（桌宠视图/聊天终端）
└── model/            # 数据层（设置/聊天存储/素材）
app/src/main/assets/  # Skill 文本与素材清单；Spine/语音首次启动下载
```

## 十一、已知限制与适用边界

功能以本页表格为准，平台边界如下。

- 需要 Android 8.0（API 26）及以上的手机或平板。
- Release APK 安装需要允许“安装未知应用”；debug 与 release 签名不同，切换需要卸载，应用数据会丢失。
- 源码构建需要 Android Studio、Android SDK 35 与 JDK 17；首次 Gradle 同步需要联网下载依赖。
- 首次启动需要联网下载素材；也可以手动导入官方 zip。
- 安卓版没有小窗模式；摄像头画面只作应用内背景，不是系统悬浮窗。
- 记忆管理与全屏自动隐藏仅 Windows 版提供。
- 设置里的“主动性发言频率”已预留（低频/中频/高频），触发逻辑尚未实装。
- 导航轨只在横屏显示。
- 摄像头陪伴需要相机权限；画面仅本地预览。
- 跟随设备 VSync，未锁定帧率；实际帧率随设备刷新率与性能变化。
- 游戏素材仅限个人非商业使用，不适用 MIT，也不随仓库分发。

### 路线图（远期，未实装）

- 碎碎念触发逻辑：把已预留的“主动性发言频率”接到空闲发言。
- 记忆库 / 知识库：与人格蒸馏、知识库协同项目联动，引入记忆行为。

## 十二、致谢

《酸橙味的信》建立在原作与这些开源项目之上。

- **原作**：[AstrariaX / Angelina-pet](https://github.com/AstrariaX/Angelina-pet)。感谢原作者的创意与工作。
- **Spine**：Esoteric Software LLC 的 Spine Runtimes 与 Spine Editor；`spine38/` 衍生自官方 spine-ts 3.8。
- **素材**：《明日方舟》角色立绘、动画、语音等素材版权归上海鹰角网络有限公司所有。
- **素材档案库**：[Arknights-Desktop-Pets-Archive](https://github.com/JNGKZbird/Arknights-Desktop-Pets-Archive)。
- **构建工具**：Android Studio、Jetpack Compose、Gradle。

> 为爱发电，随便用，出事了别找我。

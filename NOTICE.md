# 素材版权、运行时许可与分发声明

## 1. 项目自身代码（MIT）

本仓库中由本项目原创、且位于 `app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 目录之外的代码，采用 MIT 许可证。版权行见 [LICENSE](LICENSE)（Copyright (c) 2026 AstrariaX & Contributors）。本项目由 JNGKZbird 维护；JNGKZbird 的原创贡献按同一 MIT 许可证发布（`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 除外）。

## 2. Spine Runtimes（`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/`，非 MIT）

`app/src/main/java/com/jngkzbird/arknights_angelina_pet/spine38/` 是 Esoteric Software 的 Spine Runtimes（spine-ts 3.8）在本项目中的衍生/集成实现。该目录整体按 **[Spine Runtimes License Agreement](https://esotericsoftware.com/spine-runtimes-license)** 使用，**不是 MIT**。

本项目持有有效的 **Spine Editor 许可（Spine Essential）**，并按 **[Spine Editor License Agreement 第 2 节](https://esotericsoftware.com/spine-editor-license)**将 Spine Runtimes 集成进本产品。

随仓库提供的官方许可原文：

- [LICENSES/Spine-Runtimes-License-3.8.txt](LICENSES/Spine-Runtimes-License-3.8.txt) — spine-ts 3.8 随附的 2019 版许可
- [LICENSES/Spine-Runtimes-License-2025.txt](LICENSES/Spine-Runtimes-License-2025.txt) — 2025-04-05 版（Spine Editor License Agreement 的 Exhibit A）

### 终端用户与第三方

- **终端用户**：因为本项目按 Spine Editor License Agreement 第 2 节集成，直接使用本软件（不修改代码）不需要购买 Spine。
- **第三方原样分发**：仅原样分发、不做任何修改时，不需要自己的 Spine Editor 许可；但分发材料中必须保留 2025 版许可原文（Exhibit A）。
- **第三方修改 / 适配 / 衍生**：修改、适配或衍生包含 Spine Runtimes 的代码或产品，需要自己的 Spine Editor 许可。
- **关系边界**：本项目与 Esoteric Software 没有官方合作或背书关系；本项目持有的是标准 Spine Editor 许可，不代表 Esoteric Software 对本项目背书。

## 3. 游戏素材（非 MIT，不随仓库分发）

游戏素材（Spine 模型/骨骼/贴图、语音、角色图标、头像等）版权归上海鹰角网络有限公司所有，**不适用 MIT，也不随本仓库分发**；Spine 许可同样不覆盖这些素材。

首次启动时会从素材档案库下载：

<https://github.com/JNGKZbird/Arknights-Desktop-Pets-Archive>

也可以手动下载 Release 中的官方 zip，在软件中选择“手动导入官方 zip”。

素材仅供个人非商业使用；不得用于商业用途，不得损害版权方利益。如权利方提出要求，仓库所有者应停止分发并删除相关内容。

## 4. 三端实现

- Python + PySide6（Windows，主仓库）
- ArkTS + C++ GLES3（HarmonyOS，本项目衍生实现，先行版）
- Kotlin + Compose（Android，本仓库，基于鸿蒙版衍生实现）

予愿安洁莉娜是三套独立代码，分别用 Python、ArkTS、Kotlin 实现；鸿蒙版与安卓版是本项目在官方 spine-ts 3.8 运行时基础上的衍生实现。

关联项目：独立的《捣蛋鬼》（能天使）Tauri v2 版使用同源 Spine 3.8 技术栈，不属于本作三端。

# RunBeat 项目全景说明文档

> 生成日期：2026-08-06 ｜ 基于当前代码库（`git 7cb3d24` + 工作区未提交改动）全量梳理

---

## 1. 项目概览

| 项目 | 内容 |
| --- | --- |
| 应用名称 | RunBeat 跑步节拍器（面向减肥人群） |
| 项目源码 | https://github.com/Sunrisies/runbeat.git |
| 包名 / ApplicationId | `com.android.runbeat` |
| 当前版本 | `1.1.8`（versionCode `10`） |
| 平台 | Android（单端原生，Kotlin） |
| 项目形态 | 单模块 Android 应用（`settings.gradle.kts` 仅含 `:app`） |
| 版本管理 | 根目录 `version.properties` 统一维护（`VERSION_CODE` / `VERSION_NAME`） |
| Git 状态 | 共 4 次提交，最近提交 2026-07-25；工作区存在「声音启动优化」未提交改动 |

---

## 2. 核心业务定位

### 2.1 核心问题
减肥人群（尤其入门与快走慢跑交替训练者）难以长期保持稳定步频（Cadence，即每分钟步数）与合理的燃脂强度。步频不稳会直接导致配速波动、体力消耗不均、燃脂效率下降与受伤风险上升。

### 2.2 解决方案
RunBeat 是一款**面向减肥人群的高精度跑步节拍器**：通过听觉（节拍音色 + 小节重音）与视觉（脉冲动画 + 拍位指示）双重反馈，帮助用户将步频锁定在目标 BPM，并通过「快走/慢跑交替」循环训练维持高效燃脂强度。与一般节拍器不同，它针对减肥/跑步场景做了专门设计：

- **步频区间贴合跑步**：BPM 限定 120–200（典型跑者步频区间），内置 160（入门慢跑）/ 180（标准配速）/ 190（间歇冲刺）三档预设；
- **跑步中持续可用**：前台服务 + WakeLock 保证锁屏/切后台仍持续节拍，通知栏可直接控制；
- **嘈杂环境可辨识**：每小节第 1 拍为重音（更高基频 + 更高增益）。

### 2.3 服务对象
跑步爱好者（入门/进阶）、配速训练者，以及任何需要节拍引导运动节奏的用户。当前为完全本地单机应用，无账号体系、无数据上报。

---

## 3. 功能模块与协作逻辑

### 3.1 模块总览

```
┌─────────────────────────── UI 层（Compose） ───────────────────────────┐
│  MainActivity ── RunBeatTheme ── UpdateHost                              │
│      ├─ MetronomeScreen（主界面：BPM/预设/音色/音量/控制按钮）           │
│      ├─ BeatVisualizer / BeatIndicator（节拍动画）                       │
│      ├─ UpdateDialog（更新弹窗：新版本/进度/失败/权限/安装中）           │
│      └─ MetronomeViewModel / UpdateViewModel（状态聚合与动作转发）       │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ 绑定（BIND_AUTO_CREATE） / Flow 状态回流
┌────────────────────── 服务层（Android Service） ────────────────────────┐
│  MetronomeService（前台服务，节拍引擎唯一持有者）                        │
│      ├─ 节拍调度 + 音频播放 + 通知控制 + WakeLock + 音频焦点管理          │
│  DownloadService（前台服务，更新下载通知托管）                           │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
┌────────────────────── 核心逻辑层（纯 Kotlin，可 JVM 单测） ─────────────┐
│  metronome.core   MetronomeEngine / TickTiming / Settings / Constants   │
│  metronome.audio  TickSoundSynth（PCM 合成）/ MetronomeAudioPlayer       │
│  update           UpdateManager / UpdateChecker / UpdatePolicy / ...    │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.2 节拍器链路（核心业务）

1. **UI 操作** → `MetronomeViewModel` 通过 `ContextCompat.startForegroundService` 启动服务，并经 Binder 调用 `MetronomeService` 的方法（start/pause/resume/stop/changeBpm/changeSound/changeVolume）；
2. **引擎调度** → `MetronomeEngine` 独立调度线程基于**单调时钟锚点 + 固定间隔**计算绝对节拍时间（`nextTickNanos = anchor + n * interval`），无累积误差；剩余时间 >8ms 用 `Thread.sleep`，否则自旋，保证触发抖动 <50ms；
3. **音频输出** → 每拍 `TickSoundSynth.render()` 返回预渲染 PCM 短音（3 音色 × 重音/普通 共 6 种缓存），`MetronomeAudioPlayer` 以 `AudioTrack(MODE_STREAM)` 写入；播放器**预热机制**（启动即写入静音）消除设备冷启动首拍延迟；
4. **状态回流** → 服务内 `StateFlow<status/settings>` + `SharedFlow<ticks>` 经 Binder 被 ViewModel 收集，驱动 UI 的节拍动画（`beatKey` 触发 `LaunchedEffect` 脉冲）、拍位指示与按钮状态；
5. **后台保证** → `START_STICKY` + `wasRunning` 标记实现进程被杀后自动恢复运行；`PARTIAL_WAKE_LOCK`（6h 超时）保证锁屏节拍；音频焦点丢失（如来电）自动暂停、恢复后自动续跑；
6. **持久化** → `MetronomePrefs`（SharedPreferences）保存 BPM/音色/音量/自定义预设/运行标记。

### 3.3 版本更新链路（独立于节拍器）

1. **启动检测** → `UpdateViewModel.checkOnLaunch()` 延迟 1.2s 调用 `UpdateManager.checkForUpdates()`；自动检查失败静默，手动检查失败 Toast 提示；
2. **拉取清单** → `UpdateSource`（Release 走 HTTP `cdn.sunrise1024.top/runbeat/manifest.json`；Debug 走内置 `assets/update_mock.json`）→ `UpdateChecker` 重试拉取 → `UpdateManifest.parse` 解析；
3. **决策** → `UpdatePolicy.decide`：远端 versionCode > 本地则有更新；`force_update=true` 强制弹窗（仅「立即下载」）；「不再提示」版本记录于 `UpdatePrefs` 只对该版本生效；
4. **下载** → `DownloadService` 拉起前台 `AppDownloader`：自研 HTTP 流式下载（替代系统 DownloadManager），支持 Range 断点续传（`.part` 文件）、暂停/继续/取消、失败自动重试 2 次、字节完整性校验；进度经 `StateFlow` 同时驱动应用内弹窗与通知栏；
5. **安装** → 下载完成校验文件后，已具备「安装未知来源」权限则经 `FileProvider` 直接拉起系统安装器；否则跳转设置页，**返回应用时 `ON_RESUME` 自动继续安装**（无需手动重复操作）；
6. **发版配套** → `bumpVersion` Gradle 任务自动「versionCode+1、versionName patch+0.0.1、生成 deploy/manifest.json、同步 Debug Mock」；`release.bat` / `release.sh` 一键「递增版本 → 构建已签名 Release」。

### 3.4 模块依赖关系

- `MetronomeEngine`、`TickTiming`、`TickSoundSynth`、`UpdatePolicy`、`UpdateManifest`、`UpdateChecker` 均为**纯 Kotlin 无 Android 依赖**，可在 JVM 直接单测（注释明确为未来 KMP 移植 iOS 预留）；
- `MetronomeService` 是节拍引擎的**唯一持有者**（ViewModel 不直接持有引擎），保证后台/锁屏节拍不中断；
- `UpdateManager`、`AppDownloader` 为**进程级单例**，跨 Activity/Service 复用状态，避免重复下载与弹窗。

---

## 4. 全链路技术架构

### 4.1 前端（Android 客户端）

| 层级 | 技术选型 |
| --- | --- |
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（BOM 2026.02.01）、Material 3、动态取色（Android 12+） |
| 架构模式 | MVVM（`AndroidViewModel` + `StateFlow`/`SharedFlow` + Compose `collectAsState`）+ 前台服务 |
| 生命周期 | `lifecycle-runtime-ktx`、`lifecycle-viewmodel-compose`、`activity-compose` 1.13.0 |
| 并发 | Kotlin Coroutines（`CoroutineScope` + `Dispatchers.IO/Main`） |
| 音频 | 原生 `AudioTrack`（MODE_STREAM）+ 程序化 PCM 合成（无音频资源文件） |
| 系统能力 | 前台服务（mediaPlayback / dataSync）、WakeLock、音频焦点、FileProvider、通知栏操作 |
| SDK 要求 | minSdk 24（Android 7.0）、targetSdk 36、compileSdk 36.1 |
| 构建 | AGP 9.3.1、Gradle 版本目录（`libs.versions.toml`）、配置缓存开启、Release 已配置签名（`runbeat-release.jks`） |

### 4.2 后端 / 更新基础设施

- **无自有后端服务**：应用不依赖任何业务 API；
- **版本更新分发**：静态 CDN（`https://cdn.sunrise1024.top/runbeat/`）托管 `manifest.json` + 签名 APK，客户端 `HttpURLConnection` 直接拉取/下载；
- **清单协议**（`UpdateManifest`）：`version_code` / `version_name` / `update_url` / `release_notes`（可选）/ `force_update`（可选，默认 false）。

### 4.3 数据层

| 数据类型 | 方案 |
| --- | --- |
| 节拍设置（BPM/音色/音量/自定义预设） | `SharedPreferences`（`MetronomePrefs`） |
| 更新状态（不再提示版本、下载任务去重） | `SharedPreferences`（`UpdatePrefs`） |
| 更新包文件 | 外部存储 `Android/data/.../files/updates/`，回退应用内部目录 |

> 无 Room/数据库、无网络业务数据、无账号体系，数据面极简。

---

## 5. 开发进度与状态

### 5.1 Git 历史（4 次提交）

| 提交 | 说明 |
| --- | --- |
| `76857ba` | init：初始化 |
| `ad77801` | feat：跑步节拍器与版本更新系统 |
| `2345b58` | feat：更新版本检查逻辑，增加进度条反馈与超时设置 |
| `7cb3d24`（2026-07-25） | feat：更新图标资源，添加单色图标并优化背景与前景图 |

### 5.2 版本发布轨迹（version.properties 与 manifest 推断）

- `version.properties`：`VERSION_CODE=8` → 工作区已为 `10`（`1.1.6` → `1.1.8`），中间 `1.1.7`（v9）曾在 `deploy/manifest.json` 中体现；
- 最新 `deploy/manifest.json`：`1.1.8` / code 10，更新说明「声音启动优化」，`force_update=false`；
- 说明**发版在 Git 之外进行**（`release.bat` 自动递增 + 构建，不自动 commit），因此版本号与提交记录不同步。

### 5.3 当前工作区未提交改动（WIP）

「声音启动优化」专项，共 5 个文件：

| 文件 | 改动 |
| --- | --- |
| `MetronomeService.kt` | 停止时**不再释放 audioPlayer**，保持音频管线常热，避免每次「开始」冷启动设备导致首拍延迟 |
| `MetronomeAudioPlayer.kt` | 新增 `ensureStarted()` + `prime()` 预热（创建即 play + 写 125ms 静音）；缓冲从 500ms 缩至 200ms 以降低首拍输出延迟 |
| `version.properties` / `deploy/manifest.json` / `update_mock.json` | 版本 1.1.6 → 1.1.8 同步 |

### 5.4 测试覆盖

**单元测试（JVM，`app/src/test`）**
- `TickTimingTest`：BPM 间隔与节拍时间数学
- `MetronomeEngineTest`：调度精度（含 <50ms 抖动断言）、暂停/恢复/重启状态机
- `TickSoundSynthTest`：PCM 合成输出
- `UpdateManifestTest` / `UpdatePolicyTest` / `UpdateCheckerTest`：清单解析、弹窗决策、重试容错

**仪器测试（Android，`app/src/androidTest`）**
- `MetronomeScreenTest`：主界面核心控件与预设渲染
- `UpdateDialogTest`：普通/强制/下载进度三种弹窗形态

### 5.5 文档与工具链

- `docs/DEPLOY.md`：完整发布部署指南（版本递增、签名、服务器部署、测试要点、上线清单）
- `release.bat`（Windows）/ `release.sh`（macOS/Linux，自动修复 JAVA_HOME）：一键发版

---

## 6. 存在的问题与风险

| 级别 | 问题 | 说明 |
| --- | --- | --- |
| 高 | **签名密钥硬编码** | `runbeat-release.jks`、alias 与密码 `CHANGE_ME` 直接写在 [build.gradle.kts](file:///d:/project/project/app/RunBeat/app/build.gradle.kts) 并提交仓库，密钥文件也在项目内；`DEPLOY.md` 已标注「生产务必更换」但尚未执行 |
| 中 | **无自有后端，更新依赖单点 CDN** | 版本检测/下载全部依赖 `cdn.sunrise1024.top`，CDN 故障即无法更新；且无渠道/灰度能力 |
| 中 | **Release 关闭了代码优化** | `optimization { enable = false }`，APK 体积与运行效率未优化 |
| 中 | **UI 文案硬编码中文** | 音色名（咔嗒/哔声/木鱼）、预设名等在 `MetronomeScreen`/`ViewModel` 中写死，`strings.xml` 已定义但未引用，无法本地化 |
| 中 | **主题体系混用** | `Theme.kt` 仍使用模板紫色系 + 默认开启动态取色，而主界面大量使用自定义品牌色（`AccentBeat` 等），两套色系并存 |
| 低 | **测试残留不一致** | `update_test.json` 的 `update_url` 指向旧版 1.1.7 APK，与当前版本不一致（调试用途，需注意） |
| 低 | **版本号与 Git 不同步** | 发版脚本不自动 commit，`version.properties` 与提交历史脱节，难以追溯版本对应代码 |
| 低 | **`allowBackup="true"`** | 应用数据可被备份导出（本地设置，风险有限） |

---

## 7. 待开发方向（推断，无官方 Roadmap）

项目无明确的路线图文档，结合代码注释与架构可推断的演进方向：

1. **核心包 KMP 移植**：`MetronomeConstants` 注释明确「便于未来通过 KMP 移植到 iOS」，`core`/`audio` 层已具备纯 Kotlin 基础；
2. **本地化（多语言）**：UI 文案已具备抽离条件（`strings.xml` 已有完整资源）；
3. **签名安全整改**：更换生产 keystore、将密码移入环境变量/CI Secrets；
4. **功能扩展可能**：更多音色/自定义小节拍数、训练计划（间歇计时）、配速换算、步频统计等跑步场景能力；
5. **更新分发加固**：多 CDN 回源、下载校验（MD5）、灰度发布。

---

## 8. 一句话总结

> RunBeat 是一个**单机优先、面向跑步步频训练**的 Android 高精度节拍器应用：以「纯 Kotlin 零漂移调度引擎 + 前台服务后台保活 + 程序化音频合成」为核心，配以完整的「CDN 清单 + 应用内断点续传下载 + 自动安装」自更新体系；当前已迭代至 1.1.8 并对外发布，正处于「声音启动优化」的收尾阶段，整体处于**功能可用、工程化初具规模、尚未做生产级安全与多端扩展**的早期成熟状态。

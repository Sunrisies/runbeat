# RunBeat 发布部署指南

## 1. 版本更新系统概述

应用内置完整的自动版本更新流程：

```
启动自动检测 → 拉取服务器 manifest.json → 与本地 versionCode 比对
  → 有新版本 → 弹窗（立即下载 / 暂不下载 / 不再提示）
  → 立即下载 → DownloadManager 后台下载（系统通知 + 应用内进度条）
  → 下载完成 → 自动拉起系统安装器（权限不足时先引导开启）
```

关键文件：
- `app/src/main/java/com/android/runbeat/update/` — 更新系统全部逻辑
- `app/build.gradle.kts` — 版本号与 `UPDATE_CHECK_URL` 配置
- `app/src/main/assets/update_mock.json` — Debug Mock 清单

## 2. 发版流程（一键版本递增）

版本号由项目根目录 `version.properties` 统一管理：

```
VERSION_CODE=2
VERSION_NAME=1.1.0
```

### 2.1 递增小版本（每次 +0.0.1）

```bash
./gradlew :app:bumpVersion
```

该任务自动完成三件事：
1. **版本递增**：`versionCode +1`，`versionName` patch `+0.0.1`（如 1.1.0 → 1.1.1），写回 `version.properties`
2. **生成部署清单**：生成 `deploy/manifest.json`（`version_code`/`version_name`/`update_url` 自动同步）
3. **同步 Debug Mock**：更新 `assets/update_mock.json` 为同一版本，保证更新到该版本后 Debug 不再重复弹窗

> 生成的 `manifest.json` 中 `release_notes` 为占位符，上传前请改为本次更新说明。

### 2.2 服务器地址

`app/build.gradle.kts` 中 `UPDATE_CHECK_URL` 已配置为：
`https://cdn.sunrise1024.top/runbeat/manifest.json`，无需改动（必须 https）。

### 2.3 签名

已生成并配置签名 keystore：
- 文件：`runbeat-release.jks`（项目根目录，已加入 `app/build.gradle.kts` 的 `signingConfigs.release`）
- alias：`runbeat`，密码：`CHANGE_ME`
- **生产环境务必更换**：重新生成 keystore 并修改 `build.gradle.kts` 中的密码；
  或使用 Android Studio `Build > Generate Signed Bundle / APK`。

> 签名后 `assembleRelease` 直接产出 **已签名** 的 `app-release.apk`（可直接安装），不再是 unsigned 包。

## 3. 服务器部署

任选：自建 Nginx、OSS（阿里云/腾讯云）、GitHub Releases。需部署两个文件：

### 3.1 manifest.json

```json
{
  "version_code": 2,
  "version_name": "1.1.0",
  "update_url": "https://your-domain.com/runbeat-1.1.0.apk",
  "release_notes": "更新说明，\n支持换行",
  "force_update": false
}
```

字段与 `UpdateManifest.parse`（`update/UpdateManifest.kt`）严格一致：
- `version_code`：int，与本地 `versionCode` 比对，更大即可更新
- `version_name`：展示用版本名
- `update_url`：APK 下载地址（https）
- `release_notes`：弹窗展示的更新说明（可省略）
- `force_update`：true 时弹窗仅保留「立即下载」，阻断旧版使用（可省略，默认 false）

### 3.2 APK 安装包
`update_url` 指向的可直接下载的 APK 文件（需配置正确 MIME：`application/vnd.android.package-archive`）。

## 4. 构建命令

```bash
./gradlew :app:bumpVersion          # ① 递增版本 + 生成 manifest.json
./gradlew assembleRelease           # ② 发布包 → app/build/outputs/apk/release/
./gradlew assembleDebug             # ③ 调试包（Debug 走内置 Mock）
```

## 4.1 一键发版（推荐）

Windows：双击或执行 `release.bat`；macOS/Linux/CI：执行 `./release.sh`。

脚本自动完成「版本递增 → 生成 manifest → 构建 Release」，构建后只需手动上传。

## 4.2 完整发版步骤（小版本更新）

```bash
# 方式一：一键脚本（推荐）
release.bat          # 或 ./release.sh（Git Bash，自动修复 JAVA_HOME）
# 自动完成：1.1.0 → 1.1.1、生成 deploy/manifest.json、构建已签名 app-release.apk

# 方式二：手动两步
./gradlew :app:bumpVersion                  # 1.1.0 → 1.1.1，生成 deploy/manifest.json
./gradlew assembleRelease                   # 构建（读取新版本，已签名）

# ① 将 app-release.apk 重命名为 runbeat-1.1.1.apk 上传到 cdn.sunrise1024.top/runbeat/
# ② 编辑 deploy/manifest.json 填写 release_notes 后上传覆盖 manifest.json
# ③ 用户端旧版启动即检测到新版本
```

> 注意：`version_code` / `version_name` 由 `bumpVersion` 在打包前自动递增，manifest 与 APK 版本保持一致，无需手动修改。

## 4.3 安装权限说明

更新流程采用「下载不拦截、安装时引导」策略：
- 点「立即下载」**无需**预先授权，直接开始后台下载
- 下载完成后若未开启「安装未知来源应用」，自动跳转系统设置页
- 开启后**返回应用自动继续安装**（无需再手动点按钮），不会反复索要权限

## 5. 上线检查清单

- [ ] `versionCode` 已递增，`UPDATE_CHECK_URL` 指向真实服务器
- [ ] `force_update=false` 的普通版本：弹窗含 立即下载 / 暂不下载 / 不再提示 三按钮
- [ ] 严重修复发 `force_update=true`：弹窗仅剩「立即下载」，无法跳过
- [ ] 「不再提示」后该版本不打扰；发布更高 `versionCode` 后重新提示
- [ ] 手机开启 设置→应用→RunBeat→安装未知应用 后，下载完成自动拉起安装
- [ ] 下载中断（切后台/杀进程）后重新进入，系统 DownloadManager 继续下载并引导安装

## 6. 各环境测试要点

| 场景 | 预期行为 |
| --- | --- |
| 启动自动检测（弱网/断网） | 静默失败，不弹窗、不崩溃 |
| 手动「检查更新」失败 | Toast「检查失败，请检查网络连接」 |
| 手动检查且已是最新 | Toast「已是最新版本」 |
| 下载中断后重进 | DownloadManager 续传，完成后自动引导安装 |
| 下载失败 | 弹窗「重试」 |
| 安装包损坏 | 弹窗「安装包无效，请重新下载」 |
| 安装权限被拒绝 | 弹窗引导跳系统设置页；授权后重试正常安装 |
| 强制更新 | 旧版无法继续使用 |

## 7. Debug 演示模式

Debug 构建默认 `UpdateConfig.USE_MOCK = true`，读取 `assets/update_mock.json`（**使用清单真实版本号与本地比对**）。`bumpVersion` 每次发版会自动把 Mock 同步到新版本，因此：

- 旧版（versionCode 低于 Mock）→ 启动弹更新
- 已更新到 Mock 对应版本 → 不再弹窗（不会无限重复提示）

关闭方式：`update/UpdateConfig.kt` 中 `USE_MOCK` 改为 `false`。

## 7.1 界面版本显示

主界面顶部左侧显示当前版本（`v1.1.0`），右侧为「检查更新」入口，可直观确认是否已更新到最新。

## 8. 通知与权限说明

应用所需与更新相关的权限（`AndroidManifest.xml`）：
- `INTERNET` / `ACCESS_NETWORK_STATE` — 版本检测与下载
- `REQUEST_INSTALL_PACKAGES` — 安装未知来源应用
- `POST_NOTIFICATIONS`（Android 13+ 运行时申请）— 节拍器后台通知

安装未知应用权限：首次下载会校验 `canRequestPackageInstalls()`，未开启时
引导跳转系统「安装未知应用」设置页，授权后重新下载即可自动安装。

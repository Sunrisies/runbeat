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

## 2. 发布前必改项

### 2.1 版本号
`app/build.gradle.kts`：

```kotlin
defaultConfig {
    versionCode = 2        // 每次发版必须 +1，否则设备不会提示更新
    versionName = "1.1.0"
}
```

### 2.2 服务器地址
`app/build.gradle.kts` 中把 `release` 的 `UPDATE_CHECK_URL` 改为真实地址：

```kotlin
buildTypes {
    release {
        buildConfigField("String", "UPDATE_CHECK_URL",
            "\"https://your-domain.com/runbeat/manifest.json\"")
    }
}
```

> 必须使用 **https**，Android 9+ 默认禁止明文 http。

### 2.3 签名
Release 构建需配置签名，或用 Android Studio
`Build > Generate Signed Bundle / APK` 生成。

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
./gradlew assembleRelease     # 发布包 → app/build/outputs/apk/release/
./gradlew assembleDebug       # 调试包（Debug 内置 Mock，无需服务器可演示更新）
```

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

Debug 构建默认 `UpdateConfig.USE_MOCK = true`，启动自动弹 Mock 更新弹窗
（`assets/update_mock.json`，版本号自动设为 本地 versionCode + 1），
无需服务器即可验证完整更新流程。

关闭方式：`update/UpdateConfig.kt` 中 `USE_MOCK` 改为 `false`。

## 8. 通知与权限说明

应用所需与更新相关的权限（`AndroidManifest.xml`）：
- `INTERNET` / `ACCESS_NETWORK_STATE` — 版本检测与下载
- `REQUEST_INSTALL_PACKAGES` — 安装未知来源应用
- `POST_NOTIFICATIONS`（Android 13+ 运行时申请）— 节拍器后台通知

安装未知应用权限：首次下载会校验 `canRequestPackageInstalls()`，未开启时
引导跳转系统「安装未知应用」设置页，授权后重新下载即可自动安装。

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---- 版本号统一管理（由 bumpVersion 任务自动维护）----
val versionFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE", "1").toInt()
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "1.0.0")

android {
    namespace = "com.android.runbeat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("runbeat-release.jks")
            storePassword = "CHANGE_ME"
            keyAlias = "runbeat"
            keyPassword = "CHANGE_ME"
        }
    }

    defaultConfig {
        applicationId = "com.android.runbeat"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
            buildConfigField("String", "UPDATE_CHECK_URL", "\"https://cdn.sunrise1024.top/runbeat/manifest.json\"")
        }
        debug {
            buildConfigField("String", "UPDATE_CHECK_URL", "\"https://cdn.sunrise1024.top/runbeat/manifest.json\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ---- 发版辅助：自动递增小版本（+0.0.1）并生成部署 manifest.json ----
val deployDirProvider = rootProject.layout.projectDirectory.dir("deploy")
val mockFileProvider = project.layout.projectDirectory.file("src/main/assets/update_mock.json")

tasks.register("bumpVersion") {
    group = "release"
    description = "递增版本号（versionCode+1、versionName patch+0.0.1），生成 deploy/manifest.json 并同步 Debug Mock 清单"
    // 任务会改写配置期读取的 version.properties，不适用配置缓存
    notCompatibleWithConfigurationCache("bumpVersion mutates version.properties")

    val versionFileRef = versionFile
    val deployDirRef = deployDirProvider
    val mockFileRef = mockFileProvider

    doLast {
        val parts = (appVersionName.split(".").mapNotNull { it.toIntOrNull() } + listOf(0, 0, 0)).take(3)
        val newCode = appVersionCode + 1
        val newName = "${parts[0]}.${parts[1]}.${parts[2] + 1}"

        // 1. 更新 version.properties
        versionFileRef.writeText("VERSION_CODE=$newCode\nVERSION_NAME=$newName\n")

        // 2. 生成部署清单 deploy/manifest.json
        val updateUrl = "https://cdn.sunrise1024.top/runbeat/runbeat-$newName.apk"
        val manifestJson = buildString {
            appendLine("{")
            appendLine("  \"version_code\": $newCode,")
            appendLine("  \"version_name\": \"$newName\",")
            appendLine("  \"update_url\": \"$updateUrl\",")
            appendLine("  \"release_notes\": \"请填写本次更新说明\",")
            appendLine("  \"force_update\": false")
            appendLine("}")
        }
        deployDirRef.asFile.mkdirs()
        deployDirRef.file("manifest.json").asFile.writeText(manifestJson)

        // 3. 同步 Debug Mock 清单，保证更新到该版本后不再重复弹窗
        val mockFile = mockFileRef.asFile
        if (mockFile.exists()) {
            val mock = mockFile.readText()
                .replace(Regex("\"version_code\":\\s*\\d+"), "\"version_code\": $newCode")
                .replace(Regex("\"version_name\":\\s*\"[^\"]*\""), "\"version_name\": \"$newName\"")
                .replace(Regex("\"update_url\":\\s*\"[^\"]*\""), "\"update_url\": \"$updateUrl\"")
            mockFile.writeText(mock)
        }

        println("================================================")
        println("版本已递增: $appVersionName -> $newName")
        println("versionCode: $appVersionCode -> $newCode")
        println("已生成: deploy/manifest.json")
        println("已同步: app/src/main/assets/update_mock.json")
        println("下一步: ./gradlew assembleRelease")
        println("================================================")
    }
}
#!/usr/bin/env bash
# RunBeat 一键发版：自动递增版本 + 构建 Release（macOS/Linux/Git Bash）
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 自动修复 JAVA_HOME ----------
java_cmd() { [ -x "${1:-}/bin/java" ] || [ -x "${1:-}/bin/java.exe" ]; }

if ! java_cmd "${JAVA_HOME:-}"; then
    echo "检测到 JAVA_HOME 无效（${JAVA_HOME:-未设置}），正在自动查找 JDK ..."
    for cand in \
        "/c/Program Files/Android/Android Studio/jbr" \
        "/c/Program Files/Android/Android Studio Preview/jbr" \
        "/c/Program Files/Android/Android Studio Canary/jbr" \
        "$HOME/AppData/Local/Android/Sdk/jbr" \
        "/c/Program Files/Java"/* \
        "/c/Program Files/Microsoft/jdk"/* \
        "/c/Program Files/Eclipse Adoptium"/* \
        "/usr/lib/jvm"/* \
        ; do
        if java_cmd "$cand" 2>/dev/null; then
            export JAVA_HOME="$cand"
            break
        fi
    done
fi

if ! java_cmd "${JAVA_HOME:-}"; then
    echo "错误：未找到有效的 JDK（JAVA_HOME=${JAVA_HOME:-未设置}）。" >&2
    echo "请安装 Android Studio 或 JDK 17+，然后重试。" >&2
    exit 1
fi

echo "使用 JAVA_HOME=$JAVA_HOME"

echo "================================================"
echo "  RunBeat 一键发版：自动递增版本 + 构建 Release"
echo "================================================"

echo "[1/2] 递增版本号并生成 manifest.json ..."
./gradlew :app:bumpVersion

echo "[2/2] 构建 Release 包 ..."
./gradlew :app:assembleRelease

echo ""
echo "================================================"
echo "  构建完成！请手动完成以下部署："
echo "  1. 将 app/build/outputs/apk/release/app-release.apk"
echo "     重命名为 runbeat-新版本.apk"
echo "  2. 修改 deploy/manifest.json 中的 release_notes 为本次更新说明"
echo "  3. 上传 runbeat-新版本.apk 与 deploy/manifest.json"
echo "     到 https://cdn.sunrise1024.top/runbeat/"
echo "================================================"

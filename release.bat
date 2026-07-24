@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM ---------- 自动修复 JAVA_HOME ----------
if not defined JAVA_HOME goto :findjbr
if exist "%JAVA_HOME%\bin\java.exe" goto :jbrok
:findjbr
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    goto :jbrok
)
if exist "%USERPROFILE%\AppData\Local\Android\Sdk\jbr\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\AppData\Local\Android\Sdk\jbr"
    goto :jbrok
)
echo 错误：未找到有效的 JAVA_HOME，请安装 JDK 17+ 或 Android Studio 后重试。
exit /b 1
:jbrok
echo 使用 JAVA_HOME=%JAVA_HOME%

echo ================================================
echo   RunBeat 一键发版：自动递增版本 + 构建 Release
echo ================================================

echo [1/2] 递增版本号并生成 manifest.json ...
call gradlew.bat :app:bumpVersion
if errorlevel 1 (
    echo 版本递增失败，终止发版。
    exit /b 1
)

echo [2/2] 构建 Release 包 ...
call gradlew.bat :app:assembleRelease
if errorlevel 1 (
    echo 构建失败，终止发版。
    exit /b 1
)

echo.
echo ================================================
echo   构建完成！
echo   请手动完成以下部署：
echo   1. 将 app\build\outputs\apk\release\app-release.apk
echo      重命名为 runbeat-新版本.apk
echo   2. 修改 deploy\manifest.json 中的 release_notes 为本次更新说明
echo   3. 上传 runbeat-新版本.apk 与 deploy\manifest.json
echo      到 https://cdn.sunrise1024.top/runbeat/
echo ================================================
pause

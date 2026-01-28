@echo off
REM Maven 中央仓库发布脚本 (Windows - Central Publishing Portal)
REM 使用方法: release.cmd <version>
REM 示例: release.cmd 2.0.0

setlocal enabledelayedexpansion

if "%1"=="" (
    echo 错误: 请提供版本号
    echo 使用方法: release.cmd ^<version^>
    echo 示例: release.cmd 2.0.0
    exit /b 1
)

set VERSION=%1

echo ========================================
echo 准备发布版本: %VERSION%
echo ========================================

REM 检查是否有未提交的更改
git status --porcelain > nul 2>&1
if errorlevel 1 (
    echo 错误: 存在未提交的更改，请先提交或暂存
    git status --short
    exit /b 1
)

REM 1. 设置版本号
echo [1/7] 设置版本号...
call mvn versions:set -DnewVersion=%VERSION%
call mvn versions:commit

REM 2. 运行测试
echo [2/7] 运行测试...
call mvn clean test
if errorlevel 1 (
    echo 测试失败，发布终止
    exit /b 1
)

REM 3. 构建项目
echo [3/7] 构建项目...
call mvn clean install
if errorlevel 1 (
    echo 构建失败，发布终止
    exit /b 1
)

REM 4. 提交版本变更
echo [4/7] 提交版本变更...
git add pom.xml
git commit -m "Release version %VERSION%"
git tag -a "v%VERSION%" -m "Release version %VERSION%"

REM 5. 部署到 Maven 中央仓库 (Central Portal)
echo [5/7] 部署到 Maven 中央仓库...
call mvn clean deploy
if errorlevel 1 (
    echo 部署失败，发布终止
    exit /b 1
)

REM 6. 推送到 Git
echo [6/7] 推送到 Git...
for /f "tokens=*" %%i in ('git rev-parse --abbrev-ref HEAD') do set CURRENT_BRANCH=%%i
git push origin %CURRENT_BRANCH%
git push origin "v%VERSION%"

REM 7. 设置下一个开发版本
for /f "tokens=1,2,3 delims=." %%a in ("%VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set PATCH=%%c
)
set /a NEXT_PATCH=%PATCH%+1
set NEXT_VERSION=%MAJOR%.%MINOR%.%NEXT_PATCH%-SNAPSHOT

echo [7/7] 设置下一个开发版本: %NEXT_VERSION%
call mvn versions:set -DnewVersion=%NEXT_VERSION%
call mvn versions:commit

git add pom.xml
git commit -m "Prepare for next development iteration"
git push origin %CURRENT_BRANCH%

echo ========================================
echo 发布完成!
echo ========================================
echo.
echo 版本 %VERSION% 已成功发布到 Maven 中央仓库
echo.
echo 查看发布状态:
echo - Central Portal: https://central.sonatype.com/publishing/deployments
echo - Maven Central: https://search.maven.org/artifact/io.github.fluxsce/flux-service-center-sdk/%VERSION%/jar
echo.
echo 注意事项:
echo - 构件将在 30分钟 - 4小时 后同步到 Maven Central
echo - 使用 autoPublish=true，无需手动操作
echo - 如有问题，请访问 Central Portal 查看详细日志

endlocal


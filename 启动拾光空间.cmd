@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

docker info >nul 2>&1
if not errorlevel 1 goto docker_ready

if exist "D:\Docker\DockerDesktopLocal" set "LOCALAPPDATA=D:\Docker\DockerDesktopLocal"
echo 正在启动 Docker Desktop，请稍候……
start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"

for /l %%i in (1,1,90) do (
  docker info >nul 2>&1 && goto docker_ready
  timeout /t 2 /nobreak >nul
)

echo Docker Desktop 未能在 3 分钟内启动，请打开 Docker Desktop 后重试。
pause
exit /b 1

:docker_ready
echo 正在启动拾光空间……
docker compose up -d --build
if errorlevel 1 (
  echo 启动失败，请保留此窗口中的提示。
  pause
  exit /b 1
)

echo 拾光空间已启动：http://localhost:3000
start "" "http://localhost:3000"
endlocal

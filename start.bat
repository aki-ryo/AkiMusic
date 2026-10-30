@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo   潮汐 TIDE  ·  正在启动本地服务...
echo   浏览器会在 2 秒后自动打开 http://localhost:8777
echo   关闭这个窗口或按 Ctrl+C 即可停止。
echo.
start "" cmd /c "timeout /t 2 >nul & start "" http://localhost:8777"
python "%~dp0server.py" 8777
pause

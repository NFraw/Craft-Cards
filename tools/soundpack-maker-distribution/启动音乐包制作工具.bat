@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 "%~dp0soundpack_maker.py"
  goto done
)
where python >nul 2>nul
if %errorlevel%==0 (
  python "%~dp0soundpack_maker.py"
  goto done
)
echo 需要先安装 Python 3.10 或更新版本，并启用 tkinter。
echo https://www.python.org/downloads/
:done
if errorlevel 1 pause
endlocal

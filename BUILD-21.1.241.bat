@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo BUILD FAILED - copy the full error and send it back.
  pause
  exit /b 1
)
echo.
echo BUILD SUCCESSFUL
echo JAR: build\libs\originsmodernui-0.1.1.jar
pause

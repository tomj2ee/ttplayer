@echo off
REM ttplayer Windows 启动脚本
REM 需要先执行: mvn clean package

REM 获取脚本所在目录
set SCRIPT_DIR=%~dp0
set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

REM 可执行 fat jar
set JAR=%SCRIPT_DIR%\target\ttplayer.jar
if not exist "%JAR%" (
    echo 未找到 %JAR%，请先运行: mvn clean package
    exit /b 1
)

echo Starting ttplayer...
echo JAR=%JAR%

java -jar "%JAR%" %*

#!/bin/bash

# ttplayer 启动脚本
# 使用此脚本启动可以确保 Dock 栏显示正确的名称和图标
# 需要先执行: mvn clean package

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# 确定图标路径
ICON_PATH="$SCRIPT_DIR/src/main/resources/ico/ttplayer_32x32_32bpp.png"

# 可执行 fat jar
JAR="$SCRIPT_DIR/target/ttplayer.jar"

if [ ! -f "$JAR" ]; then
    echo "未找到 $JAR，请先运行: mvn clean package" >&2
    exit 1
fi

echo "Starting ttplayer..."
echo "JAR: $JAR"

# 使用 -Xdock 参数启动，这是确保 Dock 栏显示正确名称的关键
if [ -f "$ICON_PATH" ]; then
    java -Xdock:name="ttplayer" -Xdock:icon="$ICON_PATH" -jar "$JAR" "$@"
else
    java -Xdock:name="ttplayer" -jar "$JAR" "$@"
fi

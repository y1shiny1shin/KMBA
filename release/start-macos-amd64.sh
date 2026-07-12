#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JDK_DIR="$SCRIPT_DIR/jdk"
JAR="$SCRIPT_DIR/KMBA.jar"

if [ ! -f "$JAR" ]; then
    echo "[ERROR] KMBA.jar not found in $SCRIPT_DIR"
    exit 1
fi

if [ -f "$JDK_DIR/bin/java" ]; then
    JAVA="$JDK_DIR/bin/java"
elif [ -f "$JDK_DIR/Contents/Home/bin/java" ]; then
    # macOS JDK bundle layout
    JAVA="$JDK_DIR/Contents/Home/bin/java"
else
    if ! command -v java >/dev/null 2>&1; then
        echo "[ERROR] Java not found. Please install JDK 8+ or use the with-jdk package."
        exit 1
    fi
    JAVA="java"
fi

if [ $# -gt 0 ]; then
    # CLI 模式
    exec "$JAVA" -jar "$JAR" "$@"
else
    # Web 模式
    echo "Starting KMBA (Web)..."
    echo "Access: http://localhost:9099"
    echo "Press Ctrl+C to stop."
    echo ""
    exec "$JAVA" -jar "$JAR"
fi

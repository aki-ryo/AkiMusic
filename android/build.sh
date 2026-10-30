#!/usr/bin/env bash
# ---------------------------------------------------------------
# AkiMusic · 直接用 Android build-tools 打包 APK（不依赖 Gradle）
#
#   JAVA_HOME=/path/to/jdk17  ./android/build.sh
#
# 需要：JDK 11+（推荐 17）、Android SDK build-tools 35、platform android-36
# ---------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT/android"
BUILD="$ANDROID_DIR/build"
OUT="$ROOT/dist"

VERSION_NAME="0.1.0-beta"
VERSION_CODE="1"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
BT="${BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
PLATFORM="${PLATFORM_JAR:-$SDK/platforms/android-36/android.jar}"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
  *) EXE="" ;;
esac

# Git Bash 给 Windows 原生命令传路径时，@argfile 里的路径不会被自动转换
to_win() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}
WIN_ANDROID="$(to_win "$ANDROID_DIR")"
WIN_BUILD="$(to_win "$BUILD")"

echo "▸ JDK      : $("$JAVA" -version 2>&1 | head -1)"
echo "▸ SDK      : $SDK"
echo "▸ build    : $BT"

[ -f "$PLATFORM" ] || { echo "找不到 android.jar: $PLATFORM"; exit 1; }
[ -f "$BT/aapt2$EXE" ] || { echo "找不到 aapt2"; exit 1; }

# 1. 打包前端资源
rm -rf "$BUILD" "$ANDROID_DIR/app/assets/public"
mkdir -p "$BUILD" "$OUT" "$ANDROID_DIR/app/assets"
cp -r "$ROOT/public" "$ANDROID_DIR/app/assets/public"
echo "▸ 已同步 public/ → assets"

# 2. 编译 & 链接资源
"$BT/aapt2$EXE" compile --dir "$ANDROID_DIR/res" -o "$BUILD/res.zip"
"$BT/aapt2$EXE" link \
  -o "$BUILD/base.apk" \
  -I "$PLATFORM" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  -A "$ANDROID_DIR/app/assets" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay
echo "▸ 资源已链接"

# 3. 编译 Java
mkdir -p "$BUILD/classes"
find "$ANDROID_DIR/java" -name '*.java' | sed "s|^$ANDROID_DIR|$WIN_ANDROID|" > "$BUILD/sources.txt"
"$JAVAC" -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
  -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d "$WIN_BUILD/classes" "@$BUILD/sources.txt"
echo "▸ Java 已编译"

# 4. 转 dex
mkdir -p "$BUILD/dex"
CLASSES=$(find "$BUILD/classes" -name '*.class' | tr '\n' ' ')
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" --min-api 26 --release \
  --output "$BUILD/dex" $CLASSES
echo "▸ 已生成 classes.dex"

# 5. 塞进 APK
python - "$BUILD/base.apk" "$BUILD/dex/classes.dex" "$BUILD/unsigned.apk" <<'PY'
import shutil, sys, zipfile
src, dex, out = sys.argv[1:4]
shutil.copyfile(src, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY

# 6. 对齐
"$BT/zipalign$EXE" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

# 7. 签名（首次运行自动生成测试证书）
KS="$ANDROID_DIR/keystore.jks"
if [ ! -f "$KS" ]; then
  echo "▸ 生成测试签名证书"
  "$KEYTOOL" -genkeypair -keystore "$KS" -alias akimusic -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass akimusic -keypass akimusic \
    -dname "CN=AkiMusic, OU=Mobile, O=AkiRyo, L=Shanghai, C=CN" >/dev/null 2>&1
fi

APK="$OUT/AkiMusic-$VERSION_NAME.apk"
"$JAVA" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KS" --ks-key-alias akimusic \
  --ks-pass pass:akimusic --key-pass pass:akimusic \
  --out "$APK" "$BUILD/aligned.apk"

"$JAVA" -jar "$BT/lib/apksigner.jar" verify --print-certs "$APK" | head -4

echo ""
echo "✅ 打包完成：$APK"
ls -lh "$APK" | awk '{print "   大小：" $5}'

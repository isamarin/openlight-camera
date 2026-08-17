#!/bin/sh
# Собрать пробник в APK напрямую инструментами SDK.
#
# Gradle здесь не нужен: у пробника один пакет, нет ресурсов и нет зависимостей.
# Цепочка ровно та, что Gradle прячет внутри — javac → d8 → aapt2 → zipalign → apksigner.
#
# Использование: sh astro-probe/build.sh [и потом установить получившийся APK]

set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/build"

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
[ -d "$SDK" ] || { echo "Android SDK не найден: $SDK" >&2; exit 1; }

# Берём самые старшие из установленных, но платформа только для компиляции:
# приложение объявляет targetSdk 22 и работает на Android 6 самой камеры.
BT=$(ls -1 "$SDK/build-tools" | sort -V | tail -1)
BUILD_TOOLS="$SDK/build-tools/$BT"
PLATFORM=$(ls -1d "$SDK"/platforms/android-* | sort -V | tail -1)
ANDROID_JAR="$PLATFORM/android.jar"
[ -f "$ANDROID_JAR" ] || { echo "не найден android.jar в $PLATFORM" >&2; exit 1; }

# Java 17: d8 и apksigner требуют 11+, а javac с -source 8 ещё умеет старый байткод.
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
[ -x "$JAVA_HOME/bin/javac" ] || { echo "не найдена Java 17 в $JAVA_HOME" >&2; exit 1; }
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"
export PATH

echo "build-tools $BT, платформа $(basename "$PLATFORM"), $($JAVA_HOME/bin/java -version 2>&1 | head -1)"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

echo "→ javac"
find "$HERE/src" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 8 -target 8 \
      -classpath "$ANDROID_JAR" \
      -d "$OUT/classes" \
      @"$OUT/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true

echo "→ d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BUILD_TOOLS/d8" --min-api 22 --lib "$ANDROID_JAR" --output "$OUT" @"$OUT/classes.txt"

echo "→ aapt2 link"
"$BUILD_TOOLS/aapt2" link \
    -I "$ANDROID_JAR" \
    --manifest "$HERE/AndroidManifest.xml" \
    --min-sdk-version 22 --target-sdk-version 22 \
    -o "$OUT/unaligned.apk"

echo "→ упаковка dex"
( cd "$OUT" && zip -q unaligned.apk classes.dex )

# Ключ живёт рядом с исходниками, а не в build/: build чистится каждой сборкой, а
# пересозданный ключ ломает обновление уже установленного APK.
KEYSTORE="$HERE/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo "→ ключ подписи (создаю впервые)"
    keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
            -alias probe -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Astro Probe, OU=openlight, O=openlight, C=RU" >/dev/null 2>&1
fi

echo "→ zipalign + подпись"
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/astro-probe.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
        --min-sdk-version 22 "$OUT/astro-probe.apk"

echo
echo "готово: $OUT/astro-probe.apk ($(wc -c < "$OUT/astro-probe.apk" | tr -d ' ') Б)"
echo "установка:  adb -s <серийник> install -r $OUT/astro-probe.apk"

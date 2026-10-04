#!/usr/bin/env bash
# ============================================================
# تجهيز بيئة بناء الأندرويد داخل GitHub Codespace
# يثبّت: JDK 17 + Android SDK (API 34) + Gradle 8.7
# آمن للتكرار: يتخطى ما هو مثبت مسبقًا
# ============================================================
set -e

echo "==> [1/4] فحص Java (يحتاج 17 أو أحدث)"
JDK_OK=0
if command -v java >/dev/null 2>&1; then
    V=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
    MAJOR=${V%%.*}
    echo "    النسخة الحالية: $V"
    if [ "$MAJOR" -ge 17 ] 2>/dev/null; then JDK_OK=1; fi
fi
if [ "$JDK_OK" -ne 1 ]; then
    echo "    تثبيت OpenJDK 17 ..."
    sudo apt-get update -y -qq
    sudo apt-get install -y -qq openjdk-17-jdk-headless
fi
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
echo "    JAVA_HOME=$JAVA_HOME"

echo "==> [2/4] تثبيت Android SDK (API 34)"
SDK="$HOME/android-sdk"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
    command -v unzip >/dev/null || { sudo apt-get update -y -qq && sudo apt-get install -y -qq unzip; }
    echo "    تنزيل أدوات سطر الأوامر ..."
    curl -fsSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    mkdir -p "$SDK/cmdline-tools"
    unzip -q /tmp/clt.zip -d "$SDK/cmdline-tools"
    mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -f /tmp/clt.zip
fi
export ANDROID_HOME="$SDK"
echo "    قبول التراخيص وتنزيل المنصات (قد يستغرق دقائق) ..."
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null
echo "    SDK جاهز في: $SDK"

echo "==> [3/4] تثبيت Gradle 8.7"
if [ ! -d "$HOME/gradle-8.7" ]; then
    curl -fsSL -o /tmp/g.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip
    unzip -q /tmp/g.zip -d "$HOME"
    rm -f /tmp/g.zip
fi
echo "    $(gradle-8.7 --version | grep Gradle || true)" 2>/dev/null || true

echo "==> [4/4] حفظ متغيرات البيئة في ~/.bashrc"
if ! grep -q "ANDROID_HOME" ~/.bashrc; then
    cat >> ~/.bashrc <<'ENVEOF'
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$HOME/gradle-8.7/bin
ENVEOF
fi

echo ""
echo "✓ اكتمل تجهيز البيئة بنجاح — انتقل إلى خطوة البناء:"
echo "  bash yousef/apk-yousef/codespace/build_apk.sh"

#!/usr/bin/env bash
# ============================================================
# تجهيز بيئة بناء الأندرويد داخل GitHub Codespace
# يثبّت: JDK 17 + Android SDK (API 34) + Gradle 8.7
# آمن للتكرار: يتخطى ما هو مثبت مسبقًا
#
# ملاحظة مهمة: Gradle 8.7 يعمل على Java من 17 حتى 21 فقط.
# لو كان Codespace يستخدم Java أحدث (مثل 25) يبحث السكربت
# عن JDK 17/21 ويثبّته تلقائيًا إن لم يجده.
# ============================================================
set -e

# ---------- اختيار JDK مناسب (17 حتى 21) ----------
pick_jdk() {
    # 1) هل نسخة Java الحالية ضمن المدى المناسب؟
    if command -v java >/dev/null 2>&1; then
        V=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
        MAJOR=${V%%.*}
        case "$MAJOR" in
            17|18|19|20|21) dirname "$(dirname "$(readlink -f "$(command -v java)")")"; return 0 ;;
        esac
    fi
    # 2) نسخ مثبتة عبر apt في /usr/lib/jvm
    for d in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/java-21-openjdk-*; do
        [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
    done
    # 3) نسخ sdkman القديمة إن وُجدت
    for d in "$HOME"/.sdkman/candidates/java/17* "$HOME"/.sdkman/candidates/java/21*; do
        [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
    done
    return 1
}

echo "==> [1/4] فحص Java (يحتاج Gradle 8.7 نسخة 17 حتى 21)"
JAVA_HOME="$(pick_jdk || true)"
if [ -n "$JAVA_HOME" ]; then
    echo "    وُجدت نسخة مناسبة: $JAVA_HOME"
else
    echo "    النسخة الحالية غير مناسبة — تثبيت OpenJDK 17 ..."
    sudo apt-get update -y -qq
    if ! sudo apt-get install -y -qq openjdk-17-jdk-headless; then
        echo "    apt فشل — محاولة التثبيت عبر sdkman ..."
        if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
            # shellcheck disable=SC1091
            source "$HOME/.sdkman/bin/sdkman-init.sh"
            yes | sdk install java 17.0.13-tem >/dev/null 2>&1 || true
            yes | sdk install java 17.0.12-tem >/dev/null 2>&1 || true
        fi
    fi
    JAVA_HOME="$(pick_jdk || true)"
    if [ -z "$JAVA_HOME" ]; then
        echo "✗ تعذر توفير JDK 17 — أرسل لي ناتج الأمر: ls /usr/lib/jvm"
        exit 1
    fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "    JAVA_HOME=$JAVA_HOME"
echo "    $(java -version 2>&1 | head -1)"

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
GRADLE_BIN="$HOME/gradle-8.7/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
    curl -fsSL -o /tmp/g.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip
    unzip -q /tmp/g.zip -d "$HOME"
    rm -f /tmp/g.zip
fi
if [ ! -x "$GRADLE_BIN" ]; then
    echo "✗ فشل تثبيت Gradle — أرسل لي آخر رسالة ظهرت"
    exit 1
fi
echo "    التحقق من عمل Gradle مع نسخة Java المختارة ..."
"$GRADLE_BIN" --version | grep -E "^Gradle|Launcher JVM" || true

echo "==> [4/4] حفظ متغيرات البيئة في ~/.bashrc"
if ! grep -q "ANDROID_HOME" ~/.bashrc; then
    cat >> ~/.bashrc <<ENVEOF
export ANDROID_HOME=$HOME/android-sdk
export JAVA_HOME=$JAVA_HOME
export PATH=\$PATH:\$ANDROID_HOME/platform-tools:\$HOME/gradle-8.7/bin:\$JAVA_HOME/bin
ENVEOF
fi

echo ""
echo "✓ اكتمل تجهيز البيئة بنجاح — انتقل إلى خطوة البناء:"
echo "  bash yousef/apk-yousef/codespace/build_apk.sh"

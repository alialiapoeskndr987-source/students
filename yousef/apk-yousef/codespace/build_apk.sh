#!/usr/bin/env bash
# ============================================================
# بناء APK لتطبيق «صحة رياضية» (SportHealth) داخل Codespace
# الناتج: yousef/apk-yousef/apk_output/SportHealth-v2.1-debug.apk
# ============================================================
set -e

APP_DIR="$(cd "$(dirname "$0")/../StepHealthApp" && pwd)"
cd "$APP_DIR"

# ---------- اختيار JDK مناسب (Gradle 8.7 يعمل على 17 حتى 21 فقط) ----------
pick_jdk() {
    if command -v java >/dev/null 2>&1; then
        V=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')
        MAJOR=${V%%.*}
        case "$MAJOR" in
            17|18|19|20|21) dirname "$(dirname "$(readlink -f "$(command -v java)")")"; return 0 ;;
        esac
    fi
    for d in /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/java-21-openjdk-* \
             "$HOME"/.sdkman/candidates/java/17* "$HOME"/.sdkman/candidates/java/21*; do
        [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
    done
    return 1
}

JAVA_HOME="$(pick_jdk || true)"
if [ -z "$JAVA_HOME" ]; then
    echo "✗ لم يُوجد JDK 17 — شغّل أولًا: bash yousef/apk-yousef/codespace/setup_android.sh"
    exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "Java المستخدمة للبناء: $(java -version 2>&1 | head -1)"

export ANDROID_HOME="$HOME/android-sdk"
[ -d "$ANDROID_HOME" ] || { echo "✗ لم يُوجد Android SDK — شغّل أولًا: bash yousef/apk-yousef/codespace/setup_android.sh"; exit 1; }
echo "sdk.dir=$ANDROID_HOME" > local.properties

GRADLE="$HOME/gradle-8.7/bin/gradle"
[ -x "$GRADLE" ] || { echo "✗ لم يُوجد Gradle — شغّل أولًا setup_android.sh"; exit 1; }

echo "==> بدء البناء (أول مرة يستغرق 15–25 دقيقة لتنزيل المكتبات، وبعدها دقائق) ..."
"$GRADLE" assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "✗ فشل البناء — انسخ آخر 20 سطرًا من رسالة الخطأ وأرسلها"; exit 1; }

OUT_DIR="$(dirname "$APP_DIR")/apk_output"
mkdir -p "$OUT_DIR"
cp "$APK" "$OUT_DIR/SportHealth-v2.1-debug.apk"

echo ""
echo "============================================================"
echo "✓ تم بناء التطبيق بنجاح!"
echo "  الملف: yousef/apk-yousef/apk_output/SportHealth-v2.1-debug.apk"
echo ""
echo "طرق نقل التطبيق إلى هاتفك:"
echo "  كليك يمين على الملف في مستكشف VS Code ثم Download"
echo "  (ملفات APK لا تدخل المستودع حمايةً من التعارض — تُبنى محليًا في كل إصدار)"
echo "ثبّت الملف على الهاتف مع السماح بـ«المصادر غير المعروفة»"
echo "============================================================"

#!/usr/bin/env bash
# ============================================================
# بناء APK لتطبيق «خطوة صحية» داخل Codespace
# الناتج: yousef/apk-yousef/apk_output/StepHealth-v0.1-debug.apk
# ============================================================
set -e

APP_DIR="$(cd "$(dirname "$0")/../StepHealthApp" && pwd)"
cd "$APP_DIR"

export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
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
cp "$APK" "$OUT_DIR/StepHealth-v0.1-debug.apk"

echo ""
echo "============================================================"
echo "✓ تم بناء التطبيق بنجاح!"
echo "  الملف: yousef/apk-yousef/apk_output/StepHealth-v0.1-debug.apk"
echo ""
echo "طرق نقل التطبيق إلى هاتفك:"
echo "  1) كليك يمين على الملف في مستكشف VS Code ثم Download"
echo "  2) أو: git add -A && git commit -m 'بناء APK' && git push"
echo "     ثم افتح المستودع من متصفح الهاتف وحمّل الملف"
echo "ثبّت الملف على الهاتف مع السماح بـ«المصادر غير المعروفة»"
echo "============================================================"

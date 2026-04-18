#!/bin/bash
set -e

echo ""
echo "🔨 SipDoor APK derleniyor..."
echo ""

# ANDROID_HOME yoksa ayarla
if [ -z "$ANDROID_HOME" ]; then
  export ANDROID_HOME="/home/vscode/android-sdk"
  export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0
fi

# Gradle wrapper yoksa veya çalıştırılamıyorsa indir
if [ ! -f "./gradlew" ]; then
  echo "⚠️  gradlew bulunamadı, gradle wrapper oluşturuluyor..."
  gradle wrapper --gradle-version=8.4
fi

chmod +x ./gradlew

# Debug APK derle
echo "▶ Debug APK derleniyor..."
./gradlew assembleDebug --no-daemon --stacktrace 2>&1 | tail -20

# APK çıktısını kontrol et
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
  SIZE=$(du -h "$APK_PATH" | cut -f1)
  echo ""
  echo "═══════════════════════════════════════════"
  echo "  ✅ APK başarıyla oluşturuldu!"
  echo ""
  echo "  📦 Konum : $APK_PATH"
  echo "  📏 Boyut : $SIZE"
  echo ""
  echo "  İndirmek için:"
  echo "  Explorer panelinde $APK_PATH dosyasına"
  echo "  sağ tıklayıp 'Download' seçin."
  echo "═══════════════════════════════════════════"
else
  echo ""
  echo "❌ APK oluşturulamadı. Yukarıdaki hatayı kontrol edin."
  exit 1
fi

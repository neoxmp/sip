# Linphone SDK - obfuscation'dan koru
-keep class org.linphone.** { *; }
-keep class org.webrtc.** { *; }

# SipDoor uygulama sınıfları
-keep class com.sipdoor.app.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Android temel bileşenler
-keepclassmembers class * extends android.app.Service { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }

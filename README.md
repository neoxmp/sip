# 🔐 SipDoor - Android SIP Kapı Açma Uygulaması

Fanvil i66 IP diyafon (veya herhangi bir SIP kapı zili) ile entegre çalışan,
gelen çağrıyı yanıtlayıp DTMF kodu ile kapı açan Android uygulaması.

---

## 📱 Özellikler

- SIP sunucusuna kayıt (UDP/TCP/TLS)
- Gelen çağrıyı kilit ekranında göster
- Sesli ve görüntülü çağrı yanıtlama
- **Tek tuş ile kapı açma** (yapılandırılabilir DTMF kodu)
- Manuel DTMF klavyesi
- Mikrofon sessize alma, hoparlör değiştirme
- Cihaz yeniden başladığında otomatik SIP kaydı
- Otomatik yanıtlama (isteğe bağlı, saniye cinsinden)

---

## 🚀 Kurulum

### 1. Android Studio'ya aktar
```
File > Open > SipDoorApp klasörünü seç
```

### 2. Gradle Sync
Android Studio otomatik olarak Linphone SDK'yı indirir.

### 3. Uygulamayı çalıştır
Gerçek cihazda test etmeniz önerilir (emülatörde SIP çalışabilir ama ses/video sorunlu olabilir).

---

## ⚙️ Uygulama Ayarları

Uygulamayı açınca şu alanları doldurun:

| Alan | Açıklama | Örnek |
|------|----------|-------|
| Dahili No | SIP kullanıcı adı | `101` |
| Şifre | SIP şifresi | `mypassword` |
| SIP Domain / IP | Sunucu adresi | `192.168.1.100` |
| Port | SIP portu | `5060` |
| Transport | Protokol | `UDP` |
| Kapı DTMF Kodu | Kapıyı açacak kod | `#` veya `1` |
| Otomatik Yanıt | Saniye (0=kapalı) | `5` |

---

## 🔔 Çağrı Akışı

```
Fanvil i66 kapı zilini basar
        ↓
Android uyarı alır (LinphoneService)
        ↓
Kilit ekranı üzerinde IncomingCallActivity açılır
        ↓
Kullanıcı "Yanıtla" veya "Video" butonuna basar
        ↓
OngoingCallActivity açılır (Fanvil kamera görüntüsü)
        ↓
Kullanıcı "🔓 KAPIYI AÇ" butonuna basar
        ↓
DTMF kodu gönderilir → Kapı açılır!
        ↓
İsteğe bağlı: "📵 Kapat" ile çağrı sonlandırılır
```

---

## 📦 Kullanılan Kütüphaneler

- **Linphone SDK 5.3+** - SIP/VoIP motoru (PJSIP tabanlı)
- **AndroidX / Material Components** - UI
- **Kotlin Coroutines** - Asenkron işlemler

---

## 🏗️ Proje Yapısı

```
app/src/main/java/com/sipdoor/app/
├── SipDoorApplication.kt     # Application sınıfı, servis bağlama
├── LinphoneService.kt        # 🎯 SIP core, çağrı yönetimi (Foreground Service)
├── SipPreferences.kt         # Ayar okuma/yazma helper
├── MainActivity.kt           # Ayarlar ekranı
├── IncomingCallActivity.kt   # Gelen çağrı ekranı
├── OngoingCallActivity.kt    # Aktif çağrı + video + kapı açma
└── BootReceiver.kt           # Cihaz açılışında servis başlatma
```

---

## ⚠️ Önemli Notlar

### İzinler
Uygulama ilk açılışta şu izinleri ister:
- **Mikrofon** - ses için zorunlu
- **Kamera** - görüntülü arama için
- **Bildirimler** - gelen çağrı bildirimi için (Android 13+)

### Batarya Optimizasyonu
Android bazı markalarda arka plan uygulamaları öldürebilir.
Ayarlar > Uygulamalar > SipDoor > Batarya > "Kısıtlama Yok" yapın.

### Fanvil i66 Ayarları
Fanvil'de kayıtlı bir dahili oluşturun ve bu uygulamanın dahilisini
"kapı açıldığında ara" listesine ekleyin.

### DTMF Kodu
Fanvil i66'nın kapı röle kodunu öğrenmek için cihazın web arayüzüne
gidin: `Ayarlar > Kapı Ayarları > Röle Kodu`

---

## 🔧 Sorun Giderme

**SIP kaydı başarısız:**
- Domain/IP ve port doğru mu?
- UDP trafiği firewall'da açık mı?
- Kullanıcı adı ve şifre doğru mu?

**Çağrı alınamıyor:**
- Batarya optimizasyonu kapalı mı?
- Uygulama arka planda çalışıyor mu?

**DTMF çalışmıyor:**
- Fanvil'de DTMF modu RFC2833 mi?
- Çağrı bağlandıktan sonra mı gönderiyorsunuz?

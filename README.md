# Güvenli Hat

Yaşlıları telefon dolandırıcılığından korumak için Android uygulaması. Sürüm 0.1, 1. aşama.

## Ne yapar?

| Özellik | Nasıl çalışır |
|---|---|
| **Bilinmeyen arama filtresi** | Rehberde olmayan ve gizli numaralardan gelen aramalar telefonu hiç çaldırmadan reddedilir. |
| **Görüşme süre sınırı** | Rehberde olmayan biriyle yapılan görüşme seçilen süre dolunca (5/10/15/20/30 dk) otomatik kesilir. |
| **Güvenli kişiyi arama** | Görüşme kesilince ekranda büyük bir uyarı çıkar ve güvenli kişiler sırayla aranır. Biri açmazsa 45 saniye sonra sıradakine geçilir. |
| **Geri arama engeli** | Kesilen numara bir süre (varsayılan 1 saat) ne arayabilir ne de aranabilir. Dolandırıcıların "hat kesilirse beni hemen geri ara" taktiğine karşı. |
| **Aileye bildirim** | Kuruluma göre SMS ya da Telegram ile şunlar bildirilir: engellenen aramalar, rehber dışı bir numaranın aranması, uzun görüşme (sürenin yarısında), kesilen görüşme, ayarların açılması. |
| **"Rehberde yok" uyarısı** | Korunan kişi kayıtlı olmayan bir numarayı aramak isterse önce uyarılır. |
| **PIN koruması** | Ayarlar aile PIN'i olmadan açılamaz. |
| **Yaşlı dostu arayüz** | Büyük düğmeli ana ekran, rehber, son aramalar ve tuş takımı. Görüşme sırasında hoparlör, sessiz ve tuşlar düğmeleri var. |

Acil numaralar (112, 155, 110...) aranırken asla kesilmez. 112 arandıktan sonraki 1 saat boyunca filtre gevşer, böylece geri dönüş araması engellenmez.

---

## APK nasıl elde edilir?

### Yol A: GitHub ile (bilgisayara bir şey kurmadan)

1. github.com'da ücretsiz hesap açın.
2. Sağ üstteki **+ → New repository** ile depo oluşturun. Adı `GuvenliHat` olsun, **Private** seçin.
3. Depo sayfasında **"uploading an existing file"** bağlantısına tıklayın. Bu zip'in içindeki **bütün dosya ve klasörleri** sürükleyip bırakın, sonra **Commit changes** deyin.
   - Önemli: `.github` klasörü gizli klasördür. Bilgisayarınızda görünmüyorsa gizli dosyaları gösterin, yoksa otomatik derleme çalışmaz.
4. **Actions** sekmesine geçin. "APK oluştur" işi kendiliğinden başlar ve 3-5 dakika sürer. Başlamazsa **Run workflow** düğmesine basın.
5. İş yeşil tik alınca işe tıklayın. Sayfanın altındaki **Artifacts** bölümünden `GuvenliHat-apk` dosyasını indirin. Zip'in içinden APK çıkar.

### Yol B: Android Studio ile

Android Studio'yu kurun, **Open** ile bu klasörü açın, **Build → Build APK(s)** deyin.

---

## Annenin telefonuna kurulum

1. APK'yı telefona atın (WhatsApp'tan kendinize göndermek kolay olur) ve açın. "Bilinmeyen kaynaklardan yüklemeye izin ver" sorusuna izin verin.
   - Xiaomi "güvenlik taraması" uyarısı gösterebilir. Uygulamayı kendiniz derlediğiniz için devam edebilirsiniz.
2. Güvenli Hat'ı açın, **Ayarlar (aile için)** düğmesine basın ve bir **aile PIN'i** belirleyin.
3. **1. Kurulum** bölümündeki kırmızı ✗ işaretlerinin hepsini yeşile çevirin:
   - Varsayılan telefon uygulaması (en önemlisi)
   - İzinler
   - Sessiz engelleme (Android 10 ve üstü)
   - Pil kısıtlaması
   - **Xiaomi'ye özel:** "Otomatik başlatma" açık olmalı. Ayrıca "Diğer izinler" altında **Kilit ekranında göster** ve **Arka planda açılır pencere** açık olmalı. Bunlar yapılmazsa gelen arama ekranı açılmayabilir.
4. **WhatsApp:** WhatsApp → Ayarlar → Gizlilik → Aramalar → **Bilinmeyen arayanları sessize al** seçeneğini açın.
5. Güvenli kişileri (en çok 3), süre sınırını ve bildirim yöntemini seçin. **Deneme mesajı gönder** ile bildirimi test edin.
6. Ana ekrandaki eski "Telefon" simgesini kaldırın, yerine Güvenli Hat simgesini koyun.

### Telegram kurulumu (seçilirse)

1. Telegram'da **@BotFather**'a yazın ve `/newbot` gönderin. Bota bir ad verin. Size bir **token** verecek (`123456789:AA...` gibi).
2. Bir aile grubu kurun, botu gruba ekleyin ve grupta `/start` yazın.
3. Uygulamada token'ı yapıştırın, **Aile grubunu bul** düğmesine basın ve grubu seçin.

---

## Deneme planı (annene vermeden önce)

Kendi telefonuna kurup ikinci bir telefonla dene:

- [ ] Rehberde **olmayan** bir numaradan ara: telefon çalmamalı, aileye bildirim gelmeli.
- [ ] Rehberde **olan** bir numaradan ara: normal çalmalı, açılıp kapanabilmeli.
- [ ] Filtreyi kapat, süreyi **1 dakika (deneme)** yap ve rehber dışı numarayla konuş: 1 dakikada kesilmeli, güvenli kişi aranmalı, bildirim gelmeli.
- [ ] Kesilen numarayı hemen geri aramayı dene: engellenmeli.
- [ ] Ekran kilitliyken gelen arama ekranı açılıyor mu?
- [ ] Çift hat (SIM) varsa hat seçimi çıkıyor mu?
- [ ] Denemeden sonra süreyi tekrar 10 dakikaya al.

---

## Bilinen sınırlar

- **WhatsApp aramaları** uygulama tarafından kesilemez veya süre sınırına sokulamaz. Koruma WhatsApp'ın kendi "bilinmeyenleri sessize al" ayarına dayanıyor.
- **Arayana sesli mesaj** henüz yok. Bu 3. aşamada operatörün "meşgulse yönlendir" ve sesli mesaj karşılama anonsu ile yapılacak.
- Güvenli kişinin **sesli mesaj kutusu** açılırsa uygulama aramanın cevaplandığını sanar ve zincir orada durur.
- Gönderim **SMS** ise mesajlar korunan kişinin hattından gider. Türkçe karakterler sadeleştirilir (ş→s gibi), böylece her uyarı tek SMS olur.
- Sistem tarafından **engellenen** aramalar "Son aramalar" listesinde bilerek gösterilmez. Amaç, korunan kişinin merak edip geri aramasını önlemek. Aile bunları bildirimden görür.

## Teknik

- Kotlin, Android 7.0 (API 24) ve üstü, harici kütüphane yok
- `InCallService` (varsayılan telefon uygulaması) + `CallScreeningService` (sessiz engelleme)
- Bütün kod `app/src/main/java/com/guvenlihat/` altında. Asıl mantık `CallManager.kt` içinde.
- APK sabit bir anahtarla imzalanıyor (`app/debug.keystore`), böylece yeni sürümler eskisinin üzerine kurulabiliyor. Depoyu **gizli (private)** tutun.

# v2ray Dikec Edition — cihaza özel, güç odaklı v2ray istemcisi

**Tarih:** 2026-08-02
**Durum:** Onaylandı, uygulamaya hazır
**Paket:** `com.kaandikec.u30prov2ray`
**Uygulama adı:** v2ray Dikec Edition
**Repo:** `u30prov2ray` (kullanıcının GitHub hesabında, yeni)

## Amaç

240×240 yuvarlak ekranlı MU5358 saat için sıfırdan bir v2ray istemcisi. **Birinci öncelik güç
tüketimi ve verimlilik**; ikinci öncelik temel kullanım kolaylığı (içe aktar, profil değiştir,
bağlan/kes).

Bu uygulama, `feat/compact-round-screen` dalındaki v2rayNG fork'unun **yerini alır**. Fork
olduğu yerde dondurulur; kompakt mod ve yamalı core orada çalışır durumda kalır ve referans
olarak durur.

## Hedef cihaz

| Özellik | Değer |
|---|---|
| Model | MU5358 / SHENQIJIYUAN |
| Android | 15 (SDK 35), `ro.build.characteristics=default` — Wear OS değil |
| Ekran | 240×240 px @ 160dpi → 240dp; config `sw240dp-...-notround` |
| ABI | arm64-v8a |
| Not | Panel fiziksel yuvarlak ama sistem `notround` bildiriyor |

## Mimari kararı: neyi yazıyoruz, neyi taşıyoruz

**Protokol katmanı yeniden yazılmayacak.** Gerekçe: `fmt/` (URL ayrıştırma) ve `core/`
(ProfileItem → Xray JSON) çoklu protokol için binlerce satır ve sessiz, ince hatalara açık.
Bu sınıftan bir hata (`allowInsecure` zinciri) bu projede saatlerce zaman yedi.

| Katman | Karar |
|---|---|
| `fmt/` — protokol URL ayrıştırma/serileştirme | **Taşınır** (v2rayNG'den, GPL-3.0 uyumlu) |
| `core/` — config üretimi, outbound builder | **Taşınır**, kullanılmayan özellikler budanır |
| `service/` — VpnService + tun2socks | **Taşınır**, sadeleştirilir |
| `dto/`, `handler/MmkvManager` | **Taşınır** |
| `compose/CompactRound.kt` — dairesel güvenli alan, kiriş matematiği | **Taşınır** (12 birim testiyle ve gerçek cihazda doğrulanmış) |
| Kompakt ekranlar (ana ekran, profil listesi, menü) | **Taşınır ve sadeleştirilir** |
| v2rayNG'nin telefon UI'ı, ayar ekranları, drawer, sekmeler | **Gelmez** |
| Uygulama iskeleti, arka plan işleri | **Sıfırdan** (çoğu hiç olmayacak) |

Yani v2rayNG'nin telefon arayüzünden, ayar ekranlarından ve arka plan makinesinden hiçbir şey
gelmiyor. Gelen tek UI parçası, bu projede yazılıp gerçek MU5358'de doğrulanmış kompakt ekran
seti — onu yeniden türetmek, ölçülerek çözülmüş geometriyi (35dp inset, 154dp kiriş genişliği,
168dp yükseklik bütçesi) sıfırdan bulmak anlamına gelirdi.

## Kapsam — ne olacak

Tek ekran seti, yalnızca kompakt yuvarlak:

- **Ana ekran:** durum, dairesel bağlan/kes butonu, seçili profil adı, menü
- **Profil listesi:** dokun-seç, kiriş genişliğinde satırlar
- **Menü:** panodan içe aktar, dosyadan içe aktar, sertifika sabitle, log

Telefon UI'ı, drawer, sekmeler, arama **yok**.

### İçe aktarma yolları

1. **Pano** — mevcut `AngConfigManager.importBatchConfig`
2. **Paylaş menüsü / `v2rayng://` URL scheme** — yeni şema: `u30prov2ray://install-config`
3. **`/sdcard/v2ray/config.txt`** (yoksa `configs.txt`) — satır satır

`config.txt` içe aktarımı üç seçenekli onay sorar:

| Seçenek | Davranış |
|---|---|
| **Ekle** | `append=true`, mevcut profiller korunur |
| **Hepsini değiştir** | **Tüm** profiller silinir (abonelik grupları dahil), sonra dosyadakiler eklenir |
| **İptal** | Hiçbir şey yapılmaz |

Dosya yok / boş / izin yok durumları ayrı ayrı ve açık mesajla bildirilir.

**İzin:** `/sdcard` altındaki rastgele yol Android 11+'ta `MANAGE_EXTERNAL_STORAGE` ister.
Manifest'e eklenir. Magisk modülü bunu otomatik verir; modülsüz kullanımda kullanıcı "Tüm
dosyalara erişim" ekranına yönlendirilir.

## Kapsam — ne olmayacak, ve neden

Güç tasarrufunun asıl kaynağı bu liste. İlk iki kalem v2rayNG kodunda **ölçülerek** tespit
edildi, tahmin değil:

| Çıkarılan | Ölçülen / gerekçe |
|---|---|
| `SubscriptionUpdater` + WorkManager'ın tamamı | `PeriodicWorkRequest` + `RemoteWorkManager`; periyodik uyandırma, telsizi açar |
| Hız bildirimi döngüsü | `NotificationManager.kt:55` — bağlıyken **her 3 sn** IPC + bildirim güncelleme; saatte ~1200 uyandırma |
| Ping / gecikme testi, gerçek zamanlı trafik sayacı | Sürekli IPC ve UI yenilemesi |
| QS tile, Tasker, widget, dialer servisleri | Gereksiz receiver/servis yükü |
| Per-app proxy, routing editörü, backup/WebDAV, güncelleme kontrolü | Kod ve APK sadeliği |

**Bilerek korunanlar (kullanıcı kararı):** geo yönlendirme dosyaları (`geoip.dat`,
`geosite.dat`) ve çoklu protokol desteği.

Bildirim **sabit** kalır: profil adı ve bağlı/değil durumu; periyodik güncelleme yok.

## Güç ölçümü — iddia değil, sayı

"Verimli" ölçülmezse laftır. Kabul kriteri:

1. Referans ölçüm: fork (v2rayNG) ile 1 saat bağlı bekleme
2. Aynı koşulda yeni uygulama
3. Karşılaştırılan metrikler: `dumpsys batterystats` içindeki uyandırma sayısı, CPU süresi,
   telsiz aktiflik süresi; `dumpsys power` wakelock kayıtları
4. Sonuç README'ye **sayıyla** yazılır

Ölçüm yapılmadan "düşük tüketim" iddiası README'ye girmez.

## Yamalı core bağımlılığı

Uygulama, `allowInsecure`'ü geri getiren yamalı `libv2ray.aar`'a bağlıdır. Yama ve derleme
tarifi mevcut fork'un `patches/` dizinindedir ve iki Xray sürümünde doğrulanmıştır. AAR 58MB
olduğu için repoya konmaz; CI ya da yerel derleme tarifiyle üretilir.

## Lisans

v2rayNG **GPL-3.0**. Protokol katmanı taşındığı için bu proje de GPL-3.0 olmak ve kaynağını
yayımlamak zorundadır. GitHub'a açık repo olarak çıkılacağı için bu zaten sağlanıyor.
`LICENSE` ve kaynak atıfı eklenecek.

## Magisk modülü — ayrı ve sonraki adım

Uygulama çalışır hale geldikten sonra, ayrı bir plan olarak:

- APK kurulumu
- `appops set com.kaandikec.u30prov2ray MANAGE_EXTERNAL_STORAGE allow`
- `settings put global always_on_vpn_app com.kaandikec.u30prov2ray`
- `settings put global always_on_vpn_lockdown 1` — kill switch
- `/sdcard/v2ray/config.txt` yoksa örnek dosya

Always-on VPN, OS seviyesinde otomatik bağlanmayı da sağlar; uygulamada ayrı bir "boot'ta
başlat" ayarına gerek yoktur.

**Bilinen risk:** Bu oturumda gözlendi — `settings put global always_on_vpn_app` **tek başına
VPN onayını vermedi**; onay ancak sistem Ayarlar arayüzünden geldi. Format sonrası normal onay
diyaloğu düzgün çalıştı. Modül root olduğu için `appops` ile denenecek, ama **gerçek cihazda
doğrulanmadan "otomatik" diye belgelenmeyecek.** Çalışmazsa modül, kurulum sonrası
"uygulamayı bir kez açıp onay verin" talimatını gösterir.

## Riskler ve kabul edilen ödünler

- **Taşınan kod v2rayNG'ye bağımlılık yaratır.** Upstream'de protokol değişirse elle taşımak
  gerekir. Kabul: alternatifi binlerce satır protokol kodunu yeniden türetmek.
- **Kompakt UI yalnızca bu cihaz sınıfı için.** Telefonda çalışır ama optimize değildir.
- **Geo dosyaları korunduğu için APK ~50MB kalır.** Kullanıcı kararı; çıkarılsaydı ~22MB olurdu.
- **Dairesel kırpılma hâlâ hesaplanarak yönetiliyor**, sistem inset vermiyor. Fork'ta çözülmüş
  `circularStrictSafeArea` ve kiriş matematiği taşınacak.

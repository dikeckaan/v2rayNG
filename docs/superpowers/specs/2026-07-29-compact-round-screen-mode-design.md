# Kompakt Yuvarlak Ekran Modu + allowInsecure Koruması

**Tarih:** 2026-07-29
**Durum:** Onaylandı, uygulamaya hazır

## Amaç

240×240 yuvarlak ekranlı bir Android cihazda v2rayNG'yi **temel kullanım** için kullanılabilir hale getirmek:
profil içe aktarma, profiller arası kolay geçiş, bağlan/kes. Aynı zamanda `allowInsecure`
parametresinin desteğini kalıcı olarak sürdürmek — upstream bu özelliği Ağustos 2026'da kaldırmayı
planlıyor ve fork'un varlık sebebi budur.

## Hedef cihaz

adb ile (`192.168.0.1:5555`) doğrulanan gerçek değerler:

| Özellik | Değer | Sonuç |
|---|---|---|
| Model | MU5358 / SHENQIJIYUAN | — |
| Android | 15, SDK 35 | `minSdk 24` yeterli, değişmez |
| `ro.build.characteristics` | `default` | Wear OS **değil** → Material3 + `VpnService` sorunsuz |
| Ekran | 240×240 px @ 160 dpi | Logical **240dp × 240dp** |
| Config | `sw240dp-w240dp-h240dp-small-notlong-**notround**-port-mdpi` | ⚠️ Panel fiziksel yuvarlak ama sistem **düz kare** bildiriyor |
| ABI | `arm64-v8a, armeabi-v7a, armeabi` | — |
| `/dev/tun` | var | VpnService çalışır |
| Kamera | var | (bu kapsamda kullanılmıyor) |

**Kritik sonuç:** Sistem `notround` bildirdiği için `Configuration.isScreenRound()` `false` döner,
`-round` kaynak niteleyicisi eşleşmez ve sistem yuvarlak inset (`WindowInsets.waterfall`) sağlamaz.
Dairesel güvenli alan **elle hesaplanmak zorundadır**.

## Kapsam

**Dahil:** Panodan içe aktarma, Paylaş menüsü / URL scheme ile içe aktarma, profil listesi ve profil
değiştirme, bağlan/kes, dairesel güvenli alan, `allowInsecure` korunması ve regresyon testleri.

**Hariç:** QR kod tarama, abonelik URL'i ekleme ekranı, routing, per-app proxy, sunucu düzenleme
formlarının özel tasarımı. Bunlar erişilebilir kalır (Katman B) ama optimize edilmez.

## Mimari

Tek APK, mevcut `fdroid` / `playstore` flavor'ları korunur. **Yeni product flavor yok.**
`minSdk 24`, `compileSdk 37` değişmez.

### Etkinleştirme

Ekran boyutundan otomatik tespit:

```
isCompactRound = (smallestScreenWidthDp <= 280 && |screenWidthDp - screenHeightDp| <= 8)
                 || configuration.isScreenRound
```

İlk koşul bu cihaz için çalışan koşuldur (`sw240dp`, 240×240). İkincisi yuvarlaklığı doğru bildiren
cihazlar için yedektir. Telefon (`sw360dp+`) hiçbir koşulu sağlamaz → davranış birebir aynı kalır.

### Enjeksiyon noktaları

Yalnızca iki dosyada yapısal değişiklik gerekir:

1. **`compose/Theme.kt` → `AppTheme`** — zaten `CompositionLocalProvider` + `Box(fillMaxSize)`
   sarmalayıcısına sahip. `LocalCompactRound` burada sağlanır. Uygulamadaki **her** Activity
   `BaseComponentActivity.onCreate` → `AppTheme { ScreenContent() }` üzerinden geçtiği için
   tüm ekranlar otomatik kapsanır.

2. **`ui/main/MainActivity.kt` → `ScreenContent()`** — `MainScreen(...)` çağrısı koşullu hale gelir:
   kompakt modda `CompactMainScreen(...)`. Mevcut `onAction` yönlendirme bloğu **aynen** kullanılır.

`MainViewModel`, `MainAction`, `MainContract`, `MainRepository`, `core/`, `fmt/`, `handler/`
katmanlarına **hiç dokunulmaz**.

## Dairesel yerleşim

R = 120dp, merkez (120, 120).

### `strict` modu

İçe çizilen en büyük kare: kenar = R√2 ≈ **170dp**, her yönden **35dp** inset.
Dairenin %64'ünü kullanır. Rastgele dikdörtgen içerik (dialoglar, formlar, ayarlar) için.

Daha ılımlı bir inset **yetersizdir**: 20dp inset'te köşe noktası (20,20), merkeze uzaklığı
√(100² + 100²) ≈ 141dp > 120dp → hâlâ daire dışında, fiziksel olarak görünmez. Köşelerin
garanti görünmesi için 35dp şarttır.

### `chord` modu

Her satırın yatay inset'i dikey konumundan hesaplanır: yarı genişlik = √(R² − d²),
burada d = satırın merkeze dikey uzaklığı.

| d (merkeze uzaklık) | Kullanılabilir genişlik |
|---|---|
| 0dp | 240dp |
| 60dp | 208dp |
| 100dp | 133dp |
| 110dp | 96dp |

Dairenin tamamına yakınını kullanır — `strict`'e göre **~1.6× daha fazla alan**.
Özel yazılan ana ekran ve profil listesinde kullanılır.

Uygulama: `Modifier.circularSafeArea(mode)` ve chord için özel bir `Layout`.

**Hangi mod nerede uygulanır:** `AppTheme`, kompakt modda `content()`'i **varsayılan olarak `strict`**
ile sarar — yani hiçbir değişiklik yapılmayan tüm ekranlar (Katman B) otomatik güvenli hale gelir.
Katman A ekranları (`CompactMainScreen`, `CompactProfileList`) bu varsayılandan **açıkça muaf tutulur**
(`LocalCompactRound` üzerinden okunan bir "kendi güvenli alanını yönetir" bayrağıyla) ve kendi
`chord` yerleşimlerini uygular. Aksi halde iki inset üst üste binerdi.

## Kullanıcı arayüzü

### Katman A — sıfırdan yazılan (asıl kullanım)

**`CompactMainScreen`** — `MainScreen`'in kompakt moddaki karşılığı.
`TopAppBar` + `BottomBar` + `ModalNavigationDrawer` kaldırılır (240dp'de üçü birden ~80dp tüketiyordu).

Dikey sıra (yukarıdan aşağıya, tek `Column`, dikey ortalanmış):

1. Durum metni → `uiState.statusText`
2. **110dp dairesel bağlan/kes butonu** → `MainAction.ToggleService`
3. Seçili profil adı, dokunma → `CompactProfileList`
4. `≡` ikonu → `CompactMenuSheet`

**`CompactProfileList`** — tam ekran, chord-inset satırlar, **48dp** dokunma hedefi.
Dokunma → `MainAction.SelectServer`. Bu, mevcut `MainActivity.setSelectServer()` üzerinden
servis çalışıyorsa **otomatik yeniden başlatma** yapar; istenen "kolay profil değiştirme"
davranışı budur.

**`CompactMenuSheet`** — `Panodan içe aktar` (`MainAction.ImportClipboard`),
`Abonelikleri güncelle`, `Ayarlar`, `Log`.

### Katman B — otomatik uyarlanan

Ayarlar, abonelik, sunucu düzenleme vb. `strict` güvenli alan + sıkıştırılmış boşluk alır.
Estetik hedef yok; **açılabilir ve kullanılabilir** olmaları yeterli.

### Değişiklik gerektirmeyen kısım

`AndroidManifest.xml:176-192` — `UrlSchemeActivity` zaten hem `ACTION_SEND text/plain`
(Paylaş menüsü) hem `v2rayng://install-config` / `install-sub` intent filtrelerine sahip.
`ScreenContent()` gövdesi boş, yani hiç UI çizmiyor: içe aktarır, toast atar, `MainActivity`'yi
açar. Seçilen iki içe aktarma yolundan biri **hazır durumda**; yalnızca cihazda doğrulanacak.

## allowInsecure koruması

### Mevcut zincir (sağlam, fonksiyonel engel yok)

```
fmt/FmtBase.kt:77          insecure | allowInsecure | allow_insecure  (parse, 3 anahtar)
fmt/FmtBase.kt:119-123     serialize: "insecure" + "allowInsecure" birlikte yazılır
dto/entities/ProfileItem   .insecure: Boolean?
core/CoreOutboundBuilder.kt:541  insecure == true && pinnedCA256.isNullOrEmpty()
core/CoreOutboundBuilder.kt:555  -> tlsSettings.allowInsecure
dto/entities/SubscriptionItem.kt:14  .allowInsecureUrl  (abonelik indirme yolu)
handler/AngConfigManager.kt:554      allowInsecureUrl kullanımı
```

Şu anki tek kısıtlama `core/CoreServiceManager.kt:166-167` — aynı deprecation toast'ının
**iki kez üst üste** gösterilmesi. Fonksiyonel bir blok değil.

### Yapılacaklar

1. **Deprecation uyarısını kaldır.** `CoreServiceManager.kt:166-167`'deki çift
   `toastError(R.string.toast_allow_insecure_deprecated)` çağrısı silinir. Gerekçe: 240dp ekranda
   iki üst üste toast arayüzün tamamını kapatıyor; ayrıca fork bu özelliği kasten sürdürdüğü için
   uyarı çelişkili. String kaynakları (`values`, `values-zh-rCN`, `values-zh-rTW`) yerinde bırakılır
   — kullanılmayan string zararsızdır ve upstream merge'lerinde çakışma üretmez.

2. **Regresyon testleriyle çivile.** Asıl risk bugünkü kod değil, **ileride upstream'den merge
   alırken özelliğin sessizce düşmesi**. `app/src/test/java/` + JUnit 4 + Mockito zaten kurulu.
   Test kapsamı:
   - `FmtBase` üç sorgu anahtarını da (`insecure`, `allowInsecure`, `allow_insecure`)
     `=1` için `true`, `=0` için `false` olarak parse ediyor
   - Serialize ederken hem `insecure` hem `allowInsecure` anahtarlarını yazıyor
   - `CoreOutboundBuilder.populateTlsSettings`, `insecure=true` + boş `pinnedCA256` iken
     `tlsSettings.allowInsecure == true` üretiyor
   - `pinnedCA256` doluyken `allowInsecure == false` (mevcut ve doğru davranış korunuyor)
   - `SubscriptionItem.allowInsecureUrl` yolu korunuyor

   Bir merge bunu bozarsa test yüksek sesle kırılır.

3. **`FORK.md`** — upstream'den tüm sapmaların listesi, rebase'lerin bilinçli olması için.

4. **Dış risk, açıkça kayda geçirilir.** `allowInsecure`'ü asıl kaldırabilecek yer uygulama değil,
   **Xray-core**'un kendisidir. Core, `app/libs/*.aar` olarak yerelde derleniyor
   (`AndroidLibXrayLite` submodule) — yani hangi sürümün gönderileceğine bu depo karar veriyor.
   Mitigasyon uygulama kodunda değil, **submodule commit'ini sabitlemektedir**. `FORK.md`'ye yazılır.

## Doğrulama

**Birim testleri (JVM):**
```sh
cd V2rayNG && ./gradlew test
```

**Derleme:**
```sh
cd V2rayNG && ./gradlew assemblePlaystoreDebug
```

**Cihazda (adb, `192.168.0.1:5555`):**
1. APK kurulur
2. 240×240 ekran görüntüsü alınır, dairesel kırpılma kontrol edilir
3. Ana ekran: bağlan/kes butonu çalışıyor, durum metni güncelleniyor
4. Profil listesi: dokunma ile profil değişiyor, servis çalışıyorsa yeniden başlıyor
5. Panodan içe aktarma: `adb shell` ile panoya `vless://` linki konur, menüden içe aktarılır
6. Paylaş menüsü / URL scheme: `adb shell am start -a android.intent.action.VIEW -d "v2rayng://install-config?url=..."`
7. `allowInsecure` içeren bir profil içe aktarılır, seçilir, bağlanılır — deprecation toast'ı
   **çıkmamalı**, bağlantı kurulmalı

## Riskler ve kabul edilen ödünler

- **`strict` modda 170dp'lik kullanılabilir alan çok dar.** Katman B ekranları (özellikle uzun
  sunucu formları) kullanılabilir ama konforlu olmayacak. Bu bilinçli bir ödün: kullanıcının
  belirttiği kapsam içe aktarma + profil değiştirme + bağlan/kes ile sınırlı.
- **Tespit yanılabilir.** 280dp eşiği başka küçük cihazlarda da tetiklenebilir. Kullanıcı otomatik
  tespiti seçtiği için elle geçiş anahtarı eklenmiyor; sorun çıkarsa eşik ayarlanır.
- **Upstream merge çatışması.** `Theme.kt` ve `MainActivity.kt` fork'ta değiştiği için bu iki dosyada
  merge çatışması beklenmelidir. Değişiklikler bilinçli olarak minimum tutuluyor (her dosyada
  birkaç satır) ki çatışma çözümü kolay olsun.

# Kompakt Yuvarlak Ekran Modu — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 240×240 yuvarlak ekranlı bir Android 15 cihazda v2rayNG'yi temel kullanım (içe aktar / profil değiştir / bağlan-kes) için kullanılabilir hale getirmek ve `allowInsecure` desteğini kalıcı olarak güvenceye almak.

**Architecture:** Tek APK, yeni flavor yok. `Configuration`'dan ekran boyutu tespit edilir, `AppTheme` içinde `LocalCompactRound` sağlanır ve dokunulmayan tüm ekranlar otomatik olarak dairesel güvenli alana çekilir. `MainActivity.ScreenContent()` kompakt modda `MainScreen` yerine yeni `CompactMainScreen`'i çizer; `MainViewModel` / `MainAction` / `core` / `fmt` katmanları değişmez. `allowInsecure` regresyon testleriyle çivilenir.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose + Material3, AGP 9.2.1, Gradle Kotlin DSL, JUnit 4 + Mockito, MMKV.

**Spec:** `docs/superpowers/specs/2026-07-29-compact-round-screen-mode-design.md`

## Global Constraints

- `minSdk 24`, `targetSdk 37`, `compileSdk 37` — **değişmeyecek**
- Yeni product flavor **eklenmeyecek**; mevcut `fdroid` / `playstore` korunacak
- `MainViewModel`, `MainContract`, `MainRepository`, `MainDataSource`, `core/`, `fmt/`, `handler/` paketlerine **davranış değişikliği yapılmayacak**
- Telefon davranışı (`sw360dp+`) **birebir aynı kalacak** — kompakt mod yalnızca tespit tetiklendiğinde devreye girer
- Yeni string kaynağı **eklenmeyecek**; yalnızca mevcut `R.string` / `R.drawable` değerleri kullanılacak (aşağıda her biri adıyla verildi)
- Depoda `origin` = `git@github.com:dikeckaan/v2rayNG.git`, `upstream` = 2dust. **Asla `upstream`'e push edilmeyecek.**
- Çalışma dalı: `feat/compact-round-screen`
- Build: `cd V2rayNG && ./gradlew assemblePlaystoreDebug` — Test: `cd V2rayNG && ./gradlew test`

## Spec'ten sapma (bilinçli)

Spec, `chord` modu için **dinamik özel bir `Layout`** öngörüyordu. Bu planda chord matematiği korunuyor ama **statik** olarak uygulanıyor: profil listesi, satır genişliğini içerik bandının kenarındaki kirişten bir kez hesaplar. Gerekçe: dinamik chord layout, kaydırma sırasında her karede yeniden ölçüm gerektirir ve 48dp'lik satırlarda kazanç ~154dp yerine merkezde ~230dp'dir; buna karşılık `strict` insetle karşılaştırıldığında statik chord zaten aynı mertebede sonuç veriyor (35dp inset ↔ ±85dp bant ↔ 170dp genişlik). Karmaşıklık kazancı hak etmiyor. `chordHalfWidth()` yine de gerçek çalışma zamanı hesabında kullanılıyor ve test ediliyor.

## Dosya yapısı

**Oluşturulacak:**
- `V2rayNG/app/src/main/java/com/v2ray/ang/compose/CompactRound.kt` — tespit, `LocalCompactRound`, kiriş matematiği, `circularStrictSafeArea()` modifier'ı
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt` — kompakt rota anahtarı + ana ekran
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactProfileList.kt` — profil listesi
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMenuScreen.kt` — tam ekran menü
- `V2rayNG/app/src/test/java/com/v2ray/ang/fmt/AllowInsecureFmtTest.kt`
- `V2rayNG/app/src/test/java/com/v2ray/ang/core/AllowInsecureOutboundTest.kt`
- `V2rayNG/app/src/test/java/com/v2ray/ang/compose/CompactRoundTest.kt`
- `FORK.md` (depo kökü)

**Değiştirilecek:**
- `V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt:165-168` — deprecation toast'ı kaldır
- `V2rayNG/app/src/main/java/com/v2ray/ang/compose/Theme.kt:148-182` — `AppTheme`'e kompakt mod + güvenli alan
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/base/BaseComponentActivity.kt:19-27` — `managesOwnCompactSafeArea` kancası
- `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt:99-124` — koşullu ekran + `handleAction` çıkarımı

---

## Task 1: allowInsecure korumasını çivile

Fork'un varlık sebebi. UI'dan tamamen bağımsız, önce bu yapılır.

**Files:**
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/fmt/AllowInsecureFmtTest.kt`
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/core/AllowInsecureOutboundTest.kt`
- Create: `FORK.md`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt:165-168`

**Interfaces:**
- Consumes: mevcut `FmtBase` (`open class`, `getItemFormQuery(config, queryParam)`, `getQueryDic(config)`), `CoreOutboundBuilder` (`object`, `populateTlsSettings(streamSettings, profileItem, sniExt)`), `ProfileItem.create(EConfigType)`, `AppConfig.TLS = "tls"`
- Produces: sonraki task'ların kullandığı bir şey yok — bağımsız güvenlik ağı

- [ ] **Step 1: `FmtBase` için başarısız testi yaz**

`V2rayNG/app/src/test/java/com/v2ray/ang/fmt/AllowInsecureFmtTest.kt`:

```kotlin
package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fork guard: allowInsecure must survive upstream merges.
 *
 * Upstream announced (toast_allow_insecure_deprecated) that skipping certificate
 * verification would be disabled in August 2026. This fork deliberately keeps it.
 * If a merge removes the parse/serialize support, these tests must fail loudly.
 */
class AllowInsecureFmtTest {

    private val fmt = FmtBase()

    private fun parse(vararg pairs: Pair<String, String>): ProfileItem {
        val config = ProfileItem.create(EConfigType.VLESS)
        fmt.getItemFormQuery(config, mapOf("security" to AppConfig.TLS, *pairs))
        return config
    }

    @Test
    fun test_parse_insecureKey_enablesInsecure() {
        assertEquals(true, parse("insecure" to "1").insecure)
    }

    @Test
    fun test_parse_allowInsecureKey_enablesInsecure() {
        assertEquals(true, parse("allowInsecure" to "1").insecure)
    }

    @Test
    fun test_parse_allowUnderscoreInsecureKey_enablesInsecure() {
        assertEquals(true, parse("allow_insecure" to "1").insecure)
    }

    @Test
    fun test_parse_zeroValue_disablesInsecure() {
        assertEquals(false, parse("insecure" to "0").insecure)
        assertEquals(false, parse("allowInsecure" to "0").insecure)
        assertEquals(false, parse("allow_insecure" to "0").insecure)
    }

    @Test
    fun test_parse_absentKey_defaultsToFalse() {
        assertEquals(false, parse().insecure)
    }

    @Test
    fun test_serialize_emitsBothInsecureAndAllowInsecureKeys() {
        val config = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = true
        }

        val dic = fmt.getQueryDic(config)

        assertEquals("1", dic["insecure"])
        assertEquals("1", dic["allowInsecure"])
    }

    @Test
    fun test_serialize_emitsZeroWhenInsecureDisabled() {
        val config = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = false
        }

        val dic = fmt.getQueryDic(config)

        assertEquals("0", dic["insecure"])
        assertEquals("0", dic["allowInsecure"])
    }

    @Test
    fun test_roundTrip_preservesInsecureFlag() {
        val original = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = true
        }

        val dic = fmt.getQueryDic(original)
        val reparsed = ProfileItem.create(EConfigType.VLESS)
        fmt.getItemFormQuery(reparsed, dic)

        assertEquals(true, reparsed.insecure)
    }
}
```

- [ ] **Step 2: Testi çalıştır, geçtiğini doğrula**

Run: `cd V2rayNG && ./gradlew test --tests "com.v2ray.ang.fmt.AllowInsecureFmtTest"`
Expected: **PASS** — mevcut kod bu davranışı zaten sağlıyor. Bu bir karakterizasyon testidir; amacı bugünkü bir hatayı yakalamak değil, gelecekteki bir regresyonu yakalamaktır. Eğer FAIL ederse `FmtBase.kt:76-81` ve `FmtBase.kt:119-124` incelenmeli — davranış beklenenden sapmış demektir.

- [ ] **Step 3: `CoreOutboundBuilder` için testi yaz**

`V2rayNG/app/src/test/java/com/v2ray/ang/core/AllowInsecureOutboundTest.kt`:

```kotlin
package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork guard: the insecure flag must reach the generated core config.
 *
 * ProfileItem.insecure -> tlsSettings.allowInsecure is the only path that actually
 * makes the core skip certificate verification. See AllowInsecureFmtTest for the
 * URL parse/serialize half of the same guarantee.
 */
class AllowInsecureOutboundTest {

    private fun tlsProfile(block: ProfileItem.() -> Unit = {}) =
        ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            // Non-empty sni: skips the Utils.isDomainName branch.
            sni = "example.com"
            // Non-empty finalMask: skips updateOutboundFragment() (CoreOutboundBuilder.kt:575),
            // whose first statement reads MMKV settings and would need an initialised Android
            // runtime. Packet fragmentation is unrelated to what these tests assert.
            finalMask = "fragment"
            block()
        }

    @Test
    fun test_insecureTrue_setsAllowInsecureOnTlsSettings() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = true }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertEquals(AppConfig.TLS, stream.security)
        assertNotNull(stream.tlsSettings)
        assertTrue(
            "allowInsecure must reach tlsSettings — this fork keeps the feature alive",
            stream.tlsSettings!!.allowInsecure
        )
    }

    @Test
    fun test_insecureFalse_leavesAllowInsecureOff() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = false }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertNotNull(stream.tlsSettings)
        assertFalse(stream.tlsSettings!!.allowInsecure)
    }

    @Test
    fun test_pinnedCertificateSuppressesAllowInsecure() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile {
            insecure = true
            pinnedCA256 = "0123456789abcdef"
        }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertNotNull(stream.tlsSettings)
        assertFalse(
            "A pinned certificate must win over allowInsecure",
            stream.tlsSettings!!.allowInsecure
        )
        assertEquals("0123456789abcdef", stream.tlsSettings!!.pinnedPeerCertSha256)
    }

    @Test
    fun test_serverNameIsCarriedThrough() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = true }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertEquals("example.com", stream.tlsSettings!!.serverName)
    }
}
```

- [ ] **Step 4: Testi çalıştır**

Run: `cd V2rayNG && ./gradlew test --tests "com.v2ray.ang.core.AllowInsecureOutboundTest"`
Expected: **PASS**. FAIL ederse `CoreOutboundBuilder.kt:541` (`allowInsecure` hesabı) ve `:555` (`TlsSettingsBean`'e aktarım) incelenmeli.

- [ ] **Step 5: Deprecation toast'ını kaldır**

`CoreServiceManager.kt` içinde şu blok **tamamen silinir** (165-168 civarı):

```kotlin
        if (config.insecure == true) {
            context.toastError(R.string.toast_allow_insecure_deprecated)
            context.toastError(R.string.toast_allow_insecure_deprecated)
        }
```

Yerine hiçbir şey konmaz. `R.string.toast_allow_insecure_deprecated` kaynağı `values`, `values-zh-rCN`, `values-zh-rTW` içinde **yerinde bırakılır** — kullanılmayan string zararsızdır ve upstream merge'lerinde gereksiz çakışma üretmez.

Silme sonrası `CoreServiceManager.kt`'de `toastError` import'unun hâlâ kullanılıp kullanılmadığı kontrol edilir; kullanılmıyorsa import da silinir.

- [ ] **Step 6: Derle ve tüm testleri çalıştır**

```bash
cd V2rayNG && ./gradlew test && ./gradlew assemblePlaystoreDebug
```
Expected: BUILD SUCCESSFUL, tüm testler PASS.

- [ ] **Step 7: `FORK.md` yaz**

Depo kökünde `FORK.md`:

```markdown
# Fork divergence

Fork of [2dust/v2rayNG](https://github.com/2dust/v2rayNG).
Remotes: `origin` = this fork, `upstream` = 2dust. **Never push to `upstream`.**

## 1. allowInsecure is kept alive

Upstream announced via `toast_allow_insecure_deprecated` that skipping certificate
verification would be disabled in August 2026. This fork deliberately keeps it.

Divergence:

- `core/CoreServiceManager.kt` — the deprecation toast (shown twice) was removed.
  The string resources are left in place to avoid pointless merge conflicts.

Guarded by tests — do not delete these without a deliberate decision:

- `app/src/test/java/com/v2ray/ang/fmt/AllowInsecureFmtTest.kt`
- `app/src/test/java/com/v2ray/ang/core/AllowInsecureOutboundTest.kt`

The code path that must keep working:

```
fmt/FmtBase.kt                    insecure | allowInsecure | allow_insecure  (parse)
fmt/FmtBase.kt                    serialize: "insecure" + "allowInsecure" together
dto/entities/ProfileItem.kt       .insecure: Boolean?
core/CoreOutboundBuilder.kt       populateTlsSettings -> tlsSettings.allowInsecure
dto/entities/SubscriptionItem.kt  .allowInsecureUrl  (subscription fetch)
```

**External risk — already realised, not hypothetical.** The component that actually
dropped `allowInsecure` is Xray-core, not this app. Commit `2c92339f9` (2026-01-31)
removed it from the TLS `.proto`, from the generated `config.pb.go`, and from the
runtime. `infra/conf/transport_security.go` now does:

```go
if c.AllowInsecure {
    return nil, errors.PrintRemovedFeatureError(`"allowInsecure"`, `"pinnedPeerCertSha256"(pcs) and "verifyPeerCertByName"(vcn)`)
}
```

That is a hard error: the core refuses to start. There is **no date gate in the core** —
the "August 2026" date in upstream's toast was v2rayNG's own client-side messaging.

This repo pins AndroidLibXrayLite `v26.7.19` -> Xray-core `v26.7.11`, stock, with no
`replace` directive. So on this codebase, enabling allowInsecure currently breaks the
profile outright.

Making the feature genuinely work again therefore requires patching the core: a fork of
Xray-core restoring the removed field, consumed through a fork of AndroidLibXrayLite via
a go.mod `replace`, built by that fork's GitHub Actions. That work lives in other
repositories and is tracked by its own plan.

The tests below still earn their keep under any outcome: they guard the app-side half of
the chain, which every option depends on.

The app already supports the official replacements — `pinnedPeerCertSha256` (`pcs`) and
`verifyPeerCertByName` (`vcn`) — and `ui/server/BaseServerActivity.kt:311` has a one-tap
button that fetches the server's certificate fingerprint via
`CertificateFingerprintManager.fetchForManualFill`.

## 2. Compact round-screen mode

Auto-enabled on small near-square screens (`sw <= 280dp`). Targets a 240x240 round
Android 15 device that reports `notround`, so the circular safe area is computed
manually. Phone behaviour is unchanged.

- `compose/CompactRound.kt` — detection, chord math, safe-area modifier (new)
- `compose/Theme.kt` — provides `LocalCompactRound`, applies the safe area
- `ui/base/BaseComponentActivity.kt` — `managesOwnCompactSafeArea` hook
- `ui/main/MainActivity.kt` — renders `CompactMainScreen` in compact mode
- `ui/main/compact/` — the compact screens (new)

See `docs/superpowers/specs/2026-07-29-compact-round-screen-mode-design.md`.
```

- [ ] **Step 8: Commit**

```bash
git add V2rayNG/app/src/test/java/com/v2ray/ang/fmt/AllowInsecureFmtTest.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/core/AllowInsecureOutboundTest.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt \
        FORK.md
git commit -m "feat: guard allowInsecure and drop its deprecation toast

Upstream plans to disable allowInsecure in August 2026. This fork keeps it.
Adds regression tests pinning the parse/serialize chain and the
ProfileItem.insecure -> tlsSettings.allowInsecure path, removes the
double deprecation toast, and documents the divergence in FORK.md."
```

---

## Task 2: Kompakt ekran tespiti ve dairesel güvenli alan

Bu task bittiğinde, **hiç dokunulmayan tüm ekranlar** (ayarlar, abonelik, sunucu düzenleme — Katman B) yuvarlak ekranda otomatik olarak güvenli alana çekilmiş olur.

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/compose/CompactRound.kt`
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/compose/CompactRoundTest.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/compose/Theme.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/base/BaseComponentActivity.kt`

**Interfaces:**
- Consumes: mevcut `AppTheme(darkTheme, content)`, `LocalDarkTheme`, `LocalAppSnackbar`
- Produces:
  - `isCompactRoundScreen(smallestScreenWidthDp: Int, screenWidthDp: Int, screenHeightDp: Int, isScreenRound: Boolean): Boolean`
  - `chordHalfWidth(radius: Float, distanceFromCenter: Float): Float`
  - `val LocalCompactRound: ProvidableCompositionLocal<Boolean>`
  - `@Composable fun currentIsCompactRound(): Boolean`
  - `fun Modifier.circularStrictSafeArea(): Modifier`
  - `BaseComponentActivity.managesOwnCompactSafeArea: Boolean` (open val, default `false`)

- [ ] **Step 1: Saf fonksiyonlar için başarısız testi yaz**

`V2rayNG/app/src/test/java/com/v2ray/ang/compose/CompactRoundTest.kt`:

```kotlin
package com.v2ray.ang.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactRoundTest {

    // ==================== Detection ====================

    /** The actual target device: MU5358, 240x240 @ 160dpi, reports notround. */
    @Test
    fun test_detects_mu5358_240x240_reporting_notround() {
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 240,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_ignores_typical_phone() {
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 411,
                screenWidthDp = 411,
                screenHeightDp = 891,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_ignores_small_but_tall_screen() {
        // Small width alone must not trigger compact mode — it has to be near-square.
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 400,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_detects_device_that_reports_round_honestly() {
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 227,
                screenWidthDp = 227,
                screenHeightDp = 227,
                isScreenRound = true
            )
        )
    }

    @Test
    fun test_tolerates_small_aspect_delta() {
        // 8dp of slack, e.g. a 240x246 panel.
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 246,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_rejects_just_above_threshold() {
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 281,
                screenWidthDp = 281,
                screenHeightDp = 281,
                isScreenRound = false
            )
        )
    }

    // ==================== Chord math ====================

    @Test
    fun test_chordHalfWidth_atCenter_equalsRadius() {
        assertEquals(120f, chordHalfWidth(120f, 0f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_atEdge_isZero() {
        assertEquals(0f, chordHalfWidth(120f, 120f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_beyondEdge_isZero() {
        assertEquals(0f, chordHalfWidth(120f, 200f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_isSymmetric() {
        assertEquals(chordHalfWidth(120f, 60f), chordHalfWidth(120f, -60f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_knownValues() {
        // sqrt(120^2 - 60^2)  = sqrt(10800) ~= 103.92
        assertEquals(103.92f, chordHalfWidth(120f, 60f), 0.01f)
        // sqrt(120^2 - 100^2) = sqrt(4400)  ~=  66.33
        assertEquals(66.33f, chordHalfWidth(120f, 100f), 0.01f)
    }

    /**
     * Sanity check tying the two safe-area strategies together: the strict inscribed
     * square (35dp inset on a 240dp screen) must match the chord at the square's edge.
     */
    @Test
    fun test_strictInsetAgreesWithChordAtSquareEdge() {
        val halfSquare = chordHalfWidth(120f, 85f)
        assertEquals(84.7f, halfSquare, 0.5f)
    }
}
```

- [ ] **Step 2: Testi çalıştır, başarısız olduğunu doğrula**

Run: `cd V2rayNG && ./gradlew test --tests "com.v2ray.ang.compose.CompactRoundTest"`
Expected: **FAIL** — derleme hatası, `isCompactRoundScreen` ve `chordHalfWidth` tanımlı değil.

- [ ] **Step 3: `CompactRound.kt`'yi yaz**

`V2rayNG/app/src/main/java/com/v2ray/ang/compose/CompactRound.kt`:

```kotlin
package com.v2ray.ang.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Support for small, physically round screens.
 *
 * The target device (MU5358, 240x240 @ 160dpi) reports `notround`, so neither the
 * `-round` resource qualifier nor `Configuration.isScreenRound` nor system-provided
 * round insets are available. The circular safe area is computed here instead.
 */

/** Screens at or below this smallest-width are treated as compact. */
const val COMPACT_ROUND_MAX_SW_DP = 280

/** How far from square a screen may be and still count as round. */
const val COMPACT_ROUND_MAX_ASPECT_DELTA_DP = 8

/**
 * Inset fraction that yields the largest square fitting inside a circle:
 * (1 - 1/sqrt(2)) / 2 ~= 0.14645. On a 240dp screen that is ~35dp per side,
 * leaving a 170dp square.
 */
private val STRICT_INSET_FRACTION: Float = (1f - 1f / sqrt(2f)) / 2f

/**
 * Whether a screen should use the compact round layout.
 *
 * Pure function so it can be unit tested without a Configuration instance.
 */
fun isCompactRoundScreen(
    smallestScreenWidthDp: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    isScreenRound: Boolean,
): Boolean {
    val small = smallestScreenWidthDp <= COMPACT_ROUND_MAX_SW_DP
    val nearlySquare =
        abs(screenWidthDp - screenHeightDp) <= COMPACT_ROUND_MAX_ASPECT_DELTA_DP
    return (small && nearlySquare) || isScreenRound
}

/**
 * Half the width of the horizontal chord of a circle at a given vertical distance
 * from its centre: sqrt(r^2 - d^2), clamped to zero outside the circle.
 *
 * Used to size content that sits away from the vertical centre of a round screen.
 */
fun chordHalfWidth(radius: Float, distanceFromCenter: Float): Float {
    val d = abs(distanceFromCenter)
    if (d >= radius) return 0f
    return sqrt(radius * radius - d * d)
}

/** True when the current screen uses the compact round layout. Provided by [AppTheme]. */
val LocalCompactRound = staticCompositionLocalOf { false }

/** Reads the current [android.content.res.Configuration] and applies [isCompactRoundScreen]. */
@Composable
@ReadOnlyComposable
fun currentIsCompactRound(): Boolean {
    val configuration = LocalConfiguration.current
    return isCompactRoundScreen(
        smallestScreenWidthDp = configuration.smallestScreenWidthDp,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        isScreenRound = configuration.isScreenRound,
    )
}

/**
 * Constrains content to the largest square that fits inside the circular display.
 *
 * A gentler inset is not enough: at 20dp the corner of a 240dp screen still sits
 * ~141dp from the centre, outside the 120dp radius, so it is physically invisible.
 */
fun Modifier.circularStrictSafeArea(): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val inset = (minOf(width, height) * STRICT_INSET_FRACTION).roundToInt()
    val innerWidth = (width - 2 * inset).coerceAtLeast(0)
    val innerHeight = (height - 2 * inset).coerceAtLeast(0)

    val placeable = measurable.measure(
        Constraints(
            minWidth = 0,
            maxWidth = innerWidth,
            minHeight = 0,
            maxHeight = innerHeight,
        )
    )

    layout(width, height) { placeable.place(inset, inset) }
}
```

- [ ] **Step 4: Testi çalıştır, geçtiğini doğrula**

Run: `cd V2rayNG && ./gradlew test --tests "com.v2ray.ang.compose.CompactRoundTest"`
Expected: **PASS** (12 test).

- [ ] **Step 5: `AppTheme`'i bağla**

`compose/Theme.kt` içinde `AppTheme` imzası ve gövdesi değişir. Mevcut hali (`Theme.kt:148` civarı):

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
```

Yeni hali:

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    applyCompactSafeArea: Boolean = true,
    content: @Composable () -> Unit
) {
```

Gövdenin başına eklenir (`val colorScheme = ...` satırının hemen ardına):

```kotlin
    val compactRound = currentIsCompactRound()
```

`CompositionLocalProvider` çağrısına yeni bir sağlayıcı eklenir:

```kotlin
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController,
        LocalCompactRound provides compactRound,
    ) {
```

Ve `Box` içindeki `content()` çağrısı koşullu hale gelir:

```kotlin
            MaterialTheme(
                colorScheme = colorScheme
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppSnackbarBridge(controller = snackbarController)
                    if (compactRound && applyCompactSafeArea) {
                        Box(modifier = Modifier.fillMaxSize().circularStrictSafeArea()) {
                            content()
                        }
                    } else {
                        content()
                    }
                    AppSnackbarHost(hostState = snackbarController.hostState)
                }
            }
```

Not: snackbar host bilerek güvenli alanın **dışında** bırakılır — snackbar zaten ekranın dikey ortasına yakın çıkar ve kendi genişliğini sınırlar.

- [ ] **Step 6: `BaseComponentActivity`'ye kanca ekle**

`ui/base/BaseComponentActivity.kt`, `onCreate` ve yeni açık özellik:

```kotlin
abstract class BaseComponentActivity : ComponentActivity() {

    /**
     * Set to true by activities whose compact-mode content applies its own circular
     * safe area. Prevents double insetting. Has no effect on non-compact screens.
     */
    protected open val managesOwnCompactSafeArea: Boolean = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase ?: return, SettingsManager.getLocale()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(applyCompactSafeArea = !managesOwnCompactSafeArea) {
                ScreenContent()
            }
        }
    }

    @Composable
    protected abstract fun ScreenContent()
}
```

`HelperBaseComponentActivity` `BaseComponentActivity`'den türediği için ek değişiklik gerekmez.

- [ ] **Step 7: Derle**

Run: `cd V2rayNG && ./gradlew assemblePlaystoreDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/compose/CompactRound.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/compose/CompactRoundTest.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/compose/Theme.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/base/BaseComponentActivity.kt
git commit -m "feat: detect compact round screens and apply a circular safe area

Small near-square screens (sw <= 280dp) now get the largest square that fits
inside the circular display, applied centrally in AppTheme so every screen is
covered. The target device reports notround, so detection and insetting are
computed rather than taken from the system. Phone layouts are unaffected."
```

---

## Task 3: Kompakt ana ekran ve `MainActivity` bağlantısı

Bu task bittiğinde cihazda çalışan, görünür bir sonuç olur: bağlan/kes butonu.

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt`

**Interfaces:**
- Consumes: `LocalCompactRound`, `Modifier.circularStrictSafeArea()` (Task 2); `MainViewModel.uiState: StateFlow<MainUiState>` (`statusText`, `isRunning`, `selectedGuid`, `selectedGroupId` alanları); `MainAction.ToggleService`; `MmkvManager.decodeServerConfig(guid): ProfileItem?`
- Produces:
  - `enum class CompactRoute { Home, Profiles, Menu }`
  - `@Composable fun CompactMainScreen(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit, onNavigate: (String) -> Unit)`
  - `MainActivity.handleAction(action: MainAction)` — özel metot, mevcut `when` bloğunun çıkarılmış hali

- [ ] **Step 1: `CompactMainScreen.kt`'yi yaz**

Task 4 ve 5 henüz yok; o iki rota şimdilik yer tutucu değil, **gerçek ama boş** bir davranışla bağlanır: `Profiles` ve `Menu` rotaları bu adımda `Home`'a geri döner. Task 4 ve 5 bunları gerçek ekranlarla değiştirir.

`V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt`:

```kotlin
package com.v2ray.ang.ui.main.compact

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.circularStrictSafeArea
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel

/** Screens of the compact round-screen mode. Deliberately a flat, tiny state machine. */
enum class CompactRoute { Home, Profiles, Menu }

/**
 * Compact replacement for [com.v2ray.ang.ui.main.MainScreen].
 *
 * The phone layout spends roughly 80dp on a top bar, bottom bar and drawer handle,
 * which is a third of a 240dp screen. This screen drops all three and navigates
 * between three full-screen destinations instead.
 */
@Composable
fun CompactMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(CompactRoute.Home) }

    BackHandler(enabled = route != CompactRoute.Home) { route = CompactRoute.Home }

    val selectedGuid = uiState.selectedGuid
    val profileName = remember(selectedGuid) {
        selectedGuid?.let { MmkvManager.decodeServerConfig(it)?.remarks }
    }

    when (route) {
        CompactRoute.Home -> CompactHome(
            statusText = uiState.statusText,
            isRunning = uiState.isRunning,
            profileName = profileName,
            onToggle = { onAction(MainAction.ToggleService) },
            onOpenProfiles = { route = CompactRoute.Profiles },
            onOpenMenu = { route = CompactRoute.Menu },
        )

        // Wired up in Task 4 and Task 5. Until then these render nothing and the
        // BackHandler above returns to Home. Do NOT assign to `route` here: writing
        // state during composition is what causes recomposition loops.
        CompactRoute.Profiles, CompactRoute.Menu -> Unit
    }
}

@Composable
private fun CompactHome(
    statusText: String,
    isRunning: Boolean,
    profileName: String?,
    onToggle: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .circularStrictSafeArea(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isRunning) {
                    MaterialTheme.colorScheme.onTertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = if (isRunning) {
                            painterResource(R.drawable.ic_stop_24dp)
                        } else {
                            painterResource(R.drawable.ic_play_24dp)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Text(
                text = profileName ?: stringResource(R.string.title_file_chooser),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProfiles)
                    .padding(vertical = 2.dp),
            )

            Icon(
                painter = painterResource(R.drawable.ic_menu_24dp),
                contentDescription = stringResource(R.string.title_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onOpenMenu),
            )
        }
    }
}
```

- [ ] **Step 2: `MainActivity`'de eylem yönlendirmesini metoda çıkar**

`MainActivity.kt` içindeki `ScreenContent()`'in gövdesindeki uzun `onAction` lambda'sı özel bir metoda taşınır ki iki ekran da aynı yönlendirmeyi kullanabilsin. `ScreenContent()`'in **öncesine** eklenir:

```kotlin
    private fun handleAction(action: MainAction) {
        when (action) {
            MainAction.ToggleService -> handleFabAction()
            MainAction.TestCurrentServer -> handleLayoutTestClick()
            MainAction.ImportQRcode -> importQRcode()
            MainAction.ImportClipboard -> importClipboard()
            MainAction.ImportConfigLocal -> importConfigLocal()
            is MainAction.ImportManually -> importManually(action.type)
            MainAction.RestartService -> restartV2Ray()
            MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
            is MainAction.SelectServer -> setSelectServer(action.guid)
            is MainAction.EditServer -> editServer(action.guid, action.profile)
            is MainAction.ShareClipboard -> shareToClipboard(action.guid)
            is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
            else -> mainViewModel.onAction(action)
        }
    }
```

`MainAction` import'u zaten dolaylı olarak aynı pakette (`com.v2ray.ang.ui.main`), ek import gerekmez.

- [ ] **Step 3: `ScreenContent()`'i koşullu hale getir**

`MainActivity.kt`:

```kotlin
    override val managesOwnCompactSafeArea: Boolean = true

    @Composable
    override fun ScreenContent() {
        if (LocalCompactRound.current) {
            CompactMainScreen(
                mainViewModel = mainViewModel,
                onAction = ::handleAction,
                onNavigate = ::navigateTo,
            )
        } else {
            MainScreen(
                mainViewModel = mainViewModel,
                onAction = ::handleAction,
                onNavigate = { route -> navigateTo(route) },
                shareMethodEntries = resources.getStringArray(R.array.share_method).toList(),
                shareMethodMoreEntries = resources.getStringArray(R.array.share_method_more).toList()
            )
        }
    }
```

Eklenecek import'lar:

```kotlin
import com.v2ray.ang.compose.LocalCompactRound
import com.v2ray.ang.ui.main.compact.CompactMainScreen
```

- [ ] **Step 4: Derle ve testleri çalıştır**

```bash
cd V2rayNG && ./gradlew test && ./gradlew assemblePlaystoreDebug
```
Expected: BUILD SUCCESSFUL, tüm testler PASS.

- [ ] **Step 5: Cihazda ilk görsel doğrulama**

```bash
D=192.168.0.1:5555
adb connect $D
adb -s $D install -r V2rayNG/app/build/outputs/apk/playstore/debug/*arm64-v8a*.apk
adb -s $D shell am start -n com.v2ray.ang/.ui.main.MainActivity
adb -s $D shell input keyevent KEYCODE_WAKEUP
adb -s $D exec-out screencap -p > /tmp/compact-home.png
```

Ekran görüntüsünde beklenen: ortada dairesel bir bağlan butonu, üstünde durum metni, altında profil adı ve menü ikonu; hiçbiri 240dp karenin köşelerine taşmamış.

Not: APK adı ABI split'e göre değişir; `ls V2rayNG/app/build/outputs/apk/playstore/debug/` ile doğrulanır. Cihaz `arm64-v8a`.

- [ ] **Step 6: Commit**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
git commit -m "feat: add compact round-screen home with connect toggle

Replaces the top bar, bottom bar and drawer with a single centred connect
button, status line and profile name on compact round screens. Reuses
MainViewModel and MainAction unchanged; the action dispatcher is extracted
so both the phone and compact screens share it."
```

---

## Task 4: Kompakt profil listesi

Asıl istenen özellik: kolay profil değiştirme.

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactProfileList.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt`

**Interfaces:**
- Consumes: `chordHalfWidth(radius, distanceFromCenter)` (Task 2); `CompactRoute` (Task 3); `MainViewModel.serversForGroup(groupId): StateFlow<List<ServersCache>>`; `ServersCache(guid, profile, testDelayMillis, testDelayString)`; `MainUiState.groups: List<GroupMapItem>` (`GroupMapItem(id, remarks)`); `MainAction.SelectServer(guid)`, `MainAction.SelectGroup(groupId)`
- Produces: `@Composable fun CompactProfileList(mainViewModel: MainViewModel, onAction: (MainAction) -> Unit, onClose: () -> Unit)`

- [ ] **Step 1: `CompactProfileList.kt`'yi yaz**

Satır genişliği, listenin içerik bandının kenarındaki kirişten hesaplanır — bu `chordHalfWidth`'in gerçek çalışma zamanı kullanımıdır. 240dp ekranda 28dp dikey iç boşlukla bant yarı yüksekliği 92dp, kiriş yarı genişliği ~77dp, yani satır genişliği ~154dp.

`V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactProfileList.kt`:

```kotlin
package com.v2ray.ang.ui.main.compact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.compose.chordHalfWidth
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel

/** Vertical breathing room; also decides how wide rows may be (see [rowWidthDp]). */
private const val LIST_VERTICAL_PADDING_DP = 28f

/**
 * Widest row that stays inside the circle across the whole scrollable band.
 *
 * Rows never reach the extreme top or bottom of the display because of the content
 * padding, so the binding constraint is the chord at the edge of that band.
 */
private fun rowWidthDp(screenWidthDp: Int): Float {
    val radius = screenWidthDp / 2f
    val bandHalfHeight = radius - LIST_VERTICAL_PADDING_DP
    return 2f * chordHalfWidth(radius, bandHalfHeight)
}

/**
 * Full-screen profile picker. Tapping a row selects it; [MainActivity.setSelectServer]
 * restarts the service automatically when it is already running, which is what makes
 * switching profiles a single tap.
 */
@Composable
fun CompactProfileList(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onClose: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groupId = uiState.selectedGroupId

    val serverFlow = remember(groupId) { mainViewModel.serversForGroup(groupId) }
    val servers by serverFlow.collectAsStateWithLifecycle()

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val rowWidth = remember(screenWidthDp) { rowWidthDp(screenWidthDp).dp }

    val groups = uiState.groups
    val groupName = groups.firstOrNull { it.id == groupId }?.remarks

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = LIST_VERTICAL_PADDING_DP.dp),
        ) {
            if (groups.size > 1) {
                item(key = "group-header") {
                    GroupHeader(
                        name = groupName.orEmpty(),
                        width = rowWidth,
                        onClick = {
                            val index = groups.indexOfFirst { it.id == groupId }
                            val next = groups[(index + 1).mod(groups.size)]
                            onAction(MainAction.SelectGroup(next.id))
                        },
                    )
                }
            }

            if (servers.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.title_file_chooser),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(rowWidth),
                    )
                }
            }

            items(items = servers, key = { it.guid }) { server ->
                ProfileRow(
                    server = server,
                    selected = server.guid == uiState.selectedGuid,
                    width = rowWidth,
                    onClick = {
                        onAction(MainAction.SelectServer(server.guid))
                        onClose()
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun ProfileRow(
    server: ServersCache,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = server.profile.remarks,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (server.testDelayString.isNotEmpty()) {
                Text(
                    text = server.testDelayString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
```

- [ ] **Step 2: `CompactMainScreen`'de rotayı bağla**

`CompactMainScreen.kt` içindeki yer tutucu dal bölünür. Mevcut hali:

```kotlin
        CompactRoute.Profiles, CompactRoute.Menu -> Unit
```

`Profiles` kendi dalına ayrılır (`Menu` Task 5'e kadar `Unit` kalır):

```kotlin
        CompactRoute.Menu -> Unit

        CompactRoute.Profiles -> CompactProfileList(
            mainViewModel = mainViewModel,
            onAction = onAction,
            onClose = { route = CompactRoute.Home },
        )
```

- [ ] **Step 3: Derle**

Run: `cd V2rayNG && ./gradlew assemblePlaystoreDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Cihazda profil değiştirmeyi doğrula**

```bash
D=192.168.0.1:5555
adb -s $D install -r V2rayNG/app/build/outputs/apk/playstore/debug/*arm64-v8a*.apk
adb -s $D shell am start -n com.v2ray.ang/.ui.main.MainActivity
adb -s $D exec-out screencap -p > /tmp/compact-home.png
# profil adına dokun (ekranın alt-orta bölgesi, ~120,150)
adb -s $D shell input tap 120 150
adb -s $D exec-out screencap -p > /tmp/compact-list.png
```

Beklenen: liste açılır, satırlar yatayda ortalanmış ve ~154dp genişlikte, köşelere taşmıyor. Bir satıra dokununca ana ekrana dönülür ve profil adı değişmiş olur. Servis çalışıyorsa yeniden başlar.

- [ ] **Step 5: Commit**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactProfileList.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt
git commit -m "feat: add compact profile picker

Full-screen list sized from the chord of the circular display at the edge of
the scrollable band, so rows stay inside the visible area without falling back
to the much narrower inscribed square. Tapping a row selects the profile and
returns home; the existing SelectServer path restarts the service when running."
```

---

## Task 5: Kompakt menü

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMenuScreen.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt`

**Interfaces:**
- Consumes: `chordHalfWidth` (Task 2); `CompactRoute` (Task 3); `MainAction.ImportClipboard`, `MainAction.UpdateSubscriptions`; `MainActivity.navigateTo(destination: String)` rotaları `"settings"` ve `"logcat"`
- Produces: `@Composable fun CompactMenuScreen(onAction: (MainAction) -> Unit, onNavigate: (String) -> Unit, onClose: () -> Unit)`

Not: `ModalBottomSheet` bilerek kullanılmıyor — 240dp yuvarlak ekranda alt sayfa görünür alanın en dar kısmına denk gelir. Bunun yerine tam ekran bir liste kullanılıyor.

- [ ] **Step 1: `CompactMenuScreen.kt`'yi yaz**

Kullanılan kaynaklar (hepsi mevcut, doğrulandı):
`R.string.menu_item_import_config_clipboard`, `R.string.title_sub_update`, `R.string.title_settings`, `R.string.title_logcat`,
`R.drawable.ic_copy`, `R.drawable.ic_cloud_download_24dp`, `R.drawable.ic_settings_24dp`, `R.drawable.ic_logcat_24dp`.

```kotlin
package com.v2ray.ang.ui.main.compact

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.chordHalfWidth
import com.v2ray.ang.ui.main.MainAction

private const val MENU_VERTICAL_PADDING_DP = 28f

private data class CompactMenuEntry(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val onSelect: () -> Unit,
)

/**
 * Full-screen menu for compact round screens.
 *
 * A bottom sheet would land in the narrowest part of a circular display, so the
 * menu takes the whole screen instead. Only the entries that matter for basic use
 * are listed; everything else stays reachable through Settings.
 */
@Composable
fun CompactMenuScreen(
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val rowWidth = remember(screenWidthDp) {
        val radius = screenWidthDp / 2f
        (2f * chordHalfWidth(radius, radius - MENU_VERTICAL_PADDING_DP)).dp
    }

    val entries = listOf(
        CompactMenuEntry(R.drawable.ic_copy, R.string.menu_item_import_config_clipboard) {
            onAction(MainAction.ImportClipboard)
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_cloud_download_24dp, R.string.title_sub_update) {
            onAction(MainAction.UpdateSubscriptions)
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_settings_24dp, R.string.title_settings) {
            onNavigate("settings")
            onClose()
        },
        CompactMenuEntry(R.drawable.ic_logcat_24dp, R.string.title_logcat) {
            onNavigate("logcat")
            onClose()
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = MENU_VERTICAL_PADDING_DP.dp),
    ) {
        items(items = entries, key = { it.label }) { entry ->
            MenuRow(entry = entry, width = rowWidth)
        }
    }
}

@Composable
private fun MenuRow(
    entry: CompactMenuEntry,
    width: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = entry.onSelect)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(entry.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

- [ ] **Step 2: `CompactMainScreen`'de rotayı bağla**

```kotlin
        CompactRoute.Menu -> Unit
```

yerine:

```kotlin
        CompactRoute.Menu -> CompactMenuScreen(
            onAction = onAction,
            onNavigate = onNavigate,
            onClose = { route = CompactRoute.Home },
        )
```

- [ ] **Step 3: Derle ve testleri çalıştır**

```bash
cd V2rayNG && ./gradlew test && ./gradlew assemblePlaystoreDebug
```
Expected: BUILD SUCCESSFUL, tüm testler PASS.

- [ ] **Step 4: Commit**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMenuScreen.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt
git commit -m "feat: add compact menu with clipboard import

Full-screen menu rather than a bottom sheet, which would land in the narrowest
part of a circular display. Carries clipboard import, subscription update,
settings and logcat; everything else stays reachable through settings."
```

---

## Task 6: Sertifika sabitleme (pcs) akışını kompakt modda erişilebilir kıl

Xray-core `allowInsecure`'ü kaldırdığı için resmî ikame `pinnedPeerCertSha256`. Uygulama bunu zaten destekliyor ama yalnızca sunucu düzenleme formunda — 240dp ekranda ulaşması zahmetli. Bu task, seçili profil için tek dokunuşla sertifika parmak izi çekip sabitleyen bir menü girişi ekler. Kendinden imzalı sertifikayla çalışmaya devam etmenin yolu budur ve `allowInsecure`'den güvenlik olarak da üstündür: MITM'e açık bırakmaz.

**Files:**
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMenuScreen.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt`

**Interfaces:**
- Consumes: `CompactMenuScreen` (Task 5), `CompactMainScreen` (Task 3); `CertificateFingerprintManager.fetchForManualFill(profile: ProfileItem): String?`; `MmkvManager.decodeServerConfig(guid): ProfileItem?` ve `MmkvManager.encodeServerConfig(guid, config): String`; `MainAction.RefreshGroups`; mevcut `MainActivity.restartV2Ray()`
- Produces: `MainActivity.pinCertificateForSelectedProfile()`; `CompactMenuScreen`'e `onPinCertificate: () -> Unit` parametresi

Bu task **`MainContract.kt`'ye dokunmaz** — yeni bir `MainAction` eklemek yerine geri çağırım aşağı geçirilir, böylece plan boyunca geçerli olan "MainContract davranışı değişmez" kısıtı korunur.

Kullanılan kaynaklar (hepsi mevcut, doğrulandı): `R.string.pinned_ca256_action_fetch`, `R.string.toast_fetch_cert_sha256_success`, `R.string.toast_fetch_cert_sha256_failed`, `R.string.toast_config_file_invalid`, `R.string.title_file_chooser`, `R.drawable.ic_lock_24dp`.

- [ ] **Step 1: `MainActivity`'ye sabitleme metodunu ekle**

`MainActivity.kt` içine, `handleAction`'ın yanına:

```kotlin
    /**
     * Fetches the selected profile's certificate SHA-256 and pins it.
     *
     * Xray-core removed `allowInsecure`; `pinnedPeerCertSha256` is the official
     * replacement and the app already carries it end to end. This turns a profile that
     * relied on skipping verification into one that verifies against a pinned cert,
     * without making the user open the full editor on a 240dp screen.
     */
    private fun pinCertificateForSelectedProfile() {
        val guid = mainViewModel.uiState.value.selectedGuid
        if (guid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        val profile = MmkvManager.decodeServerConfig(guid)
        if (profile == null) {
            toastError(R.string.toast_config_file_invalid)
            return
        }
        lifecycleScope.launch {
            val sha256 = withContext(Dispatchers.IO) {
                CertificateFingerprintManager.fetchForManualFill(profile)
            }
            if (sha256.isNullOrBlank()) {
                toastError(R.string.toast_fetch_cert_sha256_failed)
                return@launch
            }
            profile.pinnedCA256 = sha256
            profile.insecure = false
            MmkvManager.encodeServerConfig(guid, profile)
            toastSuccess(R.string.toast_fetch_cert_sha256_success)
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (mainViewModel.uiState.value.isRunning) restartV2Ray()
        }
    }
```

Eklenecek tek import (`MmkvManager`, `Dispatchers`, `withContext`, `lifecycleScope`, `toast`, `toastError`, `toastSuccess` zaten mevcut):

```kotlin
import com.v2ray.ang.handler.CertificateFingerprintManager
```

`profile.insecure = false` kasıtlıdır: sertifika sabitlendiğinde `CoreOutboundBuilder.kt:541` zaten `allowInsecure`'ü bastırıyor, bayrağı da temizlemek profili tutarlı bırakır.

- [ ] **Step 2: Geri çağırımı `CompactMainScreen` üzerinden geçir**

`CompactMainScreen` imzasına parametre eklenir:

```kotlin
fun CompactMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    onPinCertificate: () -> Unit,
)
```

ve `Menu` rotasına aktarılır:

```kotlin
        CompactRoute.Menu -> CompactMenuScreen(
            onAction = onAction,
            onNavigate = onNavigate,
            onPinCertificate = onPinCertificate,
            onClose = { route = CompactRoute.Home },
        )
```

`MainActivity.ScreenContent()` içindeki çağrı da güncellenir:

```kotlin
            CompactMainScreen(
                mainViewModel = mainViewModel,
                onAction = ::handleAction,
                onNavigate = ::navigateTo,
                onPinCertificate = ::pinCertificateForSelectedProfile,
            )
```

- [ ] **Step 3: Menüye girişi ekle**

`CompactMenuScreen` imzasına `onPinCertificate: () -> Unit` eklenir ve `entries` listesine, panodan içe aktarmanın hemen ardına yerleştirilir:

```kotlin
        CompactMenuEntry(R.drawable.ic_lock_24dp, R.string.pinned_ca256_action_fetch) {
            onPinCertificate()
            onClose()
        },
```

- [ ] **Step 4: Derle ve testleri çalıştır**

```bash
cd V2rayNG && ./gradlew test && ./gradlew assemblePlaystoreDebug
```
Expected: BUILD SUCCESSFUL. `com.v2ray.ang.UtilsTest` içindeki `test_isIpAddress` ve `test_IsIpInCidr` **bu dalda zaten kırık** (Task 1'de doğrulandı, bu planla ilgisiz) — başka bir başarısızlık olmamalı.

- [ ] **Step 5: Commit**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMenuScreen.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/compact/CompactMainScreen.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
git commit -m "feat: pin the selected profile's certificate from the compact menu

Xray-core removed allowInsecure and points at pinnedPeerCertSha256 instead.
The app already supports it, but only through the full server editor, which is
awkward on a 240dp screen. One menu entry now fetches the server's certificate
SHA-256, pins it, clears the insecure flag and restarts if running."
```

---

## Task 7: Cihazda uçtan uca doğrulama

**Files:** yok — yalnızca doğrulama. Bulunan hatalar kendi commit'leriyle düzeltilir.

**Interfaces:**
- Consumes: Task 1-5'in tamamı

- [ ] **Step 1: Temiz derleme ve kurulum**

```bash
cd V2rayNG && ./gradlew clean test assemblePlaystoreDebug
D=192.168.0.1:5555
adb connect $D
ls app/build/outputs/apk/playstore/debug/
adb -s $D install -r app/build/outputs/apk/playstore/debug/*arm64-v8a*.apk
```
Expected: tüm testler PASS, kurulum `Success`.

- [ ] **Step 2: Katman A ekranlarını doğrula**

```bash
adb -s $D shell input keyevent KEYCODE_WAKEUP
adb -s $D shell am start -n com.v2ray.ang/.ui.main.MainActivity
adb -s $D exec-out screencap -p > /tmp/v-home.png
adb -s $D shell input tap 120 150   # profil adı -> liste
adb -s $D exec-out screencap -p > /tmp/v-list.png
adb -s $D shell input keyevent KEYCODE_BACK
adb -s $D shell input tap 120 185   # menü ikonu
adb -s $D exec-out screencap -p > /tmp/v-menu.png
```

Her üç görüntüde kontrol: hiçbir içerik 240dp karenin köşelerinde değil, metinler kırpılmamış, dokunma hedefleri ≥44dp. Dokunma koordinatları ilk ekran görüntüsüne göre ayarlanır.

- [ ] **Step 3: Panodan içe aktarmayı doğrula**

```bash
# Panoya geçerli bir profil linki koy (allowInsecure ile)
adb -s $D shell am broadcast -a clipper.set -e text \
  'vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls&allowInsecure=1&type=tcp#Insecure%20Test'
```

Cihazda pano ayarlamak için bir yardımcı yoksa, alternatif olarak URL scheme yolu kullanılır (Step 4). Pano yolu için: menüden `Import from Clipboard`'a dokunulur, ana ekrana dönülüp profil listesinde yeni kaydın göründüğü doğrulanır.

- [ ] **Step 4: Paylaş menüsü / URL scheme yolunu doğrula**

```bash
adb -s $D shell am start -a android.intent.action.VIEW \
  -d 'v2rayng://install-config?url=vless%3A%2F%2F11111111-2222-3333-4444-555555555555%40example.com%3A443%3Fsecurity%3Dtls%26allowInsecure%3D1%26type%3Dtcp%23Insecure%2520Test'
adb -s $D exec-out screencap -p > /tmp/v-import.png
```

Beklenen: içe aktarma başarılı toast'ı, ardından `MainActivity` açılır ve profil listesinde `Insecure Test` görünür. Bu yol `UrlSchemeActivity` üzerinden gider ve kod değişikliği gerektirmemiştir.

- [ ] **Step 5: allowInsecure'ün uçtan uca çalıştığını doğrula**

1. Az önce içe aktarılan `Insecure Test` profili seçilir
2. Bağlan butonuna dokunulur
3. **Deprecation toast'ı çıkmamalıdır** (Task 1'de kaldırıldı)
4. Üretilen config doğrulanır:

```bash
adb -s $D logcat -d -s com.v2ray.ang 2>&1 | grep -i "allowInsecure" | tail -5
```

Gerçek bir sunucuya bağlanmak için elde geçerli bir profil varsa, kendinden imzalı sertifikalı bir uca bağlanıp trafiğin aktığı doğrulanır. Yoksa config üretiminin `allowInsecure: true` içerdiğini görmek yeterlidir.

- [ ] **Step 6: Katman B ekranlarının kırılmadığını doğrula**

```bash
adb -s $D shell am start -n com.v2ray.ang/.ui.settings.SettingsActivity
adb -s $D exec-out screencap -p > /tmp/v-settings.png
adb -s $D shell am start -n com.v2ray.ang/.ui.subscription.SubSettingActivity
adb -s $D exec-out screencap -p > /tmp/v-sub.png
```

Beklenen: her iki ekran da açılır, içerik 170dp'lik güvenli kareye çekilmiş, kaydırma çalışıyor. Estetik beklenti yok — hedef yalnızca erişilebilir ve kullanılabilir olmaları.

- [ ] **Step 7: Telefon davranışının değişmediğini doğrula**

Elde bir telefon veya `sw360dp+` bir emülatör varsa APK kurulur ve normal `MainScreen`'in (üst bar + alt bar + drawer + sekmeler) eskisi gibi geldiği doğrulanır. Emülatör yoksa bu, `isCompactRoundScreen` testlerindeki telefon vakasıyla (`test_ignores_typical_phone`) kapsanmış sayılır ve durum not edilir.

- [ ] **Step 8: `FORK.md`'yi doğrulama sonuçlarıyla güncelle ve commit'le**

`FORK.md`'nin "Compact round-screen mode" bölümüne doğrulanmış cihaz ve tarih eklenir:

```markdown
Verified on MU5358 (SHENQIJIYUAN), Android 15 / SDK 35, 240x240 @ 160dpi, arm64-v8a.
```

```bash
git add FORK.md
git commit -m "docs: record device verification for compact round-screen mode"
git push origin feat/compact-round-screen
```

---

## Öz-inceleme notları

**Spec kapsamı — her gereksinim bir task'a bağlı:**

| Spec bölümü | Task |
|---|---|
| Etkinleştirme (`sw<=280dp` + kare tespiti) | Task 2, Step 3 |
| `AppTheme` enjeksiyon noktası | Task 2, Step 5 |
| `MainActivity.ScreenContent()` koşullu ekran | Task 3, Step 3 |
| `strict` mod (35dp inset, 170dp kare) | Task 2, Step 3 (`circularStrictSafeArea`) |
| `chord` mod matematiği | Task 2, Step 3 (`chordHalfWidth`); kullanım Task 4/5 — **statik, spec'ten sapma yukarıda gerekçelendirildi** |
| `CompactMainScreen` (durum / buton / profil / menü) | Task 3, Step 1 |
| `CompactProfileList` (48dp satır, `SelectServer`) | Task 4, Step 1 |
| `CompactMenuSheet` → `CompactMenuScreen` | Task 5, Step 1 (bottom sheet yerine tam ekran — gerekçe task içinde) |
| Katman B otomatik uyarlama | Task 2, Step 5 (`AppTheme` varsayılanı) |
| `UrlSchemeActivity` değişiklik gerektirmiyor | Task 6, Step 4 (yalnızca doğrulama) |
| Deprecation toast'ının kaldırılması | Task 1, Step 5 |
| allowInsecure regresyon testleri | Task 1, Step 1-4 |
| `FORK.md` | Task 1, Step 7; Task 6, Step 8 |
| Xray-core dış riski | Task 1, Step 7 (`FORK.md` içinde) |
| pcs akışı (spec sonrası eklendi) | Task 6 |
| Cihaz doğrulaması | Task 7 |

**Task 6 spec'te yoktu.** Araştırma sırasında Xray-core'un `allowInsecure`'ü çoktan kaldırdığı ve sert hata döndürdüğü ortaya çıktı; `pinnedPeerCertSha256` resmî ikame. Kullanıcı kararı: core yamalanacak (ayrı depoda, ayrı plan) **ve** pcs akışı kolaylaştırılacak. Task 6 ikincisidir.

**Tip tutarlılığı:** `isCompactRoundScreen` / `chordHalfWidth` / `LocalCompactRound` / `currentIsCompactRound` / `circularStrictSafeArea` / `CompactRoute` / `managesOwnCompactSafeArea` adları tüm task'larda birebir aynı kullanıldı. `CompactMainScreen` üç parametreli (`mainViewModel`, `onAction`, `onNavigate`) olarak Task 3'te tanımlandı ve Task 3 Step 3'te aynı imzayla çağrıldı.

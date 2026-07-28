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

### Known limitations on the round screen

These are real and unfixed. They are written down here so nobody has to rediscover them.

- **Compose `Dialog`s bypass the circular safe area.** The safe area is a layout modifier
  applied inside the activity window, but `androidx.compose.ui.window.Dialog` composes into
  a *separate* window that the modifier never sees. `InputDialog` and `SelectListDialog`
  (`compose/Dialog.kt`, reached from Settings) therefore render full-bleed at 240dp, with
  their corners and their right-aligned confirm/cancel buttons outside the visible circle.
  Fixing it means giving those two composables a compact-gated inset of their own.
- **Rows clip mid-scroll.** Chord width is computed once, for the band a row occupies when
  it is at rest. A row scrolling *past* that band is still drawn at the width it was given,
  so its rounded ends are clipped by the panel while it is in transit. Transient and
  self-correcting once scrolling stops; it is the price of not doing a dynamic per-row
  layout pass.
- **The compact UI is append-only.** It can import, select, connect and pin a certificate.
  It exposes no path to delete or edit a profile — for that, use the phone UI on a normal
  screen, or clear data.
- **The compact home departs from the design spec in two places, both because of its
  height budget.** The spec assigns the home screen the `chord` safe-area mode; it uses
  `strict` instead, because `chord` sizing does not bound total column height. And the
  connect button is 76dp, not the spec's 110dp: the worst case column (status text,
  button, a two-line profile name, menu icon, three gaps) has to fit the 168dp square that
  `circularStrictSafeArea` leaves, and at 110dp it does not. See the arithmetic comment in
  `ui/main/compact/CompactMainScreen.kt`.

## 3. Import paths no longer wipe the default group

`AngConfigManager.importBatchConfig(server, subid, append)` calls
`MmkvManager.removeServerViaSubid(subid)` when `append` is false. With `subid = ""` that
deletes **every profile in the default group** before adding the imported one.

Two callers passed `false`, and both are fixed here:

- `ui/UrlSchemeActivity.kt` — `v2rayng://install-config`, `v2rayng://install-sub` and the
  system share menu.
- `ui/shortcut/ScScannerActivity.kt` — the QR-scan launcher shortcut.

Both now pass `true`, with a comment saying why.

The in-app import paths were never affected. `ImportClipboard`, `ImportQRcode` and
`ImportConfigLocal` (`ui/main/MainActivity.kt`) all funnel into
`MainViewModel.importBatchConfig`, which already passed `true`.

**On the parameter rename.** Upstream names the same parameter `append` in
`AngConfigManager` but `updateUI` in `MainDataSource` / `MainRepository`. That is a trap — the name
`updateUI` invites a caller to pass `false` for "do not refresh the list" and silently get
a destructive wipe instead — so this fork renames it to `append` throughout. But the rename
is hygiene, not the root cause of the bug above: `UrlSchemeActivity` and `ScScannerActivity`
call `AngConfigManager.importBatchConfig` **directly** and never touch `MainDataSource` or
`MainRepository`. If you are rebasing, look at the direct `AngConfigManager` callers, not at
the repository layer.

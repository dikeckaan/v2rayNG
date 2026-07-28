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

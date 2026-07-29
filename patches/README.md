# Core patches

## `0001-restore-allowInsecure.patch`

Restores the `allowInsecure` TLS option to Xray-core.

**Verified against two versions, both by building, not by inspection:**

| Xray-core | Commit | `git apply --check` | `go build ./...` |
|---|---|---|---|
| `v26.7.28` (latest at time of writing) | `5ca6f4b` | clean | exit 0 |
| `v26.7.11` (what AndroidLibXrayLite `v26.7.19` pins, i.e. what this repo ships today) | `50231ea` | clean | exit 0 |

### Why this is needed

Upstream commit `2c92339f9` (2026-01-31, *"TLS config: `allowInsecure`->`pinnedPeerCertSha256`"*)
removed the option from the TLS `.proto`, from the generated `config.pb.go`, and from the
runtime. Since then `infra/conf/transport_security.go` has done this:

```go
if c.AllowInsecure {
    return nil, errors.PrintRemovedFeatureError(`"allowInsecure"`, `"pinnedPeerCertSha256"(pcs) and "verifyPeerCertByName"(vcn)`)
}
```

That is a hard error — the core refuses to start, so the whole profile dies rather than
merely falling back to verifying certificates. There is **no date gate in the core**; the
"August 2026" date in v2rayNG's toast was the app's own client-side messaging.

### Why the patch is small

The verification machinery was never removed. `RandCarrier.verifyPeerCert`
(`transport/internet/tls/config.go`) still ends with:

```go
return nil // r.PinnedPeerCertSha256==nil && r.verifyPeerCertByName==nil
```

So when neither a pinned certificate nor a name check is configured, the callback accepts
any certificate. Only the field feeding `tls.Config.InsecureSkipVerify` was cut. Restoring
that field and wiring it back is enough — four files, 23 added lines:

| File | Change |
|---|---|
| `transport/internet/tls/config.proto` | re-add `bool allow_insecure = 1;` in place of upstream's `// Number 1 was assigned and used by an legacy option.` comment |
| `transport/internet/tls/config.pb.go` | regenerated from the above |
| `transport/internet/tls/config.go` | `InsecureSkipVerify: c.AllowInsecure` in the `tls.Config` literal |
| `infra/conf/transport_security.go` | hard error replaced by a warning + `config.AllowInsecure = true` |

The patch keeps a warning on this path deliberately: `allowInsecure` really is weaker than
`pinnedPeerCertSha256`, and the log line makes that visible without killing the connection.

### Applying it

```sh
git clone https://github.com/XTLS/Xray-core.git
cd Xray-core
git checkout v26.7.28          # or whichever version AndroidLibXrayLite builds against
git apply /path/to/0001-restore-allowInsecure.patch
go build ./...                  # must exit 0
```

`config.pb.go` is included in the patch, so `protoc` is **not** required to apply it. If you
rebase onto a newer Xray-core and the generated file conflicts, drop it from the patch and
regenerate instead:

```sh
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
brew install protobuf
PATH="$PATH:$HOME/go/bin" go run ./infra/vprotogen
```

Beware: `vprotogen` rewrites **every** `.pb.go` in the tree, because the protoc version
stamp changes. Keep only `transport/internet/tls/config.pb.go` and `git checkout --` the
rest, or the diff balloons to ~87 files of pure noise.

### Shipping it to the app

The app consumes the core as a prebuilt `libv2ray.aar` (see
`.github/workflows/build.yml`, which downloads it from an AndroidLibXrayLite release).
To ship a patched core:

1. Fork `XTLS/Xray-core`, apply this patch on a branch, push it.
2. Fork `2dust/AndroidLibXrayLite`, and in its `go.mod` add:
   `replace github.com/xtls/xray-core => github.com/<you>/Xray-core <version>`
3. Let that fork's GitHub Actions build and release `libv2ray.aar`.
4. Point this repo's submodule and CI download at your fork.

Building the AAR needs the Android NDK and `gomobile`; doing it in CI avoids installing
either locally.

### Maintenance

This patch has to be re-applied on every Xray-core bump. All four touched sites are small
and stable, but `infra/conf/transport_security.go` is the one upstream is most likely to
churn. After each bump, verify a profile with `allowInsecure` still connects — a silent
regression here looks identical to a server-side problem.

**Watch proto field number 1.** Upstream did *not* write `reserved 1;` when it removed
`allow_insecure` — it left only the comment `// Number 1 was assigned and used by an legacy
option.` A comment does not stop `protoc` from handing field 1 to something new, so a future
upstream release could legitimately reassign it. This patch would then still apply (the
comment line it replaces is what changes, so more likely it would conflict), but the real
hazard is silent wire incompatibility: a peer would read our `allow_insecure` bool as
whatever upstream put at field 1. On every bump, check that
`transport/internet/tls/config.proto` still has nothing at field 1 before applying.

Keep the patch free of generator-stamp churn. It deliberately does **not** touch the
`// protoc vX.Y.Z` header of `config.pb.go`: rewriting that stamp adds a hunk at line 1 of
the file that conflicts with any upstream regeneration, for no functional gain.

Preferring `pinnedPeerCertSha256` remains the better answer where it is practical; the app
has a one-tap fetch for it (`ui/server/BaseServerActivity.kt`, and the compact menu entry
added by Task 6 of the round-screen plan).

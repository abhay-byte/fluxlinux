# Plan: Unbundle Rootfs from APK — On-Demand GitHub Release Download

**Date:** 2026-08-14
**Status:** **IMPLEMENTED (P0–P7)** — code + scripts + docs landed; release assets uploaded; device R1–R12 smoke pending.
**Related plans:**
- [embedded-terminal-bootstrap-proot-chroot.md](./embedded-terminal-bootstrap-proot-chroot.md) — the plan that *bundled* rootfs into the APK (this plan reverses that decision)
- [termux-native-packages-dual-appid.md](./termux-native-packages-dual-appid.md) — COMPLETE; bootstrap stays bundled, only rootfs leaves the APK
- Design note in `termux-native-packages-dual-appid.md:633` — "Split assets later (OBB/Play Asset Delivery) if needed" → this plan is that split, done with GitHub Releases instead of OBB/PAD

**Goal:** Remove all 12 distro rootfs tarballs (~719 MiB STORED payload) from the release APK and download the selected distro's rootfs **on demand from GitHub Releases** at install time, with SHA256 + min-size gates, resume support, progress UI, and cancellation. On-disk APK shrinks from **866 MiB → ~147 MiB**.

---

## Review notes (2026-08-14)

Code/release audit of the first draft. Corrections baked into the sections below:

| # | What was wrong | Correction |
|---|----------------|------------|
| C1 | `unzip -l` **uncompressed** total (927,257,255) was labeled "~867 MiB APK" | That listing is entry `file_size` (~884 MiB). On-disk APK is **908,155,984 bytes = 866 MiB**. After removal: **~147 MiB** (908,155,984 − 754,071,580). 148 MiB was the right ballpark; the 927 MB figure was the wrong numerator. |
| C2 | "`rootfs` tag already hosts debian **+ alpine**" | **False.** `gh release view rootfs` has **only** `debian_13_rootfs.tar.xz` (SHA `13e29f60…`). Alpine URL returns **404**. P0 must upload alpine + the other 10. |
| C3 | P3 snippet used `enter(phases, 1)` for DL on **both** paths | Proot DL is index 1; **chroot DL is index 2** (R0=0, HOST=1). Adding a phase shifts every later index. Look up by `Phase.id`, don't hardcode. |
| C4 | P3 `if (!dlOk) return` before scripts run | Breaks D7/R9: scripts resolve `/sdcard/Download/<name>` **after** Kotlin. Downloader must be local-first (same candidate list) and only fail-closed when nothing local **and** the network fails. |
| C5 | P2 before P3 in the phase list | Shipping P2 (stop asset deploy) without P3 (download) leaves installs with no rootfs source. Safe merge order is below. |
| C6 | P4 missed `setup_alpine_chroot.sh` | Alpine chroot is **not** `setup_guest_chroot.sh`. It already has a `rootfs` URL + download fallback (same 404 as C2). |
| C7 | P4-T4 only added `FLUX_ROOTFS_URL` to the Java env map | `su -c` does **not** reliably inherit `TerminalSession` env. Chroot path must `export` URL + SHA256 **inside the command string** (onboarding `runChroot` already does this for PATH/NAME/SHA256). |
| C8 | `rootfsAsset` KDoc claimed "manual placement checks" | Manual placement uses `rootfsFileName` under `$HOME` / Download, not the APK asset path. Field is leftover for test stability. |
| C9 | `--clobber` on the floating `rootfs` tag | Overwriting the same filename with new bytes **breaks old APKs** that still pin the old SHA. Never clobber on a SHA change. |
| C10 | Free-space check vs `rootfsMinBytes` | Kali/Parrot/Arch/Manjaro `minBytes` is 40–80 MiB but files are 107–127 MiB. Check `Content-Length` (when known), not minBytes alone. |
| C11 | `libs.okhttp.mockwebserver` does not exist yet | Add the catalog entry. Do **not** extend `ApkDownloader` (no Range, no SHA gate, writes `cacheDir`). |
| C12 | "debian rootfs kept in git" / "already gitignored" | `assets/rootfs/` is gitignored, but **`assets/rootfs/debian_13_rootfs.tar.xz` is still tracked** (81 MiB). P5 must `git rm --cached` it. |
| C13 | `stageHostRootfs` described as copying all 12 fail-closed | Task is **best-effort** (`if [ -f … ]`); missing files are skipped. Fail-closed is `HostScriptDeployer`, not Gradle. |
| C14 | D7 "documented in-app help text" | No such help text exists. P6 must add it (or drop the claim). |
| C15 | `docs/plans/README.md` "register this plan" as future work | Already registered. |
| C16 | Typo "Majaro" | Manjaro. |

---

## 0. Why (bloat numbers, re-measured 2026-08-14)

Current ivarna **release APK file** (`app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`):

| Metric | Bytes | MiB |
|--------|------:|----:|
| On-disk APK | 908,155,984 | 866.1 |
| `unzip` uncompressed entry sum | 927,257,255 | 884.3 |
| `assets/rootfs/*` (12 files, all STORED) | 754,071,580 | 719.1 |
| `assets/bootstrap.tar` (stays, STORED) | 127,447,040 | 121.5 |
| **Expected on-disk APK after landing** | **~154,084,404** | **~147** |

`unzip -l` of `assets/rootfs/` (matches `assets/rootfs/` on disk; all STORED / compress_type 0):

| Entry | Size (bytes) | ~MiB | Action |
|-------|--------------|------|--------|
| `assets/rootfs/manjaro_arm_rootfs.tar.xz` | 133,044,216 | 126.9 | → release asset |
| `assets/bootstrap.tar` | 127,447,040 | 121.5 | **stays in APK** |
| `assets/rootfs/kali_2026_2_rootfs.tar.xz` | 123,244,844 | 117.5 | → release asset |
| `assets/rootfs/archlinux_arm_rootfs.tar.xz` | 116,277,544 | 110.9 | → release asset |
| `assets/rootfs/parrot_7.2_rootfs.tar.xz` | 111,851,420 | 106.7 | → release asset |
| `assets/rootfs/debian_13_rootfs.tar.xz` | 85,009,380 | 81.1 | → release asset |
| `assets/rootfs/deepin_25_rootfs.tar.xz` | 55,705,284 | 53.1 | → release asset |
| `assets/rootfs/void_20250202_rootfs.tar.xz` | 45,789,416 | 43.7 | → release asset |
| `assets/rootfs/fedora_44_rootfs.tar.xz` | 30,917,104 | 29.5 | → release asset |
| `assets/rootfs/opensuse_tumbleweed_rootfs.tar.xz` | 22,130,672 | 21.1 | → release asset |
| `assets/rootfs/ubuntu_26.04_rootfs.tar.xz` | 20,734,792 | 19.8 | → release asset |
| `assets/rootfs/chimera_20251220_rootfs.tar.xz` | 5,343,176 | 5.1 | → release asset |
| `assets/rootfs/alpine_3.24_rootfs.minirootfs` | 4,023,732 | 3.8 | → release as **`.tar.gz`** (D8) |
| **rootfs subtotal** | **754,071,580** | **719.1** | **removed from APK** |

~147 MiB is under the 200 MiB Play base-module compressed-download budget (bootstrap stays STORED at 121.5 MiB, so headroom is ~53 MiB, not ~52 from a 148 guess). Same assets for zenithblue.

---

## 1. As-is (how rootfs flows today)

```
assets/rootfs/*.tar.xz (debian TRACKED in git, 81 MiB;
                        other 11 local-only, gitignored)
    │  :app:stageHostRootfs (Exec, best-effort cp if source exists)
    │  Alpine: alpine_3.24_rootfs.tar.gz → alpine_3.24_rootfs.minirootfs
    ▼
app/src/main/assets/rootfs/ (gitignored staged copy)
    │  aapt2 packages (noCompress xz/tar/minirootfs → STORED)
    ▼
APK assets/rootfs/*
    │  HostScriptDeployer.deployAllRootfsFromAssets()
    │  prepareHost fail-closed: copies ALL 12 into $HOME
    ▼
$HOME/<rootfsFileName>  (SHA256 + rootfsMinBytes gate)
    │  OnboardingInstallRunner sets FLUX_ROOTFS_PATH/NAME/SHA256
    │  (no FLUX_ROOTFS_URL today)
    ▼
flux_install.sh / setup_debian13_chroot.sh / setup_alpine_chroot.sh
 / setup_guest_chroot.sh
    │  resolve_rootfs_archive(): local-first, then download if URL set
    ▼
proot-distro install <archive> --name <distro>   or   tar extract into chroot path
```

Key files (exact, verified today):

| File | Role |
|------|------|
| `app/build.gradle.kts:155-221` | `stageHostRootfs` Exec — **best-effort** copy of 12 rootfs into `app/src/main/assets/rootfs` |
| `app/build.gradle.kts:230` | `packageHostAssets*` `dependsOn(stageHostRootfs)` |
| `scripts/package_host_assets.sh:23,73-94` | stages + reports **Debian only** (SHA check) |
| `scripts/verify_apk_host_assets.sh:22,37` | presence + STORED gate for `assets/rootfs/debian_13_rootfs.tar.xz` only (not the other 11) |
| `app/src/main/kotlin/…/core/install/DistroInstallProfile.kt` | SSOT: `rootfsAsset`, `rootfsFileName`, `rootfsSha256`, `rootfsMinBytes` for 12 distros (24 cards) |
| `app/src/main/kotlin/…/core/terminal/HostScriptDeployer.kt:163-247` | `deployAllRootfsFromAssets()` copies **every** `allRootfsProfiles()` entry APK → `$HOME`, fail-closed. Comment still says "Debian + Alpine". `deployRootfsFromAssets` (line 207) has **no callers**. |
| `app/src/main/kotlin/…/core/terminal/TerminalLauncher.kt:170-181` | `prepareHostBlocking` fails if `deployScripts` fails (which includes the rootfs gate) |
| `app/src/main/kotlin/…/core/install/OnboardingInstallRunner.kt:165-292,294-444` | proot/chroot install; sets `FLUX_ROOTFS_PATH/NAME/SHA256`; **no download phase** |
| `app/src/main/kotlin/…/core/install/BaseDesktopInstallPlan.kt:30-45` | proot `HOST/ROOTFS/CUSTOM`; chroot `R0/HOST/ROOTFS/XFCE/CUSTOM` |
| `app/src/main/kotlin/…/core/terminal/InstallSessionFactory.kt:145-189` | terminal-session installs; proot extraEnv is PATH/NAME/SHA256 only |
| `app/src/main/assets/scripts/debian/proot/setup/flux_install.sh` | local-first + curl/wget; URL defaults **only** for debian + alpine; fedora…arch are `ROOTFS_URL=""` |
| `app/src/main/assets/scripts/chroot/setup_debian13_chroot.sh` | honors `FLUX_ROOTFS_URL`; default already `releases/download/rootfs/debian_13_rootfs.tar.xz` |
| `app/src/main/assets/scripts/chroot/setup_alpine_chroot.sh` | same pattern; default alpine `.tar.gz` URL (**404 today**) |
| `app/src/main/assets/scripts/chroot/setup_guest_chroot.sh:276` | **local-only** — no download fallback (Fedora/Void/openSUSE/Deepin/Chimera/Manjaro/Ubuntu/Kali/Parrot/Arch chroot) |
| `com.ivarna.fluxlinux.yml` | F-Droid metadata (`Binaries:` is the **APK** URL, not rootfs) |

Already in place (reuse, don't rebuild):

- GitHub release tag **`rootfs`** exists (title "debian rootfs latest") and hosts **only** `debian_13_rootfs.tar.xz` (85009380 bytes, SHA matches SSOT, 868 downloads). Alpine is **not** uploaded.
- `flux_install.sh` / `setup_debian13_chroot.sh` / `setup_alpine_chroot.sh` already default debian + alpine URLs at `releases/download/rootfs/…`.
- `ProotXfceAssetInstaller.httpDownload()` (`ProotXfceAssetInstaller.kt:392-409`) — `HttpURLConnection`, redirects, 120 s read timeout; small files only.
- `ApkDownloader` (`core/utils/ApkDownloader.kt`) — OkHttp already, **no Range, no SHA-during-write, writes `cacheDir`**. Do not extend it; new `RootfsDownloader` is the right type.
- `setup_customization_debian.sh` downloads theming zips from `releases/download/debian-v1/` — proven GH-release-hosted pattern.
- OkHttp (`libs.okhttp` = 4.12.0) is already an `implementation` dependency. **`mockwebserver` is not in the catalog yet.**
- `BaseInstallService` FGS (`foregroundServiceType=dataSync`) already wraps `OnboardingInstallRunner` — correct home for a multi-minute download.
- `INTERNET` is already in the manifest; privacy policy already describes on-demand rootfs download.

---

## 2. To-be (target flow)

```
GitHub Release tag `rootfs` (all 12 tarballs + sha256sums.txt)
    │  RootfsDownloader.ensurePresent:
    │    1. verified $HOME/<name> → done
    │    2. copy+verify from local candidates (same list as flux_install.sh)
    │    3. else OkHttp GET (Range resume) → $HOME/<name>.partial → rename
    │    SHA + size gate; progress/cancel callbacks
    ▼
$HOME/<rootfsFileName>        (same path the scripts already look at)
    │  OnboardingInstallRunner "DL" phase (id lookup, not magic index)
    ▼
flux_install.sh / setup_*_chroot.sh (unchanged local-first resolution;
   FLUX_ROOTFS_URL env passed for ALL distros as script-side fallback)
    ▼
proot-distro install … / chroot extract (unchanged)
```

APK contains **zero** `assets/rootfs/*`. Host readiness = bootstrap + scripts + loader only.

Side effect (call out): today `prepareHost` copies **all 12** archives into `$HOME` (~719 MiB) even if the user only installs Alpine. After P2 that stops.

---

## 3. Design decisions (locked)

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **GitHub Releases** as the host, tag `rootfs` (existing), not OBB / Play Asset Delivery | Repo already uses this pattern for `debian-v1` theming + the `rootfs` tag; works for F-Droid + GitHub + Play identically; 2 GiB/file limit is 2× the largest asset |
| D2 | **Kotlin-side downloader** is the primary path (not script curl) | Onboarding progress %, cancel, resume, FGS already running, no proot curl hangs; scripts keep curl/wget as fallback for **terminal-session** installs only. Onboarding does **not** fall through to curl if Kotlin fails **and** no local file exists. |
| D3 | **One rootfs at a time** — download only the distro the user installs | Worst-case install (Manjaro, 127 MiB) ≪ shipping 719 MiB |
| D4 | **SHA256 + min-bytes are the SSOT** (`DistroInstallProfile`), identical for bundled and downloaded bytes | Single source of truth; re-download when a pin changes (e.g. Fedora 43→44, which is a **new filename**) |
| D5 | **Resume via HTTP Range** into `<name>.partial`, atomic rename on success | Mobile networks; no double-counted data. 206 = append; 200 = restart/truncate; **416 = delete partial and restart**. GitHub 302 → `objects.githubusercontent.com` — OkHttp must forward `Range` across the host change (it does, except `Authorization`). |
| D6 | Rootfs stays **absent from host readiness gate** | `prepareHost` must succeed without any rootfs (host = bootstrap + scripts + loader only) |
| D7 | **Local-first in Kotlin *and* scripts** | `RootfsDownloader.ensurePresent` walks the same candidates as `flux_install.sh` (`$HOME/<name>`, `$HOME/rootfs/`, proot cache, `/sdcard/Download/`, emulated Download) and copies a verified hit into `$HOME/<name>` before touching the network. Offline `$HOME` placement always works. `/sdcard/Download` only works if the file is readable by the app uid — FluxLinux has **no** `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`; do not promise Download works on modern Android without adding a permission (out of scope). Document `$HOME` / `adb push` as the supported offline path. |
| D8 | Alpine release asset keeps gzip bytes under the **real** name `alpine_3.24_rootfs.tar.gz` | The `.minirootfs` rename only existed to dodge aapt2 `*.gz` decompression inside the APK; no packaging → no rename. `DistroInstallProfile.ALPINE_ROOTFS_NAME` already deploys as `.tar.gz`. Source file on disk: `assets/rootfs/alpine_3.24_rootfs.tar.gz` (SHA `f55a90f6…`). |
| D9 | **Never replace a release asset whose SHA is changing** | Floating tag `rootfs` + `--clobber` of the same filename breaks old APKs that still pin the old hash. New bytes ⇒ new filename (keep the old asset). `--clobber` is only for identical-byte retries. |
| D10 | **Install phases are addressed by `Phase.id`**, never by numeric index | Adding `DL` shifts every later index (proot 1→2→3, chroot 2→3→4→5). `BaseDesktopInstallPlanTest` asserts the id list + weight sum 100. |
| D11 | **Free-space precheck uses `Content-Length` when the server sends it** | Fall back to `rootfsMinBytes` only if length is unknown. Slack: require `usableSpace >= needed + 8 MiB`. `rootfsMinBytes` is a *minimum accepted file*, not the download size (Kali 40 vs 117.5 MiB). |

Out of scope (future): Play Asset Delivery / split-per-ABI, CDN mirroring, signature verification beyond SHA-256, Settings "delete unused rootfs archives" (upgrading users may already have all 12 in `$HOME` from today's deployer), storage permission for `/sdcard/Download`.

### Safe merge / ship order

Do **not** implement or merge in the written P-number order blindly.

1. **P0** — all 12 URLs return the pinned bytes (blocking for anything that hits the network).
2. **P1** — downloader + tests (unused in production until P3).
3. **P3 + P4** — onboarding DL phase + script URL defaults. Can land while assets are still bundled (`ensurePresent` no-ops when `$HOME` already has a verified file from the old deployer).
4. **P2 + P5 together** (or P2 immediately before P5) — stop deploying / stop packaging. **P2 without P3 = installs have no rootfs source. P5 without P0+P3 = a 147 MiB APK that cannot install.**
5. **P6** docs, **P7** verification.

---

## 4. Execution phases

### Phase 0 — Release assets (repo owner, `gh` CLI)

**P0-T1. New script `scripts/upload_rootfs_release.sh`**

- Verifies every file in the table below against `DistroInstallProfile` SHAs (hard-coded list mirrors the SSOT; see §4.1).
- Alpine source is `assets/rootfs/alpine_3.24_rootfs.tar.gz` (not the staged `.minirootfs` name).
- Generates `sha256sums.txt` from the **files** (computed, not a fourth pin copy).
- `gh release upload rootfs <12 files> sha256sums.txt` — use `--clobber` **only** when the SHA of the remote asset already matches (retry). Refuse to overwrite a same-name asset whose remote SHA differs (D9).
- Fails closed on any local SHA mismatch.
- `--check` dry-run: verify local SHAs + print the 12 URLs, no upload.
- Optionally `gh release edit rootfs --title "FluxLinux rootfs (12 distros)"` so the release is not still named "debian rootfs latest".

**P0-T2. Upload** all 12 tarballs + manifest to the `rootfs` tag. **Alpine is not there today (404).**

| Distro | Release filename | ~MiB | SHA256 (SSOT, full) |
|--------|------------------|------|---------------------|
| debian | `debian_13_rootfs.tar.xz` | 81.1 | `13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803` |
| alpine | `alpine_3.24_rootfs.tar.gz` | 3.8 | `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259` |
| fedora | `fedora_44_rootfs.tar.xz` | 29.5 | `2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4` |
| void | `void_20250202_rootfs.tar.xz` | 43.7 | `01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6` |
| opensuse | `opensuse_tumbleweed_rootfs.tar.xz` | 21.1 | `bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a` |
| deepin | `deepin_25_rootfs.tar.xz` | 53.1 | `2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698` |
| chimera | `chimera_20251220_rootfs.tar.xz` | 5.1 | `0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c` |
| manjaro | `manjaro_arm_rootfs.tar.xz` | 126.9 | `b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156` |
| ubuntu | `ubuntu_26.04_rootfs.tar.xz` | 19.8 | `e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc` |
| kali | `kali_2026_2_rootfs.tar.xz` | 117.5 | `01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689` |
| parrot | `parrot_7.2_rootfs.tar.xz` | 106.7 | `49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4` |
| archlinux | `archlinux_arm_rootfs.tar.xz` | 110.9 | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` |

On-disk files in `assets/rootfs/` were SHA-checked 2026-08-14; every pin matches. (Ubuntu/Chimera comments in `DistroInstallProfile` note they were gzip recompressed to xz for aapt2 — the release ships those **xz** bytes, same SHA.)

**Exit P0:** `curl -sIL` each of the 12 URLs ends at HTTP 200, and `sha256sum` of the downloaded bytes matches the SSOT. Debian already does; alpine currently 404.

### Phase 1 — Kotlin SSOT: download identity

**P1-T1. `DistroInstallProfile`** (`core/install/DistroInstallProfile.kt`)

- Add field: `val rootfsUrl: String` (full `https://github.com/abhay-byte/fluxlinux/releases/download/rootfs/<file>` per profile; chroot cards share the proot card's URL).
- Add companion `const val ROOTFS_RELEASE_BASE = "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs"` and per-distro `ROOTFS_URL` constants next to the existing `ROOTFS_NAME/SHA256` constants.
- `rootfsAsset` **stays** so `DistroInstallProfileTest` aapt2 assertions (`rootfsAsset` must not end in `.gz`) keep compiling. KDoc: *"legacy APK-asset path; unused after P2. Not the manual-placement name — that is `rootfsFileName`."*

**P1-T2. New `RootfsDownloader`** (`core/install/RootfsDownloader.kt`)

Keep it unit-testable on the JVM: **inject dest directory + OkHttpClient**, don't require `Context` in the core methods. A thin `Context` wrapper can call `TermuxHostPaths.homeDir(ctx)`.

```kotlin
object RootfsDownloader {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) // totalBytes may be -1

    /**
     * True when [destDir]/[DistroInstallProfile.rootfsFileName] exists,
     * length > rootfsMinBytes, and sha256 matches.
     */
    fun isDeployed(destDir: File, profile: DistroInstallProfile): Boolean

    /**
     * Local-first, then download.
     * 1. isDeployed → true
     * 2. first readable candidate (same list as flux_install.sh) whose
     *    sha256+minBytes pass → copy into destDir/<name> → true
     * 3. else GET profile.rootfsUrl → destDir/<name>.partial → rename
     *    - Range resume: existing .partial + `Range: bytes=<len>-`
     *      206 append; 200 truncate+restart; 416 delete partial + restart
     *    - OkHttp followRedirects + followSslRedirects (GitHub → objects.githubusercontent.com)
     *    - hash the **final** file (same shape as HostScriptDeployer.sha256)
     *    - gate: bytes > rootfsMinBytes && sha256 == rootfsSha256 else delete + false
     *    - cancel: check every chunk; keep .partial
     *    - free space: after headers, if Content-Length >= 0
     *        usableSpace < remaining + 8 MiB → fail
     *      else usableSpace < rootfsMinBytes + 8 MiB → fail
     */
    fun ensurePresent(
        destDir: File,
        profile: DistroInstallProfile,
        client: OkHttpClient,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): Boolean
}
```

Implementation notes:
- Default client: `OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).connectTimeout(30, SECONDS).readTimeout(60, SECONDS).writeTimeout(60, SECONDS).build()`. **No overall call timeout** (Manjaro is 127 MiB on slow networks).
- Do not reuse `ApkDownloader` (wrong dest, no resume).
- Move `HostScriptDeployer.sha256` (currently `private`) into this type (or a tiny shared hash helper) so P2 can delete the copy.

**P1-T3. Unit tests** (`app/src/test/…/core/install/RootfsDownloaderTest.kt`)

- Catalog: add `okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }` and `testImplementation(libs.okhttp.mockwebserver)`.
- `TemporaryFolder` as `destDir` — no Robolectric/`Context` required.
- Cases:
  - happy path 200 → deployed, SHA ok
  - truncated body → fail, `.partial` left; second call sends `Range` → 206 → success
  - 416 → partial deleted, full GET succeeds
  - SHA mismatch → dest deleted, false
  - cancel mid-stream → false, `.partial` retained
  - existing verified file → `isDeployed` true, **zero** requests
  - candidate in `destDir` sibling / injected extra path (simulate `$HOME/rootfs/` or Download) → copy, no network
  - `usableSpace` cannot be reliably mocked; skip or extract the predicate and unit-test that.

### Phase 2 — Host deployer no longer owns rootfs

**Land only after P3 (see merge order).**

**P2-T1. `HostScriptDeployer.deployScripts`** (`core/terminal/HostScriptDeployer.kt:163-167`)

- Remove the `rootfsOk` fail-closed term: host readiness = scripts + loader (bootstrap is `TerminalLauncher` / `BootstrapInstaller`).
- **Delete** `deployAllRootfsFromAssets` and `deployRootfsFromAssets`. Grep first: today the only production caller is `deployScripts` → `deployAllRootfsFromAssets`. No unit test calls them.
- `deployRootfsProfile` verify logic lives in `RootfsDownloader.isDeployed` (P1-T2). Delete the HostScriptDeployer copy.

**P2-T2. `TerminalLauncher.prepareHostBlocking`** — no change once `deployScripts` no longer gates on rootfs (it never referenced rootfs directly). Re-run existing host-readiness tests.

### Phase 3 — Onboarding download phase

**P3-T1. `BaseDesktopInstallPlan.phasesFor`** (`core/install/BaseDesktopInstallPlan.kt:30-45`)

```
proot:  HOST(20) → DL(15) → ROOTFS(35) → CUSTOM(30)                 // was HOST/ROOTFS/CUSTOM 20/50/30
chroot: R0(5) → HOST(15) → DL(10) → ROOTFS(30) → XFCE(25) → CUSTOM(15)  // was 5/15/35/25/20
```

Weights must still sum to 100 (`BaseDesktopInstallPlanTest` asserts this).

Update tests:

```
phasesFor("proot")   → ["HOST", "DL", "ROOTFS", "CUSTOM"]
phasesFor("chroot")  → ["R0", "HOST", "DL", "ROOTFS", "XFCE", "CUSTOM"]
```

UI reads `Progress.phaseId` / `phaseLabel` from the list — no hardcoded `HOST`/`ROOTFS` in Compose. Still grep `phases[` and numeric `enter(phases, N` in `OnboardingInstallRunner`.

**P3-T2. `OnboardingInstallRunner` — address phases by id**

Add a helper (or use `phases.indexOfFirst { it.id == id }`) and **remap every existing call**:

| Path | Today | After |
|------|-------|-------|
| proot HOST | 0 | `HOST` (0) |
| proot **DL (new)** | — | `DL` (**1**) |
| proot ROOTFS (`flux_install.sh`) | 1 | `ROOTFS` (**2**) |
| proot CUSTOM | 2 | `CUSTOM` (**3**) |
| chroot R0 | 0 | `R0` (0) |
| chroot HOST | 1 | `HOST` (1) |
| chroot **DL (new)** | — | `DL` (**2**, not 1) |
| chroot ROOTFS | 2 | `ROOTFS` (**3**) |
| chroot XFCE | 3 | `XFCE` (**4**) |
| chroot CUSTOM | 4 | `CUSTOM` (**5**) |

```kotlin
val destDir = TermuxHostPaths.homeDir(appCtx)
val dlIdx = phases.indexOfFirst { it.id == "DL" }
enter(phases, dlIdx, onProgress, "Downloading ${profile.displayName} rootfs…")
val dlOk = RootfsDownloader.ensurePresent(
    destDir, profile, RootfsDownloader.defaultClient,
    isCancelled = { isStale(gen) },
) { p ->
    if (isStale(gen)) return@ensurePresent
    val frac = if (p.totalBytes > 0) p.downloadedBytes.toFloat() / p.totalBytes else 0f
    updateFraction(
        phases, dlIdx, frac, onProgress,
        "Downloaded ${p.downloadedBytes / 1_048_576} / ${p.totalBytes.coerceAtLeast(0) / 1_048_576} MiB"
    )
}
if (!dlOk) {
    postFail(onProgress, phases, "Rootfs download failed — place ${profile.rootfsFileName} in the app home directory or retry online")
    return
}
completePhase(phases, dlIdx, onProgress, "Rootfs ready (${profile.rootfsFileName})")
```

- `ensurePresent` already no-ops when verified-local (re-install path).
- `FLUX_ROOTFS_PATH` still points at `$HOME/<name>`.
- **Add `FLUX_ROOTFS_URL`** (P3-T3).
- Cancel: existing `generation`/`cancelled` token; pass `{ isStale(gen) }`.
- chroot: insert the same block **after HOST, before staging the chroot setup script**. R0 stays first — non-rooted devices fail before any download (R6).

**P3-T3. Env wiring.** Everywhere `FLUX_ROOTFS_PATH/NAME/SHA256` are set, also set `FLUX_ROOTFS_URL=<profile.rootfsUrl>`:

- `OnboardingInstallRunner.runProot` env map (~199-201)
- `OnboardingInstallRunner.runChroot` **export string** (~346-349) — must stay in the `su -c` command, not only the unused Java env
- `InstallSessionFactory` (P4-T4)

### Phase 4 — Script-side fallback for ALL distros

**P4-T1. `flux_install.sh`**

- Fill `ROOTFS_URL` defaults in the `case` for fedora/void/opensuse/deepin/chimera/manjaro/ubuntu/kali/parrot/archlinux (currently empty strings) with `releases/download/rootfs/<file>` (same values as `DistroInstallProfile`).
- Keep existing debian/alpine defaults unchanged.
- Empty URL today still hits `curl -fL … "$ROOTFS_URL"` and fails opaquely — filling the defaults is what makes R8 work.
- Kotlin always passes `FLUX_ROOTFS_URL`, so defaults only matter for hand-run / Scripts-page sessions.

**P4-T2. `setup_debian13_chroot.sh`** — already honors `FLUX_ROOTFS_URL` (line 16) with the correct `rootfs` tag default. Verify only.

**P4-T2b. `setup_alpine_chroot.sh`** — already honors `FLUX_ROOTFS_URL` (line 13) with `alpine_3.24_rootfs.tar.gz`. Verify only. **P0 must make that URL stop 404ing.**

**P4-T3. `setup_guest_chroot.sh`** — add the same download fallback as `setup_debian13_chroot.sh` / `setup_alpine_chroot.sh`: honor `FLUX_ROOTFS_URL`, `curl -fL --retry 3` (or the existing `download_file` + busybox wget pattern — **copy one helper, don't invent a third**), cache at `$APP_PREFIX/var/lib/proot-distro/cache/rootfs/$ROOTFS_NAME`, then SHA check. Root path runs via `RootShell` (`su`), so the download lands root-owned — acceptable; Kotlin (P3) is still the primary path.

**P4-T4. `InstallSessionFactory`**

- `openProotInstall` extraEnv: add `"FLUX_ROOTFS_URL" to profile.rootfsUrl`.
- `openChrootInstall` / `openRootScriptSession`: put `FLUX_ROOTFS_URL` **and** `FLUX_ROOTFS_SHA256` (and NAME if missing) into the **`export …; sh script` string**. `RootShell.shellRootCommand` wraps `su -c '…'` and does not pass the `TerminalSession` env map through su. Onboarding `runChroot` already learned this; the Scripts-page chroot path must match.

### Phase 5 — Packaging: stop staging rootfs

**Ship with P2. Do not ship without P0 + P3.**

**P5-T1. `app/build.gradle.kts`**

- Delete `stageHostRootfs` (lines 155-221) and `dependsOn(stageHostRootfs)` (line 230).
- Keep `noCompress += listOf("xz", "tar")`. Drop `minirootfs` (Alpine is no longer packaged). `bootstrap.tar` must stay STORED.
- Update the comment block (lines 141-149) to describe bootstrap-only staging.

**P5-T2. `scripts/package_host_assets.sh`**

- Delete the Debian-only rootfs step (lines 23, 73-77, 79-94 incl. `ROOTFS_SRC`, `ROOTFS_SHA256`, report lines). Keep bootstrap + jniLibs.

**P5-T3. `scripts/verify_apk_host_assets.sh`**

- Remove `assets/rootfs/debian_13_rootfs.tar.xz` from the presence loop **and** the STORED check.
- Add a **negative gate**: fail if `unzip -l` matches `assets/rootfs/`.

**P5-T4. Repo hygiene**

- `rm -rf app/src/main/assets/rootfs` (staged copy, gitignored).
- **`git rm --cached assets/rootfs/debian_13_rootfs.tar.xz`** — this file is **tracked** (81 MiB) even though `.gitignore` has `assets/rootfs/`. Keep the local file for P0 uploads.
- `.gitignore` already has `assets/rootfs/` + `app/src/*/assets/rootfs/` — keep (local source tarballs still needed to build the GitHub release).
- `native/README.md:117` — "rootfs `debian_13_rootfs.tar.xz` is shared and kept in git (SHA pinned)" is already stale; replace with "rootfs is NOT packaged and NOT in git; distributed via GitHub release tag `rootfs`".

### Phase 6 — Docs + metadata

- `docs/assets_reference.md` — rewrite "Rootfs Archives". Current text is wrong in two ways: only lists Debian, and the download URL is the **old** `debian-v1/debian_arm64_rootfs.tar.xz` name. Table from P0-T2; supported offline path is `$HOME/<rootfsFileName>` (plus `/sdcard/Download` as best-effort).
- `docs/architecture.md:34,41` — "Debian rootfs: packaged asset" / "each flavor ships one ~122 MB bootstrap + ~82 MB rootfs" → bootstrap only; rootfs on demand.
- `docs/scripts_reference.md:599-628` — debian chroot already says "Download rootfs from GitHub release"; extend to all distros / `setup_guest_chroot.sh`.
- `docs/distro/alpine.md:68-77` — drop the aapt2 `.minirootfs` packaging note (runtime name stays `.tar.gz`; release asset is `.tar.gz`).
- `docs/adding_new_distro.md` — new distro rootfs goes on the `rootfs` tag + `DistroInstallProfile` pin, **not** into the APK / `stageHostRootfs`.
- `README.md` — only if it still mentions a bundled rootfs.
- `docs/plans/README.md` — **already lists this plan**; flip status as phases land.
- In-app: one sentence on the install-failure path and/or Distro Settings pointing at `$HOME/<name>` (D7/C14). No storage-permission UI.
- `com.ivarna.fluxlinux.yml` — F-Droid **build** is unaffected (rootfs was never required at compile time; debian-in-git going away just shrinks the clone). Re-run an `fdroid build` smoke after P5. Runtime download is already described in the privacy policy.

### Phase 7 — Verification

**Build/CI (no device needed):**

1. `./gradlew :app:testIvarnaDebugUnitTest` (and zenithblue) — all green, incl. new `RootfsDownloaderTest` and updated `BaseDesktopInstallPlanTest`.
2. `./gradlew :app:assembleIvarnaDebug :app:assembleZenithblueDebug` — passes with **no** `app/src/main/assets/rootfs` and with debian untracked.
3. `./scripts/verify_apk_host_assets.sh app/build/outputs/apk/ivarna/debug/app-ivarna-debug.apk` — PASS incl. negative rootfs gate.
4. `unzip -l` — **zero** `assets/rootfs/*`; `assets/bootstrap.tar` + jniLibs present.
5. Release build size: on-disk **~147 MiB** (ivarna + zenithblue).
6. `bash scripts/upload_rootfs_release.sh --check` — all 12 SHAs match SSOT.
7. `curl -sIL` each of the 12 release URLs → final HTTP 200 (after P0). Confirm alpine is no longer 404.

**Device checklist:**

- R1: clean install → onboarding Debian proot → DL phase shows MiB progress → install succeeds → `$HOME/debian_13_rootfs.tar.xz` SHA matches pin.
- R2: cancel mid-download → `.partial` kept → retry resumes (log shows Range/206, or 200-restart if the CDN ignored Range).
- R3: airplane-mode, nothing local → fail-fast message, install aborts, nothing marked installed.
- R4: Manjaro (127 MiB, largest) proot install end-to-end.
- R5: Debian Rooted on a rooted device — DL phase + root script uses `$HOME` file.
- R6: non-rooted device — chroot card fails at R0 **before** any download.
- R7: re-install — existing verified `$HOME` file → DL phase instant ("already present"), no network.
- R8: Scripts-page / terminal-session install with no local rootfs → script curl fallback (wifi). Needs P4-T1/T4.
- R9: offline — `adb push` (or equivalent) the file to `$HOME/<name>` → install proceeds without network. `/sdcard/Download` is best-effort only (no storage permission).
- R10: Fedora pin-bump simulation — replace `$HOME/fedora_44_rootfs.tar.xz` with different bytes → `ensurePresent` re-downloads; script SHA gate still catches a planted mismatch if Kotlin is skipped.
- R11: Alpine — gzip bytes under real `.tar.gz` name; no `.minirootfs` anywhere on the runtime path; release URL 200.
- R12: first launch after P2 — `prepareHost` does **not** copy 12 archives into `$HOME`. Disk used ≈ one chosen rootfs.

---

## 4.1 SHA/manifest SSOT note

Pins already live in more than three places. Do **not** add another hand-maintained copy.

| Location | What |
|----------|------|
| `DistroInstallProfile` companion | **SSOT** |
| `flux_install.sh` `case` | runtime fallback defaults (12 SHAs) |
| `setup_debian13_chroot.sh` / `setup_alpine_chroot.sh` | debian + alpine defaults |
| `scripts/package_host_assets.sh` | debian only — **deleted in P5** |
| `scripts/upload_rootfs_release.sh` | release-time mirror of the 12 SHAs |

`scripts/verify_rootfs_shas.sh` (small, like `verify_deb_prefix.sh`) diffs Kotlin SSOT vs `flux_install.sh` vs the two dedicated chroot scripts vs the upload script. Wire into P0-T1 `--check` and Phase 7. `sha256sums.txt` on the release is **generated from files**, not a sixth pin list.

---

## 5. Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| GitHub release URL changes / rate limits | Fixed `rootfs` tag; URLs centralized in SSOT; script fallback tries cache dir + `$HOME` |
| Alpine (and 10 others) 404 until P0 | P0 is the hard gate before P5 / any release APK |
| `--clobber` replaces bytes under the same name | D9: refuse SHA-changing overwrite |
| Download interrupted (mobile) | Range resume; FGS `dataSync` keeps process alive; `.partial` retained on cancel |
| GitHub 302 drops `Range` | OkHttp forwards Range on cross-host redirect; if a CDN returns 200, downloader restarts (no corrupt append) |
| User on metered network downloads 127 MiB | DL phase shows MiB count; chroot fails at R0 before download; no extra confirm dialog in this plan |
| Free space too small for Kali/Manjaro | D11: use `Content-Length`, not `rootfsMinBytes` |
| SHA pin updated but old APK installed | Old APK keeps downloading the **old filename** (D9). Upload the new filename before shipping the APK that references it |
| `deployScripts` no longer fail-closed on missing rootfs | Intentional (D6); install fails with a clear DL-phase / script message |
| aapt2 `*.gz` regression for Alpine | Alpine is no longer packaged (D8); `ALPINE_ROOTFS_ASSET` kept only so existing tests stay green |
| Existing users already have all 12 in `$HOME` | Local-first: zero re-download. Leftover ~719 MiB is out of scope (future Settings cleanup) |
| `/sdcard/Download` undocumented / unreadable | Supported offline path is `$HOME/<name>`; Download is best-effort; no new storage permission |
| P2 lands without P3, or P5 without P0 | Merge-order section; treat as a release blocker |
| Play policy (download-at-runtime) | Unchanged — rootfs is data, already disclosed in the privacy policy; still reviewer discretion |
| F-Droid | Build does not need rootfs; runtime download already in privacy policy |

## 6. Exit criteria

- [ ] All 12 rootfs live on GitHub release tag `rootfs` with SHA-verified manifest. Alpine URL is 200, not 404.
- [ ] Zero `assets/rootfs/*` entries in both flavor APKs; debian rootfs untracked; build green without staged rootfs.
- [ ] `RootfsDownloader` + tests landed; onboarding shows a `DL` phase with progress/cancel/resume; phases addressed by id.
- [ ] Script fallbacks (curl/wget) wired for all distros incl. `setup_guest_chroot.sh`; chroot terminal-session exports URL+SHA in the `su -c` string.
- [ ] `verify_apk_host_assets.sh` PASS incl. negative rootfs gate; unit tests green.
- [ ] Device R1–R12 logged; docs updated (incl. alpine.md + adding_new_distro.md); release APK ~147 MiB.

## 7. Progress log

| When | What |
|------|------|
| 2026-08-14 | Plan written from code audit (12 rootfs = 719 MiB STORED of the 866 MiB on-disk APK; GH `rootfs` tag hosts debian). |
| 2026-08-14 | Plan review vs tree + `gh release view rootfs` + `sha256sum assets/rootfs/*`. Corrections C1–C16 (alpine 404, APK size, phase-index remap, local-first Kotlin, merge order, tracked debian, `su -c` env, D9 clobber). |
| 2026-08-14 | Implemented P0–P7: `upload_rootfs_release.sh` + `verify_rootfs_shas.sh` (P0); `rootfsUrl` SSOT + `RootfsDownloader` + MockWebServer tests (P1); DL phase + id-based phase indexing + env wiring (P3); script URL defaults + `setup_guest_chroot.sh` fallback + `su -c` exports (P4); host deployer rootfs removal (P2); packaging negative gate + repo hygiene (P5); docs (P6); verification: both flavor unit tests + debug/release APKs ~147 MiB, zero `assets/rootfs/*`, all 12 release URLs 200 (P7). Device R1–R12 remain NOT RUN. |

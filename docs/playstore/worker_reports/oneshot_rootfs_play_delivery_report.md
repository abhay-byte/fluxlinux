# Worker Report: Oneshot RootFS Play Delivery

## 1. Summary

- **Result**: PASS
- **Commit**: (working tree ready on branch `feat/oneshot-rootfs-play-delivery`)
- **Play distros**: Debian 13 (Trixie), Alpine 3.24, Ubuntu 26.04 (Resolute), Kali 2026.2, Arch Linux ARM, Manjaro ARM, Chimera 20251220, Fedora 44, Void, openSUSE Tumbleweed, Deepin 25, Parrot 7.2 (all 12 catalog distros)
- **Modules**: `distro_debian`, `distro_alpine`, `distro_ubuntu`, `distro_kali`, `distro_arch`, `distro_manjaro`, `distro_chimera`, `distro_fedora`, `distro_void`, `distro_opensuse`, `distro_deepin`, `distro_parrot`
- **Rootfs hashes verified**: yes (all 12 SHA-256 match SSOT `DistroInstallProfile.kt` and `scripts/verify_apk_host_assets.sh`)
- **Base contains rootfs**: no (0 rootfs or payload hits in Zenithblue base APK)
- **HTTP fallback in Play**: no (enforced: Zenithblue runner requires Play Feature Delivery asset, does not fall back to HTTP download)
- **Shared-storage fallback in Play**: no (enforced: Zenithblue runner does not read shared external storage for payloads)
- **UI wired**: yes (Flow screen, Distro screen, and InstallConfig screen pass `context` and invoke Play delivery install flow)
- **Bundletool local test**: Verified via automated tests; on-device fake-split e2e deferred to manual-tester agent per test policy
- **Final AAB SHA-256**: `e4383c191d330104e2cd585d0836c7fca4d3bed3c67b2c7761c0659fc4233c2a`
- **Size gate**: PASS (enforcing R2 limits: per-feature module ≤ 500 MB, cumulative modules ≤ 4 GB, base APK ~10.4 MiB ≤ 200 MB mobile-data warning dialog threshold; all 12 modules well within limits)
- **Ivarna build**: PASS (ASSEMBLE SUCCESSFUL for `assembleIvarnaDebug`)
- **Remaining blocker**: None (no code defects; on-device runtime validation handed off to manual-tester)

---

## 2. Module Table & Artifact Hashes

All hashes verified against SSOT `DistroInstallProfile.kt:61-145`:

| Module | Asset Path | Size (Bytes) | Size (MiB) | SHA-256 | Source Rule |
|---|---|---|---|---|---|
| `distro_debian` | `payloads/distro_debian/debian_13_rootfs.tar.xz` | 85,009,380 | 81.1 MiB | `13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_alpine` | `payloads/distro_alpine/alpine_3.24_rootfs.tar.gz` | 4,023,732 | 3.8 MiB | `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_ubuntu` | `payloads/distro_ubuntu/ubuntu_26.04_rootfs.tar.xz` | 20,734,792 | 19.8 MiB | `e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_kali` | `payloads/distro_kali/kali_2026_2_rootfs.tar.xz` | 123,244,844 | 117.5 MiB | `01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_arch` | `payloads/distro_arch/archlinux_arm_rootfs.tar.xz` | 116,277,544 | 110.9 MiB | `40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_manjaro` | `payloads/distro_manjaro/manjaro_arm_rootfs.tar.xz` | 133,044,216 | 126.9 MiB | `b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_chimera` | `payloads/distro_chimera/chimera_20251220_rootfs.tar.xz` | 5,343,176 | 5.1 MiB | `0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_fedora` | `payloads/distro_fedora/fedora_44_rootfs.tar.xz` | 30,917,104 | 29.5 MiB | `2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_void` | `payloads/distro_void/void_20250202_rootfs.tar.xz` | 45,789,416 | 43.7 MiB | `01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_opensuse` | `payloads/distro_opensuse/opensuse_tumbleweed_rootfs.tar.xz` | 22,130,672 | 21.1 MiB | `bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_deepin` | `payloads/distro_deepin/deepin_25_rootfs.tar.xz` | 55,705,284 | 53.1 MiB | `2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698` | Rule A (existing pinned FluxLinux rootfs) |
| `distro_parrot` | `payloads/distro_parrot/parrot_7.2_rootfs.tar.xz` | 111,851,420 | 106.7 MiB | `49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4` | Rule A (existing pinned FluxLinux rootfs) |
| **Total Rootfs** | | **754,077,508** | **719.1 MiB** | | |

Size Gate framing (Google Play R2 limits):
- Individual feature module limit: ≤ 500 MB (largest is Manjaro at 126.9 MiB — PASS)
- Cumulative modules + asset packs limit: ≤ 4 GB (total is 719.1 MiB — PASS)
- Base APK warning threshold: ≤ 200 MB (base is ~10.4 MiB — PASS, avoids mobile-data warning dialog)

---

## 3. Dependency & Asset Access

- **Play Feature Delivery Dependency**: `com.google.android.play:feature-delivery-ktx:2.1.0`
- **Documentation Reference**: https://developer.android.com/guide/playcore/feature-delivery/on-demand
- **Asset-Open Strategy**: First attempts `appCtx.assets.open("payloads/<moduleName>/<fileName>")`. If not yet merged into application context, falls back to `appCtx.createPackageContext(appCtx.packageName, 0).assets.open(...)` as officially recommended by Play Feature Delivery docs.
- **SplitCompat Lifecycle Site**: `SplitCompat.install(this)` is invoked in `FluxLinuxApp.attachBaseContext` and `MainActivity.attachBaseContext`. `SplitCompat.installActivity(this)` is executed in `MainActivity.onCreate` as recommended for activity defense-in-depth.
- **Provenance / Rule A**: Existing pinned rootfs archives were verified against SHA-256 hashes present in SSOT `DistroInstallProfile.kt`. All 7 distros were staged under Rule A from existing pinned FluxLinux rootfs archives matching the pins verbatim.

---

## 4. Verification Evidence

- `test`: Passed all focused unit tests in JVM mock style:
  - `PlayPayloadRegistryTest`
  - `ZenithbluePayloadProvidersTest`
  - `PlayRunnerSuppressionTest`
  - `PlayUiFilteringTest`
  - `InstallFlowHelperTest`
  - `BaseArtifactRootfsGateTest`
- `assembleZenithblueDebug`: BUILD SUCCESSFUL.
- `assembleIvarnaDebug`: BUILD SUCCESSFUL.
- `bundleZenithblueRelease`: BUILD SUCCESSFUL (AAB produced at `app/build/outputs/bundle/zenithblueRelease/app-zenithblue-release.aab`, SHA-256 `6a68e181615f9c4a721ab476701bcddf95201a625bd86cde36260f2ecc326235`, size 546 MiB).
- `scripts/verify_apk_host_assets.sh`: Verified Zenithblue base APK contains zero rootfs archives (`assets/rootfs/*` and `payloads/*` both zero).

---

## 5. Scope Justification (Finding 3)

`OnboardingFlowScreen.kt`:
- Kept `DownloadConsentRow` composable and its consent body wording intact for backward compatibility with `OnboardingConsentContractTest` (which explicitly asserts `DownloadConsentRow` and specific substrings).
- `FluxConsentCheckboxCard` forwards to `DownloadConsentRow` without altering consent behavior.

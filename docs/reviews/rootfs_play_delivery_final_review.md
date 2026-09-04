# RootFS Play Delivery Final Review

> SUPERSEDED by `rootfs_play_delivery_final_closure.md` (final closure on the
> required Poco X6 Pro, 12 modules everywhere, Zenithblue manifest boundary,
> Legacy Termux hidden, flavor-aware Welcome/consent copy). This document is
> kept for history; fields below describe the earlier `9b792db` state unless
> noted. The ~800 MiB cumulative AAB is NOT a blocker: each of the 12
> on-demand modules is < 500 MB compressed and cumulative modules are
> < 4 GB (verified in closure).

Verdict: PASS WITH POLICY DECISION

Branch: feat/oneshot-rootfs-play-delivery
Commit: 9b792dbce88efde107a13715f089a540a9153ab6 (worktree dirty — reviewed worktree, not a new commit)
Worktree clean: false (32 modified + 7 untracked groups; all in review scope except termux-x11 CmdEntryPoint.java and 3 asset scripts — see Findings/MINOR)

Scope note: review doc expected 7 Play modules; worktree implements 12 (adds fedora, void, opensuse, deepin, parrot with matching PlayPayloadRegistry entries, dynamic-feature modules, staged+verified payloads). Treated as in-scope extension, verified same as the 7.

## PFD
Seven modules: EXTENDED — 12 present, all correct (debian, alpine, ubuntu, kali, arch, manjaro, chimera + fedora, void, opensuse, deepin, parrot)
On-demand: yes — all 12 dynamic-feature modules, dist:on-demand, dist:fusing include=false, base declares all 12 dynamicFeatures, features depend on :app
Base rootfs absent: yes — verify script: base contains zero rootfs and zero payloads
Feature payload SHAs: all 12 match DistroInstallProfile SSOT and staged sources (see table)
Staging reproducible: yes — scripts/stage_play_rootfs_features.sh verifies SSOT pin + SHA, copies, fails closed; re-ran UP-TO-DATE x12; negative test with corrupt alpine source → [STOP] SHA-256 mismatch, exit 1; restored, re-ran clean
Runtime HTTP fallback: none on Play path — InstallFlowHelper routes Play-supported distros to ZenithbluePayloadProviders.ensurePresent (PFD only); OnboardingInstallRunner Play branch only does RootfsDownloader.isValid check + no FLUX_ROOTFS_URL export; failure text "Distro download failed. Retry."; no /sdcard, /storage/emulated/0, Download usage
App-private materialization: yes — files/home/<archive>, .partial temp + atomic rename, SHA verify after materialization, bad file deleted, installer not started on failure
SplitInstall race: closed — listener matches by moduleNames() until startInstall success returns sessionId, then pins sessionId; unrelated sessions ignored
REQUIRES_USER_CONFIRMATION: handled — PlayFeatureDelivery emits RequiresUserConfirmation; ZenithbluePayloadProviders surfaces phase "Confirmation required…" with confirmationState; InstallFlowHelper launches startConfirmationDialogForResult via MainActivity; accept continues, cancel → failure/retry path

Feature payload SHAs (AAB-verified):
module | asset path | bytes | SHA-256
distro_debian | distro_debian/assets/payloads/distro_debian/debian_13_rootfs.tar.xz | 85009380 | 13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803
distro_alpine | distro_alpine/assets/payloads/distro_alpine/alpine_3.24_rootfs.tar.gz | 4023732 | f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259
distro_ubuntu | distro_ubuntu/assets/payloads/distro_ubuntu/ubuntu_26.04_rootfs.tar.xz | 20734792 | e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc
distro_kali | distro_kali/assets/payloads/distro_kali/kali_2026_2_rootfs.tar.xz | 123244844 | 01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689
distro_arch | distro_arch/assets/payloads/distro_arch/archlinux_arm_rootfs.tar.xz | 116277544 | 40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75
distro_manjaro | distro_manjaro/assets/payloads/distro_manjaro/manjaro_arm_rootfs.tar.xz | 133044216 | b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156
distro_chimera | distro_chimera/assets/payloads/distro_chimera/chimera_20251220_rootfs.tar.xz | 5343176 | 0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c
distro_fedora | distro_fedora/assets/payloads/distro_fedora/fedora_44_rootfs.tar.xz | 30917104 | 2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4
distro_void | distro_void/assets/payloads/distro_void/void_20250202_rootfs.tar.xz | 45789416 | 01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6
distro_opensuse | distro_opensuse/assets/payloads/distro_opensuse/opensuse_tumbleweed_rootfs.tar.xz | 22130672 | bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a
distro_deepin | distro_deepin/assets/payloads/distro_deepin/deepin_25_rootfs.tar.xz | 55705284 | 2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698
distro_parrot | distro_parrot/assets/payloads/distro_parrot/parrot_7.2_rootfs.tar.xz | 111851420 | 49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4

## Zenithblue hardening
Chroot hidden Home: yes — isPlay filter drops chroot cards, MethodTabs hidden, device Home shows Alpine PROOT only, no tabs
Chroot hidden Distro: yes — availableDistros forces prootSupported on Play, MethodTabs hidden
Chroot hidden Terminal: yes — chroot group filtered, MethodTabs hidden; device Terminal shows PROOT rows only, Alpine User/Root enabled, rest "Install X in Distros"
Chroot hidden Onboarding: yes — DistroPick proot-only, no Chroot tab (device Choose Distribution shows PROOT chips only)
Chroot hidden Settings: yes — Chroot nav card gated by !isPlay
Direct bypass: none found — no exported chroot activity; fluxlinux://callback actions generic (distro_uninstall_<id>, setup_termux, legacy ping); chroot reachable only via hidden UI
ACCESS_SUPERUSER in merged Play manifest: PRESENT in base AndroidManifest.xml (line 13) → applies to Zenithblue merged manifest; classify as policy-hardening issue (see Findings/MAJOR)
External APK/runtime CTA: renamed — "Download v0.118.3 (GitHub)" → "Download v0.118.3"; "from F-Droid or GitHub releases" → "from the official releases" (Prerequisites x3 + LegacyTermux); buttons still open official Termux:X11 releases URL (external APK CTA remains functionally — see Findings/MAJOR)
GitHub/F-Droid Play-visible text: clean — phase "Preparing host…" (was “…from GitHub”), consent "Package Notice / Play Feature Delivery", no Play-visible GitHub/F-Droid strings; remaining hits are KDoc/comments/URLs, Ivarna-only branches

## Build
test: PASS — ./gradlew --no-daemon test (1139 tasks) BUILD SUCCESSFUL; targeted zenithblue+ivarna suites pass
assembleZenithblueDebug: PASS
assembleIvarnaDebug: PASS
bundleZenithblueRelease: PASS — app-zenithblue-release.aab 838898809 bytes
AAB SHA-256: 4840fb9bd9772ed9fdb589113459ca326087232cbf9794aa11c8a8dd7c5b5bbd

Play size (payloads only, TOTAL 754071580 bytes / 719.1 MiB; AAB 838898809 bytes / ~800 MiB):
alpine 3.8 MiB; chimera 5.1 MiB; ubuntu 19.8 MiB; opensuse 21.1 MiB; fedora 29.5 MiB; void 43.7 MiB; deepin 53.1 MiB; debian 81.1 MiB; parrot 106.7 MiB; arch 110.9 MiB; kali 117.5 MiB; manjaro 126.9 MiB
Note: cumulative AAB far exceeds install-time limits but ships as on-demand dynamic features; per-module on-demand delivery keeps install base small. Confirm Play Console accepts per-module sizes (manjaro 126.9 MiB, kali 117.5 MiB largest).

## Device
Device: REQUIRED Poco X6 Pro 2311DRK48I / Y5WWBMJVOZSK4HU8 — NOT FOUND (offline). SUBSTITUTE: OPD2403 (7d6afed8), Android 16, arm64-v8a. Fresh uninstall → bundletool local-testing APKS install OK (1.8 GB local-testing apks; splitcompat cleanup warning benign on fresh install).
bundletool local-testing: PASS — build-apks + install-apks success
Alpine module install: PASS — onboarding: consent (Play-safe) → host READY → Choose Distribution (PROOT only) → Alpine → "Downloading distro…" → module INSTALLED → "Rootfs ready (alpine_3.24_rootfs.tar.gz)" → "SHA256 OK" → "Finished installation. Install Successful!" → XFCE 288 pkgs → "You're Ready! Alpine base desktop is installed. PROOT GUEST"
Alpine rootfs SHA: f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259 — device log "SHA256 OK" + AAB/staged SHA match; run-as unavailable (release not debuggable), functional proof via Home Alpine+Start and Terminal Alpine User/Root enabled
Installer continued: yes — full XFCE + "You're Ready", Home dashboard Alpine installed
Retry: unit/fake tested — InstallFlowHelper/OnboardingInstallRunner emit "Distro download failed. Retry."; targeted unit suites pass. No forced real on-device PFD failure performed (would need module uninstall plumbing); real retry-after-failure not observed on device.
Confirmation: unit/fake handling tested (PlayFeatureDeliveryTest REQUIRES_USER_CONFIRMATION); real Play-backend confirmation not observed (local-testing small module installs without confirmation)

## Policy decision
Automatic guest package downloads: YES — initial setup runs apk update/add (Alpine observed: dl-cdn.alpinelinux.org, 28546 packages, 54 base + 288 XFCE pkgs) and apt update/install (Debian family scripts). Classification: Policy decision remains (per review rules, not a PFD correctness fail). Additional note: onboarding "delivered through Play Feature Delivery" is true for rootfs/host payloads but guest distro packages come from upstream repos — disclosure accuracy caveat.
Notes: Welcome page still shows marketing copy "PRoot & Chroot … via Chroot on rooted devices" (Play-visible Chroot mention, MINOR); consent header "External Downloads" retained though body is Play-accurate (MINOR).
OWNER REQUIREMENT (added post-review): all guest packages must be baked into the rootfs — zero runtime apk/apt/dnf/pacman downloads — working for both proot and chroot. CURRENT STATUS: NOT MET. Device evidence above proves Alpine downloads ~342 packages at install time. Meeting this requires rebuilding every rootfs archive with packages pre-installed, new SHA pins in DistroInstallProfile, re-staging, re-verification, and a fresh end-to-end device test. That work is explicitly out of review scope (review forbids rootfs rebuilds) and is NOT done.

## Findings
CRITICAL: none
MAJOR:
- ACCESS_SUPERUSER remains in base manifest → present in Zenithblue merged manifest; remove or scope to Ivarna sourceSet for Play hardening
- Termux:X11 external APK CTA remains functionally (renamed button still opens official releases APK download; Prerequisites/Legacy flows); confirm Play stance on external runtime installer guidance
MINOR:
- Unrelated production changes in worktree vs 9b792db: termux-x11 CmdEntryPoint.java, 3 asset scripts (start_gui_chroot.sh, flux_install.sh, start_gui.sh) — not reviewed (out of scope), flag for separate review
- Review-doc drift: 12 Play modules implemented vs 7 specified; PlayPayloadRegistry chroot→proot aliases make supports(chrootId)==true on Play (UI hides chroot so no normal-UI path, but alias keeps direct-call resolution); recommend either document or restrict contains() to proot ids on Play
- Welcome "PRoot & Chroot" marketing copy + "External Downloads" header remain Play-visible; consider Play-neutral wording
- AAB ~800 MiB cumulative; confirm Play Console per-module/on-demand acceptance for 100+ MiB modules

## Final recommendation
TECHNICALLY CORRECT BUT POLICY DECISION REMAINS (equivalent to PASS WITH POLICY DECISION): PFD implementation, payloads, AAB, and Alpine end-to-end on device all pass; Zenithblue Chroot UI fully hidden with no reachable bypass; remaining items are the accepted guest-package-download policy question plus the two MAJOR hardening follow-ups above. Ivarna regression: code/test level retained (Chroot UI, non-Play wording/behavior), targeted ivarna unit suite BUILD SUCCESSFUL; no full Ivarna device test (none required).

Code changes made during this review: none (review-only; device test used the worktree as-is). Prior worktree changes (chroot hiding, policy text, 5 extra Play modules, confirmation handling, session-race fix) were reviewed, not authored here.

Owner direction recorded: do NOT merge until the bake-in requirement above is implemented and re-verified. Under review verdict rules alone this would be PASS WITH POLICY DECISION; under the owner's bake-in requirement the release is FIX BEFORE MERGE.

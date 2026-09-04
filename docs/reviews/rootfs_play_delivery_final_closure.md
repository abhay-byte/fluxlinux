# RootFS Play Delivery Final Closure

Result: PASS

Starting commit: a4a97e82df32e944c496719b610d50ec1af63cc3
Final commit: (this commit — `fix(playstore): close final PFD policy surface`)
Worktree clean: true at commit

Required device was available and used — no substitution.

## PFD
Modules: 12 (debian, alpine, ubuntu, kali, arch, manjaro, chimera, fedora, void, opensuse, deepin, parrot — all on-demand, fusing=false)
Clean staging ordering: PASS — deleted all `distro_*/src/zenithblue/assets/payloads/`, ran only `./gradlew clean bundleZenithblueRelease`; root `build.gradle.kts` `projectsEvaluated` block orders every `:distro_*:merge*Zenithblue*Assets / pre*Zenithblue*Build` after `:app:stagePlayRootfsFeatures`. Build auto-staged all 12 and produced the AAB with no manual step. Ivarna tasks unaffected (name-matched on Zenithblue only).
Base rootfs: 0 rootfs, 0 payloads (verify script)
Feature SHAs: 12/12 match DistroInstallProfile SSOT (alpine f55a90f6…259, debian 13e29f60…803, ubuntu e648a530…efc, kali 01c48a29…689, arch 40209ef6…75, manjaro b7339bcc…156, chimera 0900e3f2…a6c, fedora 2d89fe43…1bd, void 01a30f17…3ce, opensuse bdcb8522…a12a, deepin 2c7abfe8…698, parrot 49f4c289…94d)
AAB SHA-256: 307105edebb5056bff60c3118e862b8e88ab118b241091d9cc235bccad14972c
AAB bytes: 838899721
Size sanity: largest module manjaro 126.9 MiB < 500 MB; cumulative modules 0.70 GiB < 4 GB; module count 12. Cumulative AAB size is not a blocker (on-demand delivery).

## Zenithblue
ACCESS_SUPERUSER: absent (merged release manifest; `tools:node="remove"` in `app/src/zenithblue/AndroidManifest.xml`; merger report shows REJECTED)
RUN_COMMAND: absent (same boundary)
Termux query: absent (`com.termux` package query REJECTED)
Termux:X11 query: absent (`com.termux.x11` package query REJECTED)
Note: only remaining Play-reachable consumer was the deferred legacy-external KDE driver (explicit `com.termux` RUN_COMMAND intents), which cannot work on a Play device without sideloaded Termux and fails gracefully (caught → toast). Embedded terminal, embedded X11 (own-package broadcasts), and PFD install verified working without these entries. Ivarna merged manifest retains all four entries.
Chroot Home: hidden — dashboard shows installed Alpine PROOT + Start, no MethodTabs
Chroot Distro: hidden — catalog PRoot-only, no tabs (code gate unchanged, verified this round in onboarding DistroPick: PROOT chips only)
Chroot Terminal: hidden — drawer lists all 12 distros as PROOT rows, Alpine User/Root enabled, rest "Install X in Distros", no chroot group, no tabs
Chroot Onboarding: hidden — DistroPick PROOT-only (Debian/Alpine/Fedora/Void/openSUSE/Deepin/Chimera/… visible, no Chroot tab)
Chroot Settings: hidden — cards are Terminal, X11 Display, Audio, PRoot only
Legacy Termux Settings: hidden — card removed on Play; `Screen.SETTINGS_LEGACY_TERMUX` route guard redirects to Settings on Play; screen file kept for Ivarna
External APK/runtime CTA: none reachable on Play — f-droid.org/com.termux, termux-x11/releases, "Download v0.118.3", "Get X11" live only in LegacyTermuxSettingsScreen (unreachable) and dead PrerequisitesScreen (zero call sites); Play-reachable links are docs/credits/community URLs only
Welcome copy: "PRoot Linux / Run full Linux distributions without root." (Ivarna keeps "PRoot & Chroot")
Consent title: "Linux Setup" (Ivarna keeps "External Downloads"); body: "The base Linux rootfs is delivered through Google Play Feature Delivery. Additional Linux packages may be installed from the selected distribution's package repositories during setup."; checkbox: "…whose base rootfs is delivered with this app via Play; additional Linux packages may come from the distro's own package repositories."

## Build
test: PASS (`./gradlew test`, 1538 tasks, BUILD SUCCESSFUL)
Zenithblue debug: PASS (`assembleZenithblueDebug`)
Ivarna debug: PASS (`assembleIvarnaDebug`)
Zenithblue release: PASS (`bundleZenithblueRelease` from clean, auto-staged)

## Device
Required device: Poco X6 Pro 2311DRK48I
Serial: Y5WWBMJVOZSK4HU8
bundletool local-testing: PASS (`build-apks --local-testing` → `/tmp/fluxlinux-final-review.apks`, `install-apks` Success; on-device `dumpsys` shows no SUPERUSER/RUN_COMMAND grants, only in-app `com.termux.x11.*` components)
Alpine module: PASS — fresh onboarding: consent → host READY → Alpine → "Downloading Alpine rootfs…" → module INSTALLED → "Rootfs ready (alpine_3.24_rootfs.tar.gz)" → materialized app-private → installer continued → "You're Ready! Alpine base desktop is installed. PROOT GUEST"
Alpine SHA: f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259 ("SHA256 OK" on device, matches AAB/staged/SSOT)
Installer continued: yes — 54 base + 288 XFCE packages, customization, Start Desktop / Open Terminal / Go to Dashboard; embedded Alpine shell opens and runs
Retry evidence: unit/fake proven (PlayFeatureDeliveryTest, InstallFlowHelper/OnboardingInstallRunner failure→"Distro download failed. Retry." + Retry Setup UI observed on device). Real-backend forced failure: not exercised (no permanent debug switches per plan). Note: one observed on-device failure ("Setup Failed … Distro download failed") was a test-harness artifact — `pm clear` wiped bundletool local-testing split staging; recovered by reinstall (no pm clear) + `bmgr wipe` of the app's backup dataset to stop backup-state restore. Product retry UI behaved correctly.
Confirmation evidence: unit/fake handling PASS (REQUIRES_USER_CONFIRMATION covered); real Play-backend confirmation: not observed (local-testing installs without confirmation)

## Ivarna regression
Chroot: present (code gates are `!isPlay`; Ivarna merged manifest keeps ACCESS_SUPERUSER; `com.ivarna.fluxlinux` on the same Poco shows PRoot/Chroot tabs — also served as the negative control proving the Play gates are package-flavor-driven)
Legacy Termux: present (card + route intact on Ivarna)
Build: `assembleIvarnaDebug` PASS; full `test` PASS; Ivarna consent/Welcome copy unchanged ("External Downloads", "PRoot & Chroot", GitHub/F-Droid wording intact)

## Separate policy decision
Automatic guest package downloads: YES, still occur — Poco final run: `apk update` (dl-cdn.alpinelinux.org, 28546 pkgs), 54 base + 288 XFCE + customization pkgs, plus `git clone` (Oh My Zsh). Rootfs archives themselves are NOT rebuilt in this worker (per plan). Owner direction recorded in prior review stands: bake-in of all guest packages is a future release work item (requires rootfs rebuilds + new SHA pins + re-verification), NOT done here. No claim is made that guest packages are Play-delivered.

## Remaining blockers
None for this closure. PASS criteria 1–17 all met (15+16 on the required Poco, not a substitute).

# v1.8.0

## What's Changed

### Features
- **T1**: Add uninstall option for feature packages (closes GH-11)
- **T1.1**: Make evince/xournalpp install non-fatal in office
- **T3**: Foreground service keeps local install server alive (closes GH-9)
- **T4**: Disclaimer banner on Termux/X11 install checks (closes GH-8)
- **T6**: Monochrome icon as single-colour vector for themed icons (closes GH-2)
- **T9**: Add uninstall branches to Termux KDE + KDE customisation scripts
- Enable Termux Native card with components and script wiring
- Add native Termux GUI scripts (XFCE4, KDE, VirGL, Turnip) + research doc
- Upgrade system Mesa to 26.2.0-devel on Adreno/Turnip path and pin to prevent apt downgrade
- Add SHA256 verification, corrupt APK handling, and scroll/layout fixes to onboarding
- Add Play Store Termux version warning in onboarding wizard
- Concurrent installation prevention with user cancel
- Add Discord card, remove theme/auto-update options, and force dark mode

### Bug Fixes
- **T7**: Harden Termux XFCE setup
- **T8**: Hide broken uninstall button on Termux XFCE4 customisation
- **T10**: Fix Termux KDE launch and customisation
- **T11**: Dark splash screen in dark theme
- Stop Re-run Base Install from locking the UI in Busy mode by removing it from the task queue
- Deploy stop_xfce4_termux.sh from assets before running it; add exit 0/1 to preferences script
- Auto-detect GPU backend in start scripts when gpu_config is missing — Adreno/Snapdragon now correctly uses Turnip
- Correct Mesa upgrade block structure — use --fail on curl, move inside turnip path
- Add Chromium sandbox/GPU wrapper for PRoot to fix GPU process SIGABRT crashes
- Add Firefox sandbox wrapper for PRoot to fix SIGSEGV child process crashes
- Start virgl_test_server_android in proot start_gui.sh and fix XDG_RUNTIME_DIR/VTEST_SOCKET_NAME
- Export XDG_RUNTIME_DIR in .zshrc to suppress fastfetch error in PRoot/chroot
- Install locales package and configure en_US.UTF-8 for XFCE and KDE customization
- Fix 'script not found' on XFCE4 launch
- Fix script execution and download issues
- Update onboarding continue button visibility and step labels
- Add vertical scroll to onboarding screens for high-DPI devices
- Anchor cancel button to bottom and center scrollable content in InstallationProgressScreen
- Badge alignment in README and scroll issue in InstallationProgressScreen
- Build errors from removed Settings dependencies

### Refactoring
- Restructure scripts/ into per-distro hierarchy with setup/start/stop/addon sub-folders

### Maintenance
- Add kdenlive tutorial project file
- Update turnip driver version to 26.2.0-devel-20260610
- Remove IntelliJ IDEA Community from appdev setup script
- Remove Antigravity installation from webdev setup script
- Close T2 (concurrent install prevention) - already implemented
- Add video tutorials section to readme
- Onboarding + prereqs landscape layout; rm x11 sha check
- Fine-tune F-Droid badge height

### Documentation
- Add mandatory error handling patterns to script guides
- Add scripts_usage_guide.md and adding_new_distro.md
- Add Debian PRoot setup guide
- Add Debian Chroot setup tutorial with images
- Add setup guide for FluxLinux under tutorial
- Add mermaid flowchart of script execution lifecycle
- Add script locations reference document
- Update README badges and Discord link
- Reformat tutorial screenshots into descriptive tables
- Enhance setup_fluxlinux_non-root.md readability and design

## Items Shipped
- **T1**: Add uninstall option for feature packages
- **T2**: Prevent concurrent feature package installs (already implemented — verified closed)
- **T3**: Foreground service to keep local server alive during OS install
- **T4**: Disclaimer banner on Termux/X11 install checks
- **T6**: Add monochrome icon for Android theming
- **T7**: Review and fix XFCE customisation script
- **T8**: Termux customisation + hardware-accel components missing uninstall button
- **T9**: Add uninstall branch to Termux component setup scripts
- **T10**: KDE Plasma launch broken on Termux
- **T11**: Splash screen white in dark theme

## Migration Notes
No breaking changes. No migration required.

## Verification
- All version-bearing files updated to 1.8.0 (versionCode 10): `app/build.gradle.kts`, `com.ivarna.fluxlinux.yml`, `fastlane/README.md`.
- 84 commits since v1.7, 10 todo items shipped (T1, T2, T3, T4, T6, T7, T8, T9, T10, T11).

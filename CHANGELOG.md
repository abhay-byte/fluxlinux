# Changelog

## v1.8.0

### Features
- feat: add uninstall option for feature packages (T1, closes GH-11)
- feat: make evince/xournalpp install non-fatal in office (T1.1)
- feat: foreground service keeps local install server alive (T3, closes GH-9)
- feat: disclaimer banner on Termux/X11 install checks (T4, closes GH-8)
- feat: monochrome icon as single-colour vector for themed icons (T6, closes GH-2)
- feat: add uninstall branches to Termux KDE + KDE customisation scripts (T9)
- feat: enable Termux Native card with components and script wiring
- feat: add native Termux GUI scripts (XFCE4, KDE, VirGL, Turnip) + research doc
- feat: upgrade system Mesa to 26.2.0-devel on Adreno/Turnip path and pin to prevent apt downgrade
- feat: add SHA256 verification, corrupt APK handling, and scroll/layout fixes to onboarding
- feat: add Play Store Termux version warning in onboarding wizard
- feat: concurrent installation prevention with user cancel
- feat: add Discord card, remove theme/auto-update options, and force dark mode

### Bug Fixes
- fix: harden Termux XFCE setup (T7)
- fix: Termux KDE launch and customisation (T10)
- fix: dark splash screen in dark theme (T11)
- fix: hide broken uninstall button on Termux XFCE4 customisation (T8)
- fix: stop Re-run Base Install from locking the UI in Busy mode by removing it from the task queue
- fix: deploy stop_xfce4_termux.sh from assets before running it; add exit 0/1 to preferences script
- fix: auto-detect GPU backend in start scripts when gpu_config is missing — Adreno/Snapdragon now correctly uses Turnip
- fix: correct Mesa upgrade block structure — use --fail on curl, move inside turnip path
- fix: add Chromium sandbox/GPU wrapper for PRoot to fix GPU process SIGABRT crashes
- fix: add Firefox sandbox wrapper for PRoot to fix SIGSEGV child process crashes
- fix: start virgl_test_server_android in proot start_gui.sh and fix XDG_RUNTIME_DIR/VTEST_SOCKET_NAME
- fix: export XDG_RUNTIME_DIR in .zshrc to suppress fastfetch error in PRoot/chroot
- fix: install locales package and configure en_US.UTF-8 for XFCE and KDE customization
- fix(termux): fix 'script not found' on XFCE4 launch
- fix(termux): fix script execution and download issues
- fix: update onboarding continue button visibility and step labels
- fix: add vertical scroll to onboarding screens for high-DPI devices
- fix: anchor cancel button to bottom and center scrollable content in InstallationProgressScreen
- fix: badge alignment in README and scroll issue in InstallationProgressScreen
- fix: build errors from removed Settings dependencies

### Refactoring
- refactor: restructure scripts/ into per-distro hierarchy with setup/start/stop/addon sub-folders

### Maintenance
- chore: add kdenlive tutorial project file
- chore: update turnip driver version to 26.2.0-devel-20260610
- chore: remove IntelliJ IDEA Community from appdev setup script
- chore: remove Antigravity installation from webdev setup script
- chore: close T2 (concurrent install prevention) - already implemented
- chore: add video tutorials section to readme
- chore: onboarding + prereqs landscape layout; rm x11 sha check
- chore: fine-tune F-Droid badge height

### Documentation
- docs: add mandatory error handling patterns to script guides
- docs: add scripts_usage_guide.md and adding_new_distro.md
- docs: add Debian PRoot setup guide
- docs: add Debian Chroot setup tutorial with images
- docs: add setup guide for FluxLinux under tutorial
- docs: add mermaid flowchart of script execution lifecycle
- docs: add script locations reference document
- docs: update README badges and Discord link
- docs: reformat tutorial screenshots into descriptive tables
- docs: enhance setup_fluxlinux_non-root.md readability and design

### UI / Style
- style: align and resize F-Droid and Play Store badges in README.md
- style: enhance setup_fluxlinux_non-root.md readability and design
- Add Tutorials card to Settings screen
- Add Step 10: Help and Support with Docs/Discord
- Use actual image icons for instructions table
- Remove redundant spacer between Settings cards
- Allow navigating back to Installation Progress screen
- Update docs links to use separate distro guides with proper image icons
- Create README.md index for docs/tutorial folder

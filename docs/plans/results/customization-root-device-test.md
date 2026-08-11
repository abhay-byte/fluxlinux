# Device test: XFCE4 Customization as root (proot)

## Fix under test
- Component install sessions now use `--user root` (was `flux` → dpkg "requires superuser").
- Host stages themes + Oh My Zsh before Distro Settings customization.
- Guest script: root check, silent chown, git+zsh apt deps, no empty OMZ tree after fail.

## Package
- `com.ivarna.fluxlinux`
- APK: `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk`

## Pass criteria
1. `adb shell` can run proot login as root and `id -u` prints `0`.
2. Customization guest path does **not** fail with `requested operation requires superuser privilege`.
3. Prefer end-to-end: trigger install or simulate same command as app component session:
   - Host prep optional; guest script under root should run `apt-get` without dpkg superuser error.
4. Look for: `Installing customization tools (as root)`, then progress past Dependency Installation.
5. Ideally reach `Customization Complete!` or at least clear `Oh My Zsh pre-installed` / valid skip without hang on corrupt removal.
6. No flood of unhandled chown Operation not permitted (stderr silenced).
7. Write results to this file under `## Results`.

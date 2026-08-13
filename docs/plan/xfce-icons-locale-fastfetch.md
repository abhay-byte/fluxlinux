# Plan: XFCE icons + Fedora locale + fastfetch disks (follow-up)

**Date:** 2026-08-13  
**Status:** PLAN ONLY — device-diagnosed, not implemented.  
**Parents:** [`fedora-void-opensuse.md`](./fedora-void-opensuse.md) (FVO still DEVICE PASS for I1–D3/D5/T2/T3), [`deepin-chimera-manjaro.md`](./deepin-chimera-manjaro.md) (DCM DEVICE PASS; Manjaro D4 icons incomplete).  
**Package:** `com.ivarna.fluxlinux`. **APK:** `assembleIvarnaRelease` + `adb install -r` only.

**Not in this plan:** Ubuntu/Kali/Parrot/Arch. PulseAudio. First-X SIGSYS. Stale Terminal tabs. DDE/KDE.

---

## 0. What the user saw (2026-08-13, device `Y5WWBMJVOZSK4HU8`)

| Report | Card | Symptom |
|--------|------|---------|
| A | Fedora **proot** User | `setlocale: LC_ALL: cannot change locale (en_US.UTF-8)` ×4; fastfetch prints **dozens** of read-only Android `/apex` disks; `PKG › 0 (pacman)` on Fedora |
| B | Fedora **chroot** Root | Same locale warnings; prompt `[root@localhost]~#` (bash Root tab — see §1.4) |
| C | Manjaro **proot** XFCE | Applications menu: Settings / Accessories / Internet show **⊘** (missing icon) |
| D | openSUSE **proot** XFCE | Same ⊘ on those categories |
| E | Manjaro **chroot** + openSUSE **chroot** | Same icon defect |
| F | openSUSE **chroot** Root tab | `tty: ttyname error: Inappropriate ioctl for device` then `localhost:/ #` |

---

## 1. Root causes (adb, not screenshots)

### 1.1 Locale (Fedora proot + chroot)

```
$ ls …/fedora/rootfs/usr/lib/locale
C.utf8          # ONLY this
$ cat …/fedora/rootfs/etc/locale.conf
LANG="C.UTF-8"
```

openSUSE already has `C.utf8` **and** `en_US.utf8`. Fedora container image has **no** `glibc-langpack-en` and the family script never runs `localedef`.

Everything still **exports** `en_US.UTF-8`:

- `setup_customization_xfce.sh` `.zshrc` lines `export LANG=en_US.UTF-8` / `LC_ALL=en_US.UTF-8`
- `GuestZshrcRepair.DEFENSIVE_ZSHRC` same
- `ProotCommandBuilder.guestLoginEnv` — `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8` on **every** proot login (user and root)
- `ChrootCommandBuilder.buildEnv` — same for every chroot session

`/bin/sh` and `/usr/bin/sh` then print `cannot change locale` before zsh even starts. That is the four-line spam.

**Fix:** generate `en_US.UTF-8` inside the guest **before** any login. Do not stop exporting `en_US` from the app (agnosterzak needs it). Fallback in `.zshrc` only if generation failed.

### 1.2 Fastfetch disk flood + `PKG › 0 (pacman)`

Fedora proot `~/.local/share/fastfetch/presets/termux.jsonc` (mtime 02:53) is the **old GitHub** preset:

- `"type": "disk"` with **no** `folders: ["/"]` → every Android bind mount in `/proc/mounts`
- `"format": "{} (pacman)"` → Fedora reports `0 (pacman)`

Shared `setup_customization_xfce.sh` **already** writes a filtered preset (`folders: ["/"]`, no hardcoded pacman). Fedora/Void/Alpine/Debian **on device** still have the old file because:

- customization ran before that write, **or**
- `GuestZshrcRepair` rewrites `.zshrc` (fedora proot `.zshrc` 15:22) **without** rewriting the jsonc

`fastfetch --config termux` therefore keeps using the stale preset.

**Fix:** always overwrite the preset in customization **and** in `GuestZshrcRepair` (flux + root homes). Never curl the old Linux_Setup `termux.jsonc`.

### 1.3 XFCE ⊘ category icons (Manjaro + openSUSE, proot + chroot)

Two independent bugs stacked.

**Bug I — shipped Papirus is a stub.**  
`app/src/main/assets/xfce4/icons/papirus-dark-only.tar.xz` is **276 KiB**. Listing:

| Path | Real files |
|------|------------|
| `16x16/actions`, `22x22/actions`, `24x24/actions` | ~2300 each |
| `16x16/places`, `16x16/devices` | ~160–190 |
| `24x24/apps`, `24x24/categories`, `48x48/*` | **empty dirs** (or `48x48` → dangling `../Papirus/48x48`) |

XFCE Applications menu looks up **`applications-accessories`**, **`applications-internet`**, **`applications-system`**. They are not in the pack → GTK `image-missing` → ⊘.

`index.theme` still says `Inherits=breeze-dark,hicolor`. **breeze-dark is not installed** on any guest.

**Bug II — Fedora/openSUSE never even select Papirus.**  
`setup_customization_xfce.sh`:

```sh
SEL_ICON="Papirus-Dark"
if dnf || dnf5 || zypper; then
    SEL_ICON="Adwaita"    # glycin / incomplete-tree workaround
fi
```

Device:

| Guest | `IconThemeName` | Papirus extracted? |
|-------|-----------------|--------------------|
| Manjaro proot/chroot | `Papirus-Dark` | yes, stub |
| openSUSE proot/chroot | **`Adwaita`** | yes, unused |
| Fedora proot/chroot | **`Adwaita`** | yes, unused |
| Deepin proot | `Papirus-Dark` | stub (same ⊘ risk) |

Modern **Adwaita** only has symbolic `applications-system-symbolic`. Real category icons live in:

- Fedora: `AdwaitaLegacy/24x24/legacy/applications-*.png` (wrong subdir — GTK does not treat `legacy/` as `categories/`)
- openSUSE: `adwaita-xfce/scalable/categories/applications-*.svg` (Adwaita does not inherit this automatically)
- Manjaro Adwaita: almost no category icons at all

So selecting Adwaita on Tumbleweed/Fedora **still** shows ⊘. Completeness check today only tests `48x48@2x/status/image-missing.svg` (seeded from Adwaita) — it never checks category icons. `icon_is_installed` is true if `index.theme` exists.

`~/.config/gtk-3.0/settings.ini` is **not** written.

Full theme already sits unused at `assets/xfce4/icons/papirus-icon-theme-20250501.tar.gz` (**31 MiB** gzip). Do **not** ship that `.gz` in the APK (aapt2). Rebuild a richer **xz** dark pack, or install `papirus-icon-theme` from the guest PM.

### 1.4 Fedora/openSUSE “rooted” prompt is not an icon/locale bug

`ChrootCommandBuilder`: flux User → `zsh`; Root → `bash` (Alpine Root → `sh`).

- Fedora chroot `passwd`: `flux` → `/usr/sbin/zsh`, `root` → `/bin/bash` → `[root@localhost]~#` is the **Root** tab.
- openSUSE chroot Root: `localhost:/ #` + `ttyname error: Inappropriate ioctl` is bash/sh in a chroot pty (no real tty). Cosmetic.

**User** tab must be `@flux` + agnosterzak. Do not “fix” Root into a fake user shell. Optional: copy filtered fastfetch + locale to `/root` so Root is quiet.

---

## 2. Product decisions

| Decision | Choice |
|----------|--------|
| Locale | Every glibc family **must** materialize `en_US.UTF-8`. Keep app/zshrc export. `.zshrc` falls back to `C.UTF-8` only if `locale -a` has no en_US. |
| Fastfetch | SSOT preset in customization **and** `GuestZshrcRepair`. Disk = `/` only. Packages = auto (no `(pacman)` format). |
| Icons | **Papirus-Dark** on all XFCE guests (including Fedora/SUSE). Remove the dnf/zypper Adwaita hardcode. |
| Papirus completeness | Category icons required before selecting Papirus. Seed or install a real theme — do not claim D4 pass with ⊘ menus. |
| Inherits | Rewrite Papirus `index.theme` to `Inherits=Adwaita,hicolor` (add `AdwaitaLegacy` / `adwaita-xfce` if those dirs exist). Never leave `breeze-dark` unless breeze is installed. |
| Existing guests | Distro Settings → XFCE4 Customization re-run after the new APK must repair without wipe/reinstall. |
| Root tab | Stay root. Quiet locale + no disk flood. |

---

## 3. Implementation

### 3.1 Locale — family scripts (new installs)

Add `_flux_ensure_en_us_locale` to `flux_guest_common.sh` **and** the three inlined DCM family copies (HostScriptDeployer). Call it from **every** glibc family after packages, before user/zsh:

**Fedora** (`setup_fedora_family.sh`):

```sh
$DNF $DNF_OPTS install glibc-langpack-en || true
if ! ls /usr/lib/locale | grep -qi 'en_US'; then
    localedef -i en_US -f UTF-8 en_US.UTF-8 || true
fi
printf 'LANG=en_US.UTF-8\n' > /etc/locale.conf
```

**openSUSE:** already has `en_US.utf8` on device; still install `glibc-locale` / `glibc-locale-base` if `locale -a` lacks en_US.

**Manjaro:** already has `_flux_ensure_locale` — keep it. Call the shared helper instead of a second copy if you touch common.

**Void / Deepin:** `glibc-locales` / `locales` + `localedef` or `locale-gen` as already sketched for Manjaro.

**Chimera (musl):** do **not** require glibc locales. `.zshrc` fallback must accept `C.UTF-8` without warning.

Verify fail-closed in family: `ls /usr/lib/locale | grep -qi en_US` on Fedora/openSUSE/Manjaro/Deepin/Void or log a hard warning.

### 3.2 Locale — `.zshrc` + launchers (existing + new)

In `setup_customization_xfce.sh` and `GuestZshrcRepair.DEFENSIVE_ZSHRC`, replace the hard export with:

```sh
if locale -a 2>/dev/null | grep -qiE 'en_US\.(utf8|UTF-8)'; then
  export LANG=en_US.UTF-8
  export LC_ALL=en_US.UTF-8
else
  export LANG=C.UTF-8
  export LC_ALL=C.UTF-8
fi
```

Keep `ProotCommandBuilder` / `ChrootCommandBuilder` on `en_US.UTF-8` **after** families generate it. Optional later: detect locale in Kotlin — not required if family is fail-closed.

Write `/etc/locale.conf` and a tiny `/etc/profile.d/flux-locale.sh` with the same if/else so **Root bash** does not inherit a broken `LC_ALL` from the Android env. If the builder still injects `en_US` and the locale exists, Root is silent.

### 3.3 Fastfetch preset (existing + new)

SSOT contents (write every customization + every `GuestZshrcRepair.repairIfNeeded` into `$HOME/.local/share/fastfetch/presets/termux.jsonc` for **flux and root**):

```jsonc
{
  "logo": null,
  "display": { "separator": " ›  " },
  "modules": [
    { "type": "os", "key": "OS  " },
    { "type": "kernel", "key": "KER " },
    { "type": "cpu", "key": "CPU " },
    { "type": "gpu", "key": "GPU " },
    { "type": "packages", "key": "PKG " },
    { "type": "shell", "key": "SH  " },
    { "type": "terminal", "key": "TER " },
    {
      "type": "disk",
      "key": "DSK ",
      "folders": ["/"],
      "showRemovable": false,
      "showHidden": false,
      "showSubvolumes": false
    },
    { "type": "memory", "key": "MEM " },
    { "type": "swap", "key": "SWP " }
  ]
}
```

Rules:

- **No** `"format": "{} (pacman)"`.
- **Always** overwrite (do not skip if file exists).
- `.zshrc` already runs `fastfetch --config termux`. Keep that.
- Delete any curl of `Linux_Setup/.../termux.jsonc` left in Alpine/Debian customization if those paths still run on a guest you touch.

### 3.4 Icons — stop selecting Adwaita on Fedora/SUSE

In `setup_customization_xfce.sh` **delete** the block that sets `SEL_ICON=Adwaita` when `dnf`/`zypper` exist.

Always extract Papirus-Dark (same `papirus-dark-only.tar.xz` path). Then run §3.5 repair. Only then decide `SEL_ICON`.

### 3.5 Icons — make Papirus usable (required)

After extract, in `setup_customization_xfce.sh` (single place — all XFCE guests):

1. **Dangling dirs.** If `48x48` or `48x48@2x` is a symlink whose target is missing (`../Papirus/48x48`), `rm` and `mkdir -p` a real dir. Keep the existing `image-missing.svg` seed.

2. **Inherits.** In `Papirus-Dark/index.theme` (POSIX rewrite, no GNU sed required):
   - `Inherits=Adwaita,hicolor`
   - If `/usr/share/icons/AdwaitaLegacy` exists, `Inherits=Adwaita,AdwaitaLegacy,hicolor`
   - If `/usr/share/icons/adwaita-xfce` exists, add it.

3. **Seed XFCE category icons** into  
   `/usr/share/icons/Papirus-Dark/{24x24,48x48}/categories/`  
   Names (file stem, `.svg` or `.png`):

   `applications-accessories` `applications-development` `applications-games` `applications-graphics` `applications-internet` `applications-multimedia` `applications-office` `applications-other` `applications-science` `applications-system` `applications-utilities` `preferences-desktop` `preferences-system` `xfce-settings`

   Search order per name:

   1. Guest `papirus-icon-theme` / full Papirus if §3.6 installed it  
   2. `adwaita-xfce/scalable/categories/`  
   3. `AdwaitaLegacy/*/legacy/`  
   4. `Adwaita/**/categories/`  
   5. Bundled extras tarball (§3.6)

4. **Completeness gate** (replace the image-missing-only check):

   ```sh
   papirus_xfce_ok() {
     _p=/usr/share/icons/Papirus-Dark
     [ -f "$_p/index.theme" ] || return 1
     for n in applications-internet applications-accessories applications-system; do
       find "$_p/24x24/categories" "$_p/48x48/categories" "$_p/scalable/categories" \
         -name "$n.svg" -o -name "$n.png" 2>/dev/null | grep -q . || return 1
     done
     return 0
   }
   ```

   If `papirus_xfce_ok`: `SEL_ICON=Papirus-Dark`.  
   Else: keep seeding; if still fail, `SEL_ICON=Adwaita` **and** copy the same three names into `Adwaita/24x24/categories/` so Adwaita is not ⊘ either. Log which path you took.

5. **`gtk-update-icon-cache -f`** on Papirus-Dark (best-effort).

6. **Write** `$USER_HOME/.config/gtk-3.0/settings.ini`:

   ```ini
   [Settings]
   gtk-theme-name=Space-transparency
   gtk-icon-theme-name=Papirus-Dark
   gtk-cursor-theme-name=Vimix-white-cursors
   gtk-font-name=JetBrainsMono Nerd Font 10
   ```

   (`Space-light` / `Vimix-cursors` when light theme.)

7. Existing `xsettings.xml` `IconThemeName=$SEL_ICON` stays. Do not write Papirus if the gate failed.

`icon_is_installed` for skip-extract may stay weak. The **gate** decides selection. Re-run must still seed + rewrite Inherits even if extract is skipped (`already installed`).

### 3.6 Richer Papirus source (pick one, in this order)

**Preferred A — guest package** (new installs, complete theme, no APK bloat):

```sh
_flux_pkg_add papirus-icon-theme || _flux_pkg_add papirus-icon-theme-dark || true
```

If `/usr/share/icons/Papirus-Dark/24x24/categories/applications-internet.svg` exists after this, skip asset extract.

**Preferred B — rebuild asset** from `assets/xfce4/icons/papirus-icon-theme-20250501.tar.gz`:

- Produce `papirus-dark-only.tar.xz` that includes **Papirus-Dark** `24x24/categories`, `48x48/categories`, `24x24/apps`, `48x48/apps`, plus the current actions/places.
- Keep it xz (aapt2). Target **under ~3–8 MiB** compressed (categories+apps only, not every size).
- Update `ProotXfceAssetInstaller` comments/size if needed.

**C — tiny extras tarball** `xfce4/icons/papirus-xfce-categories.tar.xz` (~100 KB) with only the stems in §3.5. Extract on top of the stub. Fastest if A/B slip.

Do **not** ship the 31 MiB `.tar.gz` as an APK asset.

### 3.7 Existing guests (no wipe)

After new APK: Distros → gear → **XFCE4 Customization** → Dark → Apply. Script must:

- Not skip seed/Inherits because `icon_is_installed` is true
- Overwrite fastfetch jsonc
- Rewrite `.zshrc` locale block
- Re-write `xsettings.xml` + `settings.ini`

Fedora also needs **family** locale packages. Distro Settings → **XFCE4 Desktop** (family) once, **or** a one-shot locale block at the start of customization (`_flux_pkg_add glibc-langpack-en` / `glibc-locale` / `glibc-locales`) so a customization-only re-run fixes Fedora without a full family reinstall.

### 3.8 Optional ttyname (openSUSE chroot Root)

`ttyname error: Inappropriate ioctl` is the helper pty. Non-blocking. If easy: `export MESG=n` / skip `mesg n` in helper login. Do not block D4 on this.

---

## 4. Unit tests

| File | Assert |
|------|--------|
| New or `OmzPokemonContractTest` | customization does **not** contain `SEL_ICON="Adwaita"` tied to `dnf`/`zypper` |
| same | contains `papirus_xfce_ok` or `applications-internet` seed |
| same | fastfetch jsonc in script has `folders` and `/` and does **not** contain `(pacman)` |
| same | `.zshrc` locale uses `locale -a` / `C.UTF-8` fallback |
| `GuestZshrcRepairTest` | repair writes/updates `termux.jsonc` with `folders` |
| Fedora family string test | `glibc-langpack-en` or `localedef` + `en_US.UTF-8` |

---

## 5. Device matrix (must pass after APK)

Rebuild Ivarna release, `adb install -r`. Device serial used for diagnosis: `Y5WWBMJVOZSK4HU8`.

### 5.1 Existing guests (repair path)

Re-run **Customization** (and Fedora **family** once if locale pkg missing) on:

`fedora`, `fedora_chroot`, `opensuse`, `opensuse_chroot`, `manjaro`, `manjaro_chroot`

Also smoke `deepin` + `void` (same shared script).

| # | Check | Pass |
|---|--------|------|
| L1 | New **User** shell: **zero** `setlocale` / `cannot change locale` lines | |
| L2 | `locale` / `locale -a` shows `en_US.utf8` or `en_US.UTF-8` (glibc) | |
| F1 | fastfetch: **one** `DSK` line (guest `/`), not a page of `/apex` | |
| F2 | Fedora `PKG` is **not** `0 (pacman)` (rpm/dnf count or bare number) | |
| I1 | `xsettings.xml` `IconThemeName=Papirus-Dark` on Manjaro **and** Fedora/openSUSE | |
| I2 | `Papirus-Dark/24x24/categories/applications-internet.{svg,png}` exists | |
| I3 | Applications → Settings / Accessories / Internet: **real icons, no ⊘** | |
| I4 | `gtk-3.0/settings.ini` `gtk-icon-theme-name=Papirus-Dark` | |
| U1 | **User** tab: `@flux` + `echo $ZSH_THEME` → `agnosterzak` | |
| U2 | **Root** tab may stay `#` — no locale spam; do not fail U1 if Root is bash | |

### 5.2 Fresh install (do not skip)

Uninstall **one** small-or-medium guest in-app (prefer Fedora proot **or** openSUSE proot if space; do not wipe Debian/Alpine unless needed) and install from scratch. L1–I4 must pass **without** a second customization click.

### 5.3 Sibling smoke

Shared script change → Deepin proot Start + `sudo -n id` + Applications menu not ⊘.

---

## 6. Key files

| Path | Change |
|------|--------|
| `scripts/common/setup/setup_customization_xfce.sh` | Remove Adwaita hardcode; seed categories; Inherits; gate; gtk settings.ini; locale fallback; always write fastfetch |
| `scripts/common/setup/flux_guest_common.sh` + 3 DCM inlined copies | `_flux_ensure_en_us_locale` |
| `scripts/fedora/common/setup/setup_fedora_family.sh` | `glibc-langpack-en` + localedef + locale.conf |
| `scripts/opensuse/common/setup/setup_opensuse_family.sh` | ensure `en_US.utf8` package if missing |
| `scripts/{void,deepin,manjaro}/…_family.sh` | call shared locale helper (Manjaro already generates) |
| `GuestZshrcRepair.kt` | locale fallback; write fastfetch jsonc for flux+root |
| `ProotXfceAssetInstaller.kt` | only if asset name/size changes |
| `app/src/main/assets/xfce4/icons/papirus-dark-only.tar.xz` | rebuild **or** add categories extras xz |
| Tests | §4 |

---

## 7. Definition of done

- [ ] Fedora proot + chroot User: no setlocale spam; one disk line; PKG not `(pacman)`  
- [ ] Manjaro + openSUSE proot + chroot: Papirus selected; Applications categories have icons  
- [ ] Fresh install of at least one of {fedora, opensuse, manjaro} passes without a second customization  
- [ ] Unit tests §4 green  
- [ ] Ivarna release only; no Ubuntu/Kali/Parrot/Arch work  
- [ ] Update `docs/plans/results/` notes on the guests you retest  

---

## 8. Worker agent prompt (paste)

```
You are the FluxLinux icons/locale/fastfetch worker.

Repo: /home/abhaybyte/repos/fluxlinux
Plan (SSOT): docs/plan/xfce-icons-locale-fastfetch.md
Parents: docs/plan/fedora-void-opensuse.md , docs/plan/deepin-chimera-manjaro.md
Package: com.ivarna.fluxlinux
APK: ./gradlew :app:assembleIvarnaRelease --no-daemon
     adb install -r app/build/outputs/apk/ivarna/release/app-ivarna-release.apk
Device: adb -s Y5WWBMJVOZSK4HU8 (Xiaomi). KernelSU for chroot.
Do not git push. Stop Gradle when done. No debug APK. No APK uninstall unless signature mismatch.

Do NOT start Ubuntu/Kali/Parrot/Arch. Do not flip archlinux. No KDE/DDE/modules.
Do not chmod 0400 doas.conf. Do not default FLUX_SKIP_POKEMON=1.

READ the plan §1 (adb root causes) before coding.

FIX ALL THREE, so a NEW install is clean (not only Distro Settings re-run):

1) LOCALE
   Fedora family: install glibc-langpack-en and/or localedef en_US.UTF-8; write /etc/locale.conf.
   Shared helper in flux_guest_common.sh; if you edit common, copy into the inlined
   tops of setup_{deepin,chimera,manjaro}_family.sh.
   Customization + GuestZshrcRepair: if locale -a has en_US use it, else C.UTF-8.
   Customization should also _flux_pkg_add glibc-langpack-en / glibc-locale / locales
   so an existing Fedora guest is fixed by Customization re-run alone.
   Launchers may keep LANG=en_US.UTF-8 once the locale exists.

2) FASTFETCH
   Always overwrite ~/.local/share/fastfetch/presets/termux.jsonc (flux AND root)
   in setup_customization_xfce.sh AND GuestZshrcRepair.
   disk.folders=["/"]; no format "{} (pacman)". Never curl the old Linux_Setup jsonc.

3) ICONS (shared setup_customization_xfce.sh)
   DELETE the dnf/zypper SEL_ICON=Adwaita hardcode.
   Always extract Papirus-Dark, then:
     - fix dangling 48x48 -> ../Papirus/48x48
     - rewrite Inherits=Adwaita,hicolor (+ AdwaitaLegacy / adwaita-xfce if present)
     - seed applications-internet/accessories/system (and the full list in plan §3.5)
       into Papirus-Dark/24x24/categories and 48x48/categories
     - Prefer _flux_pkg_add papirus-icon-theme if the package has those SVGs.
       Else rebuild papirus-dark-only.tar.xz from
       assets/xfce4/icons/papirus-icon-theme-20250501.tar.gz (categories+apps, xz only)
       or add a tiny papirus-xfce-categories.tar.xz. Do NOT ship the 31MiB .tar.gz.
     - Gate: those three names must exist or do not set IconThemeName=Papirus-Dark
       (if falling back to Adwaita, seed the same names into Adwaita/24x24/categories).
   Write gtk-3.0/settings.ini. Re-run must seed/rewrite even if icon_is_installed is true.
   Manjaro/openSUSE/Fedora/Deepin/Void all use this script — do not special-case only one.

Root chroot tab staying bash/# is OK. User tab must be @flux + agnosterzak.
ttyname ioctl on openSUSE Root is optional.

VERIFY (adb -s Y5WWBMJVOZSK4HU8, no vision for pty — redirect to files):
  New User shell: no "cannot change locale"
  locale -a | grep -i en_US   (glibc guests)
  fastfetch: one DSK line; Fedora PKG not "(pacman)"
  grep IconThemeName …/xsettings.xml → Papirus-Dark  (fedora, opensuse, manjaro × proot+chroot)
  ls …/Papirus-Dark/24x24/categories/applications-internet.*
  Applications menu: Settings/Accessories/Internet have icons, not ⊘
  Fresh install at least fedora or opensuse or manjaro proot: same checks without a second customization.

Unit tests: plan §4.
Shared-script change: smoke Deepin proot Start + sudo -n id + menu icons.
Update docs/plans/results/* for guests you retest.
Do not claim D4 pass if ⊘ remain.
```

---

*Diagnosed on device 2026-08-13. Implement from this file; do not re-litigate FVO/DCM I1–D5 except D4 icons and T1 locale/fastfetch.*

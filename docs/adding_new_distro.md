# Adding a New Distro to FluxLinux Pro

This guide walks through every step needed to make a new Linux distribution appear on the FluxLinux Pro Home and Distros screens — from scripts to UI registration — covering PRoot, Chroot, install, start, stop, CLI, and root CLI.

---

## Overview: How a Distro Works

Each distro entry in the app is a `Distro` object that links:

```
DistroRepository.Distro
├── id                 → used in all script dispatch logic
├── prootSupported     → enables rootless install path
├── chrootSupported    → enables rooted/chroot install path
├── configuration      → links to SupportedDistro enum (selects base script)
└── components         → list of DistroComponent (optional install steps shown in UI)
```

At runtime, [`TermuxIntentFactory`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/data/TermuxIntentFactory.kt) uses the `distro.id` to dispatch the correct scripts for install, start, stop, CLI, and root CLI.

---

## Step-by-Step: Adding a New Distro

### Step 1 — Create Scripts

Create the required scripts under `app/src/main/assets/scripts/`. The minimum set depends on the modes you want to support.

#### A. PRoot (Rootless) Mode

| Script | Location | Purpose |
|---|---|---|
| `setup_<distro>_family.sh` | `<distro>/common/setup/` | Installs desktop environment inside the container |
| `start_gui.sh` *(reuse existing)* | `debian/proot/start/` | Already handles XFCE4 launch for all PRoot distros |
| `start_gui_kde.sh` *(reuse existing)* | `debian/proot/start/` | Already handles KDE launch for all PRoot distros |
| `stop_gui.sh` *(reuse existing)* | `debian/proot/stop/` | Already handles stop for all PRoot distros |

> **PRoot start/stop scripts are shared.** You only need to write the `setup_<distro>_family.sh` base script.

#### B. Chroot (Rooted) Mode

| Script | Location | Purpose |
|---|---|---|
| `setup_<distro>_chroot.sh` | `<distro>/chroot/setup/` | Mounts the filesystem, debootstraps, or sets up bind mounts |
| `start_<distro>_kde_gui.sh` | `<distro>/chroot/start/` | Starts KDE inside chroot (with VirGL) |
| `start_<distro>_kde_gui_turnip.sh` | `<distro>/chroot/start/` | Starts KDE inside chroot (Turnip/Zink) |
| `start_<distro>_kde_gui_software.sh` | `<distro>/chroot/start/` | Starts KDE inside chroot (LLVMpipe) |
| `stop_<distro>_gui.sh` | `<distro>/chroot/stop/` | Kills GUI process |
| `stop_<distro>_kde_gui.sh` | `<distro>/chroot/stop/` | Gracefully stops KDE session |
| `uninstall_<distro>.sh` | `<distro>/chroot/setup/` | Cleans up the chroot filesystem |

#### C. Template for `setup_<distro>_family.sh` (runs inside container)

```bash
#!/bin/bash
# FluxLinux Pro — <Distro Name> Family Setup
# Location: <distro>/common/setup/setup_<distro>_family.sh
# Runs on: Inside PRoot/Chroot container
# Root required: no (inside container context)
set -e

export DEBIAN_FRONTEND=noninteractive  # or equivalent for your distro

# ── Error Handler (MANDATORY) ──
# Scripts must NEVER exit silently. This keeps the terminal open so the user
# can read the error, copy it, and report the issue.
handle_error() {
    echo ""
    echo "❌ FluxLinux Pro Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

log_step() { echo ""; echo "===== $1 ====="; echo ""; }

log_step "Installing Desktop Environment"
# apt-get install -y xfce4 xfce4-goodies   || handle_error "XFCE4 Install"   # Debian/Ubuntu
# pacman -S --noconfirm xfce4              || handle_error "XFCE4 Install"   # Arch
# dnf install -y @xfce-desktop             || handle_error "XFCE4 Install"   # Fedora

log_step "Installing TigerVNC / X11 bridge"
# apt install -y tigervnc-standalone-server || handle_error "TigerVNC Install"

log_step "Setting up locale and timezone"
# ...

# ── Verification (recommended for scripts that install multiple tools) ──
verify_installation() {
    echo ""
    echo "🔎 FluxLinux Pro: Verifying Installations..."
    echo "------------------------------------------------"
    MISSING=0
    if command -v xfce4-session >/dev/null; then echo " [✅] XFCE4"; else echo " [❌] XFCE4 Missing"; MISSING=1; fi
    if command -v vncserver >/dev/null; then echo " [✅] TigerVNC"; else echo " [❌] TigerVNC Missing"; MISSING=1; fi
    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed. Check errors above."
    else
        echo "🎉 All components installed successfully!"
    fi
}
verify_installation

# Signal completion back to FluxLinux Pro app
am start -a android.intent.action.VIEW \
  -d "fluxlinux://callback?result=success&name=base_install" \
  --flags 0x10000000 2>/dev/null || true

echo "Setup complete!"
read -p "Press Enter to close..."
```

> **⚠️ Critical Rules:**
> 1. **Every failable command** must use `|| handle_error "Step Name"` — never let errors pass silently.
> 2. **`read -p` at the end** — Termux closes the terminal on script exit. Without this, the user can't read success/error output.
> 3. **`verify_installation`** — Recommended for scripts installing 2+ tools. Shows a clean checklist so the user knows exactly what succeeded/failed.
>
> See [setup_appdev_debian.sh](file:///home/abhay/repos/fluxlinux/app/src/main/assets/scripts/debian/common/setup/setup_appdev_debian.sh) (lines 6–15, 786–846) for the full canonical implementation.

---

### Step 2 — Add `SupportedDistro` Enum Entry

Open [`DistroSpec.kt`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/model/DistroSpec.kt) and add a new enum value:

```kotlin
// Add inside the SupportedDistro enum:
UBUNTU(
    id = "ubuntu",
    family = DistroFamily.DEBIAN,
    packageManager = PackageManager.APT,
    releaseType = ReleaseType.FIXED
),
```

Available families: `DEBIAN`, `ARCH`, `FEDORA`, `ALPINE`, `VOID`, `SUSE`, `TERMUX`, `OTHER`
Available package managers: `APT`, `PACMAN`, `DNF`, `APK`, `XBPS`, `ZYPPER`, `PKG`, `OTHER`
Available release types: `FIXED`, `ROLLING`, `SEMI_ROLLING`

> Skip this step if your distro shares an existing family (e.g., Ubuntu can reuse `SupportedDistro.DEBIAN`).

---

### Step 3 — Define Components

In [`DistroRepository.kt`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/data/DistroRepository.kt), create a private components list:

```kotlin
private val ubuntuComponents = listOf(
    // ── Mandatory base desktop ──
    DistroComponent(
        id = "xfce4_desktop",
        name = "XFCE4 Desktop",
        description = "Base XFCE4 desktop environment.",
        scriptName = "ubuntu/common/setup/setup_ubuntu_family.sh",
        sizeEstimate = "350 MB",
        isMandatory = false     // Set true to auto-queue, cannot be skipped
    ),
    // ── Hardware acceleration (always include for GUI distros) ──
    DistroComponent(
        id = "hw_accel",
        name = "Hardware Acceleration",
        description = "VirGL & Zink GPU drivers.",
        scriptName = "debian/common/setup/setup_hw_accel_debian.sh",  // Reuse if Debian-based
        sizeEstimate = "50 MB",
        isMandatory = true
    ),
    // ── Optional components ──
    DistroComponent(
        id = "web_dev",
        name = "Web Development",
        description = "Node.js, VS Code, Nginx.",
        scriptName = "ubuntu/common/setup/setup_webdev_ubuntu.sh",
        sizeEstimate = "800 MB"
    )
)
```

> **Tip:** Debian-compatible distros (Ubuntu, Kali, Pop!_OS) can **reuse all** `debian/common/setup/*.sh` scripts — just reference them directly in `scriptName`.

---

### Step 4 — Add the `Distro` Entry

Add your distro to the `supportedDistros` list in `DistroRepository.kt`:

```kotlin
Distro(
    id = "ubuntu",                          // Unique ID — used in all dispatch logic
    name = "Ubuntu 24.04 LTS",             // Display name in UI
    description = "Popular Linux distribution. Great for beginners.",
    color = Color(0xFFE95420),              // Ubuntu orange (brand colour)
    iconRes = R.drawable.distro_ubuntu,    // Add drawable to res/drawable/
    comingSoon = false,                     // false = enabled, true = greyed out
    prootSupported = true,                 // Enables rootless install
    chrootSupported = true,                // Enables rooted chroot install
    configuration = SupportedDistro.DEBIAN, // Selects base script family
    components = ubuntuComponents           // Component list defined above
)
```

#### `comingSoon = true` vs `comingSoon = false`

- **`false`** — Distro is live. Shows Install button, allows install/launch.
- **`true`** — Shows as greyed-out card with "Coming Soon" badge. Safe to define early before scripts are ready.

---

### Step 5 — Wire the Base Install Script

Open [`TermuxIntentFactory.kt`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/data/TermuxIntentFactory.kt) and add your `distro.id` to the `baseScriptName` `when` block:

```kotlin
val baseScriptName = when (distro.id) {
    "debian13_chroot" -> "debian/chroot/setup/setup_debian13_chroot.sh"
    "debian_chroot"   -> "debian/chroot/setup/setup_debian_chroot.sh"
    "termux"          -> "termux/setup_termux.sh"
    "archlinux"       -> "arch/common/setup/setup_arch_family.sh"
    "ubuntu"          -> "ubuntu/common/setup/setup_ubuntu_family.sh"  // ← ADD THIS
    else              -> "debian/common/setup/setup_debian_family.sh"
}
```

> If your distro is Debian-based and runs the exact same setup, you can skip this step — it will fall through to the `else` branch and use `setup_debian_family.sh`.

---

### Step 6 — Wire Start / Stop / CLI Intents

The `HomeScreen` uses these `TermuxIntentFactory` functions for launch/stop. Each function dispatches based on `distro.id`:

#### XFCE4 Launch (PRoot — no changes needed)
```kotlin
TermuxIntentFactory.buildLaunchGuiIntent(distro.id)
```
Runs `debian/proot/start/start_gui.sh` — **shared across all PRoot distros**. No code change needed.

#### KDE Launch — 3 GPU Variants (PRoot — no changes needed)
```kotlin
TermuxIntentFactory.buildLaunchKdeGuiIntent(context, distro.id)         // VirGL
TermuxIntentFactory.buildLaunchKdeGuiTurnipIntent(context, distro.id)   // Turnip/Zink
TermuxIntentFactory.buildLaunchKdeGuiSoftwareIntent(context, distro.id) // LLVMpipe
```
All three run `debian/proot/start/start_gui_kde.sh` for PRoot distros. **No code change needed unless your distro has a unique chroot start script.**

For Chroot distros with unique start scripts, add a `when` branch inside each function:

```kotlin
// Inside buildLaunchKdeGuiIntent():
if (distroId == "ubuntu_chroot") {
    val scriptContent = scriptManager.getScriptContent("ubuntu/chroot/start/start_ubuntu_kde_gui.sh")
    // ... same pattern as debian13_chroot branch
}
```

#### Stop GUI (PRoot — no changes needed)
```kotlin
TermuxIntentFactory.buildStopGuiIntent(distro.id)       // XFCE4 stop
TermuxIntentFactory.buildStopKdeGuiIntent(context, distro.id) // KDE stop
```
Uses `debian/proot/stop/stop_gui.sh` for PRoot distros. For Chroot, add a `when` branch pointing to your `chroot/stop/` script.

#### PRoot CLI (Rootless Terminal)
```kotlin
TermuxIntentFactory.buildLaunchCliIntent(distro.id)
```
Runs `proot-distro login <distroId>` — uses the distro's `id` field directly as the proot-distro alias. **No code change needed** as long as `distro.id` matches the proot-distro alias name (e.g., `ubuntu`, `debian`, `archlinux`).

#### Root CLI (Chroot Terminal)
```kotlin
TermuxIntentFactory.buildLaunchRootCliIntent(distro.id)
```
Opens a `su` root terminal in Termux. If your chroot needs a specific mount command, add it as a `when` branch in `buildLaunchRootCliIntent`.

---

### Step 7 — Add the Distro Icon

Place a vector drawable in `app/src/main/res/drawable/`:

```
distro_<name>.xml     (e.g., distro_ubuntu.xml)
```

Reference it as `iconRes = R.drawable.distro_ubuntu` in the `Distro` definition.

> **Tip:** Use the existing `distro_debian.xml` as a template size/format reference. The icons are displayed at 36dp.

---

### Step 8 — Wire Uninstall (Chroot only)

In [`DistroSettingsScreen.kt`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/ui/screens/DistroSettingsScreen.kt), add your chroot ID to the uninstall `when` block:

```kotlin
val scriptName = when(distro.id) {
    "debian_chroot"    -> "debian/chroot/setup/uninstall_debian_chroot.sh"
    "debian13_chroot"  -> "debian/chroot/setup/uninstall_debian13.sh"
    "ubuntu_chroot"    -> "ubuntu/chroot/setup/uninstall_ubuntu.sh"  // ← ADD
    else               -> "debian/chroot/setup/uninstall_debian_chroot.sh"
}
```

---

## Full Checklist

```
Scripts
 [ ] setup_<distro>_family.sh in <distro>/common/setup/
 [ ] (Chroot) setup_<distro>_chroot.sh in <distro>/chroot/setup/
 [ ] (Chroot) start scripts in <distro>/chroot/start/  (VirGL, Turnip, Software)
 [ ] (Chroot) stop scripts in <distro>/chroot/stop/
 [ ] (Chroot) uninstall_<distro>.sh in <distro>/chroot/setup/
 [ ] All scripts tested manually in Termux

Kotlin
 [ ] SupportedDistro entry added (DistroSpec.kt)  — if new family
 [ ] Components list defined  (DistroRepository.kt)
 [ ] Distro entry added to supportedDistros list  (DistroRepository.kt)
 [ ] baseScriptName when-branch added  (TermuxIntentFactory.kt)  — if unique base script
 [ ] chroot start when-branches added  (TermuxIntentFactory.kt)  — if chroot mode
 [ ] chroot stop when-branch added  (TermuxIntentFactory.kt)  — if chroot mode
 [ ] uninstall when-branch added  (DistroSettingsScreen.kt)  — if chroot mode

Assets
 [ ] distro_<name>.xml icon in res/drawable/
 [ ] App builds successfully (./gradlew assembleRelease)
```

---

## Quick Reference: Which Scripts Are Reusable

| Script | Reusable by all distros? | Notes |
|---|---|---|
| `debian/proot/start/start_gui.sh` | ✅ XFCE4 PRoot — all | Generic, uses `DISPLAY=:1` |
| `debian/proot/start/start_gui_kde.sh` | ✅ KDE PRoot — all | Generic KDE via X11 |
| `debian/proot/stop/stop_gui.sh` | ✅ All PRoot | Kills X11 + PulseAudio |
| `debian/common/setup/setup_hw_accel_debian.sh` | ✅ Debian-based only | APT-based, reusable for Ubuntu/Kali/etc |
| `debian/common/setup/setup_kde_debian.sh` | ✅ Debian-based only | APT-based |
| Chroot start/stop | ❌ Distro-specific | Each chroot needs its own mount paths |
| `setup_<distro>_family.sh` | ❌ Distro-specific | Package manager differs per distro |

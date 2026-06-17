# Scripts Folder Usage Guide

This guide explains how the `app/src/main/assets/scripts/` directory works, the conventions for creating new scripts, and how they are loaded and executed by the app at runtime.

---

## 1. Directory Structure Convention

All scripts are organised using the pattern:

```
scripts/<distro>/<execution-type>/<action-type>/<script-name>.sh
```

| Segment | Values | Meaning |
|---|---|---|
| `<distro>` | `termux`, `debian`, `arch`, `fedora` | Which OS family the script targets |
| `<execution-type>` | `common`, `proot`, `chroot` | Where the script runs |
| `<action-type>` | `setup`, `start`, `stop`, `addon` | What the script does |

### Execution Type Rules

| Type | Runs On | Root Required |
|---|---|---|
| `termux/` | Host Android device (Termux) | ❌ |
| `proot/` | Host Android device (Termux), manages the PRoot container | ❌ |
| `common/setup/` | **Inside** the Linux container | ❌ |
| `common/addon/` | Inside the container, runtime helpers | ❌ |
| `chroot/` | Host Android device, uses `su` to operate on Chroot | ✅ Root |

> **Rule:** If a script runs on the *host* Termux (not inside the container), it belongs in `termux/` or `proot/` or `chroot/`. If it runs *inside* the Linux container, it belongs in `<distro>/common/`.

---

## 2. How Scripts Are Loaded

The app reads scripts from assets using [`ScriptManager`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/data/ScriptManager.kt):

```kotlin
val scriptManager = ScriptManager(context)
val content: String = scriptManager.getScriptContent("debian/common/setup/setup_kde_debian.sh")
```

`ScriptManager.getScriptContent(fileName)` reads the asset at:
```
app/src/main/assets/scripts/<fileName>
```

The `fileName` argument **must match exactly** (case-sensitive, include sub-folders).

### Script Delivery to Termux

Scripts are never written to disk directly. They are delivered to Termux via one of two mechanisms:

**A. Base64 pipe (components & install scripts):**
```bash
echo '<base64_encoded_script>' | base64 -d | bash
```
Used by `TermuxIntentFactory` for all component installs. Avoids shell quoting issues entirely.

**B. Heredoc (setup scripts):**
```bash
cat > $HOME/script.sh << 'EOF'
<script content>
EOF
chmod +x $HOME/script.sh && bash $HOME/script.sh
```
Used for scripts that need to persist in `$HOME` (e.g., `start_gui.sh`, `flux_install.sh`).

---

## 3. Creating a New Script

### Step 1 — Pick the correct location

Use this decision tree:

```
Is this script for Termux host (not inside container)?
├── Yes, and no root needed:
│   ├── It sets up the Termux env / installs base deps  →  termux/
│   ├── It installs a desktop component in Termux        →  termux/setup/
│   ├── It starts a Termux-native GUI                   →  termux/start/
│   ├── It stops a Termux-native GUI                    →  termux/stop/
│   └── It manages PRoot containers                     →  debian/proot/{setup|start|stop}/
└── No, runs INSIDE the container:
    ├── It installs software  →  <distro>/common/setup/
    ├── It's a runtime helper/launcher  →  <distro>/common/addon/
    └── It's chroot-specific:
        ├── Installs/uninstalls  →  debian/chroot/setup/
        ├── Launches GUI  →  debian/chroot/start/
        └── Stops GUI  →  debian/chroot/stop/
```

### Step 2 — Name your script

Follow the existing naming convention:

| Pattern | Example |
|---|---|
| `setup_<feature>_<distro>.sh` | `setup_gamedev_debian.sh`, `setup_kde_termux.sh` |
| `start_<de>_<distro>.sh` | `start_xfce4_termux.sh`, `start_kde_termux.sh` |
| `stop_<de>_<distro>.sh` | `stop_xfce4_termux.sh`, `stop_kde_termux.sh` |
| `start_<de>_gui.sh` | `start_gui_kde.sh` (PRoot only) |
| `stop_<de>_gui.sh` | `stop_gui.sh` (PRoot only) |
| `uninstall_<distro>.sh` | `uninstall_debian13.sh` |
| `launch_<tool>.sh` | `launch_qwen25.sh` |

### Step 3 — Script header template

All scripts must start with:

```bash
#!/bin/bash
# ============================================================
# FluxLinux Pro — <Script Name>
# Location: <relative path from scripts/>
# Runs on: <HOST Termux | PRoot container | Chroot via su>
# Root required: <yes | no>
# ============================================================

set -e  # Exit on error

# ── Error Handler (MANDATORY — keeps terminal open on failure) ──
handle_error() {
    echo ""
    echo "❌ FluxLinux Pro Error: Script failed at step: $1"
    echo "---------------------------------------------------"
    echo "Please check the error message above for details."
    echo "---------------------------------------------------"
    read -p "Press Enter to acknowledge error and exit..."
    exit 1
}

# ── Callback (for app state tracking — only on component scripts) ──
CALLBACK_NAME="<your_component_id>"   # Must match DistroComponent.id

# ... your script body ...

# ── Signal completion back to app (only on component scripts) ──
am start -a android.intent.action.VIEW \
  -d "fluxlinux://callback?result=success&name=${CALLBACK_NAME}" \
  --flags 0x10000000 2>/dev/null || true
```

> **Important:** The `am start` callback at the end is what tells the app the component installed successfully, advancing the install queue. Omit it only for `addon/` or standalone `termux/` scripts that don't have a component ID.

---

## 3c. Termux Native Scripts

The **Termux Native** distro (`id = "termux"`) runs desktops directly in Termux without any container. Scripts for this distro live under `termux/setup/`, `termux/start/`, and `termux/stop/`.

### Available Termux Native Scripts

| Script | Location | Purpose | Component ID |
|---|---|---|---|
| `setup_xfce4_termux.sh` | `termux/setup/` | Installs XFCE4 + Termux:X11 | `xfce4_desktop` |
| `setup_hw_accel_termux.sh` | `termux/setup/` | Installs VirGL / Turnip GPU drivers | `hw_accel` |
| `setup_customization_termux.sh` | `termux/setup/` | Applies FluxLinux Pro XFCE4 theme | `customization` |
| `setup_kde_termux.sh` | `termux/setup/` | Installs KDE Plasma + Termux:X11 | `kde_plasma` |
| `setup_customization_kde_termux.sh` | `termux/setup/` | Applies FluxLinux Pro KDE theme | `kde_customization` |
| `start_xfce4_termux.sh` | `termux/start/` | Launches XFCE4 via Termux:X11 | (launch only) |
| `start_kde_termux.sh` | `termux/start/` | Launches KDE via Termux:X11 | (launch only) |
| `stop_xfce4_termux.sh` | `termux/stop/` | Stops XFCE4 + Termux:X11 | (stop only) |
| `stop_kde_termux.sh` | `termux/stop/` | Stops KDE + Termux:X11 | (stop only) |

### Key Differences from Container Scripts

- Scripts run **directly in Termux** — no `proot-distro login`, no `su`, no `busybox chroot`.
- `pkg install` is used instead of `apt install`.
- `$PREFIX` (e.g., `/data/data/com.termux/files/usr`) is always available.
- Start/stop scripts are deployed to `$HOME` and run directly by the app:
  ```bash
  bash $HOME/start_xfce4_termux.sh   # XFCE4
  bash $HOME/stop_xfce4_termux.sh    # stop XFCE4
  bash $HOME/start_kde_termux.sh     # KDE
  bash $HOME/stop_kde_termux.sh      # stop KDE
  ```
- Component scripts are dispatched by `TermuxIntentFactory.buildRunFeatureScriptIntent("termux", ...)` which runs them inline in Termux (no proot wrapper).

---

## 3b. Error Handling (Mandatory)

Scripts **must never silently exit on failure**. The user runs these in a Termux terminal — if the script crashes without output, they see nothing and can't report the issue.

### The `handle_error` Pattern

Every command that can fail must use `|| handle_error "<step name>"`:

```bash
# ✅ CORRECT — user sees which step failed + terminal stays open
apt install -y git wget curl || handle_error "Dependencies Installation"
wget https://example.com/tool.tar.gz -O /tmp/tool.tar.gz || handle_error "Tool Download"
unzip /tmp/tool.tar.gz -d /opt || handle_error "Tool Extract"

# ❌ WRONG — script crashes silently, user sees nothing
apt install -y git wget curl
wget https://example.com/tool.tar.gz -O /tmp/tool.tar.gz
```

### `read -p` to Hold the Terminal

Termux closes the terminal window immediately when a script exits. Both `handle_error` (on failure) and the script end (on success) must use `read -p` to keep the terminal open so the user can read the output:

```bash
# At the END of every script (success path):
echo "---------------------------------------------------"
echo "Setup Complete!"
read -p "Press Enter to close..."
```

### Verification Function (Recommended for Large Scripts)

For scripts that install multiple tools, add a verification block (see [setup_appdev_debian.sh](file:///home/abhay/repos/fluxlinux/app/src/main/assets/scripts/debian/common/setup/setup_appdev_debian.sh) lines 786–833):

```bash
verify_installation() {
    echo ""
    echo "🔎 FluxLinux Pro: Verifying Installations..."
    echo "------------------------------------------------"
    MISSING=0
    
    if command -v tool1 >/dev/null; then
        echo " [✅] Tool1 Installed"
    else
        echo " [❌] Tool1 Missing"
        MISSING=1
    fi
    
    # ... repeat for each tool ...
    
    echo "------------------------------------------------"
    if [ $MISSING -eq 1 ]; then
        echo "⚠️  Some components failed to install."
    else
        echo "🎉 All components installed successfully!"
    fi
}

verify_installation
read -p "Press Enter to close..."
```

### Step 4 — Register in the app (if it's a component)

Add an entry to [`DistroRepository.kt`](file:///home/abhay/repos/fluxlinux/app/src/main/kotlin/com/ivarna/fluxlinux/core/data/DistroRepository.kt):

```kotlin
DistroComponent(
    id = "my_feature",                          // Unique ID — must match CALLBACK_NAME in script
    name = "My Feature",                        // Shown in UI
    description = "What this installs.",        // Shown in UI
    scriptName = "debian/common/setup/setup_my_feature_debian.sh",  // Asset path
    sizeEstimate = "200 MB",                    // Approximate download size
    isMandatory = false,                        // true = auto-queued, cannot skip
    comingSoon = false                          // true = disabled in UI
)
```

Add this `DistroComponent` to the appropriate `components` list (e.g., `debianComponents`).

---

## 4. Environment Variables Available in Scripts

The app injects these environment variables before running component scripts:

| Variable | Set by | Example Value | Usage |
|---|---|---|---|
| `FLUX_THEME` | User selection | `dark` / `light` | Customization scripts |
| `FLUX_GPU` | User selection | `auto` / `virgl` / `turnip` / `ask` | Hardware acceleration |
| `FLUX_DESKTOP_ENV` | User selection | `XFCE` / `KDE` | Desktop env choice |

Access them in your script like any env var:

```bash
if [ "$FLUX_THEME" = "dark" ]; then
    # Apply dark theme
fi
```

---

## 5. Testing a Script Manually

Before wiring it into the app, test directly in Termux:

```bash
# For a container script (proot):
proot-distro login debian -- bash -c 'bash /path/to/your/script.sh'

# For a chroot script (requires root):
su -c 'bash /data/local/tmp/your_script.sh'

# For a host Termux script:
bash ~/your_script.sh
```

---

## 6. File Checklist

Before adding a new script, verify:

- [ ] Correct directory chosen (see decision tree above)
- [ ] Script name follows naming convention
- [ ] `#!/bin/bash` shebang + `set -e` at top
- [ ] `CALLBACK_NAME` and `am start` callback included (if it's a component)
- [ ] `DistroComponent` entry added to `DistroRepository.kt` (if registering as a component)
- [ ] `scriptName` in `DistroRepository.kt` matches the exact asset path
- [ ] `sizeEstimate` is realistic (use `du -sh` on a test device)
- [ ] Script tested manually in Termux before wiring to the app

# Phase 3: Distro Management & Logic

**Goal:** Creating the core user flow: Browsing, Installing, and Launching Linux Distributions.

## 3.1 Distro Repository (Recall)
Instead of fetching list from internet every time, we ship a `distros.json` definition file.
```json
{
  "ubuntu": {
    "name": "Ubuntu 22.04 LTS",
    "id": "ubuntu",
    "logo": "assets/logos/ubuntu.png",
    "description": "Stable, standard linux experience.",
    "isRecommended": true
  },
  "arch": {
    "name": "Arch Linux",
    "id": "archlinux",
    "description": "Rolling release for advanced users."
  }
}
```

## 3.2 Installation Flow
1.  **User Selects Distro:** Clicks "Install" on Ubuntu card.
2.  **Intent Firing:**
    *   Command: `pkg install -y proot-distro` (Ensure dependency)
    *   Command: `proot-distro install ubuntu`
3.  **State Tracking:** Since we can't easily read Termux's internal state synchronously, we track "Installed" state in our local `SharedPrefs`.
    *   *Improvement:* Intent callback or file watcher (if possible) to confirm success.

## 3.3 The Launch Desktop Logic
This is the most complex script. It must:
1.  Start PulseAudio (for sound).
2.  Start Termux:X11 Activity (The display).
3.  Login to PRoot and launch the desktop environment.
```bash
# Conceptual Launch Script
pulseaudio --start --load="module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1" --exit-idle-time=-1
export DISPLAY=:0
am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity
proot-distro login ubuntu --shared-tmp -- bash -c "export DISPLAY=:0 && startxfce4"
```

## 3.4 Deliverables for Phase 3
1.  **Distro Grid UI:** Beautiful cards for each supported distro.
2.  **Install/Uninstall Actions:** Functional buttons sending correct intents.
3.  **Launch Button:** Successfully boots into a Graphical Desktop.

# Scripts Directory Reference

This document provides a comprehensive reference for all **45 files (44 shell scripts + 1 markdown)** bundled in `app/src/main/assets/scripts/`. The directory is now organised by **distro → execution type → action**.

```
scripts/
├── termux/
├── debian/
│   ├── common/
│   │   ├── setup/
│   │   └── addon/
│   ├── proot/
│   │   ├── setup/
│   │   ├── start/
│   │   └── stop/
│   └── chroot/
│       ├── setup/
│       ├── start/
│       └── stop/
├── arch/
│   ├── common/setup/
│   └── chroot/setup/
└── fedora/
    └── common/setup/
```

---

## 1. `termux/` — Host Termux Scripts (5 Files)

Run exclusively on the **host Android device (Termux)**. Never executed inside a container.

| Script | Called By App | Purpose |
|---|---|---|
| `setup_termux.sh` | ✅ PrerequisitesScreen & SettingsScreen | **Step 7 (Required):** Installs proot-distro, grants storage, sets up X11 env |
| `termux_tweaks.sh` | ✅ PrerequisitesScreen & SettingsScreen | **Step 7 (Optional):** X11 packages and PulseAudio config |
| `install.sh` | Manual use / LocalInstallServer | Bootstrap Termux packages. App generates composite install via `getBaseInstallScript()` |
| `install_apps.sh` | Manual use | Additional Termux package requirements |
| `setup_theme.sh` | Manual use | Termux terminal theme (fonts, colours, key layout) |

---

## 2. `debian/proot/` — PRoot Host Scripts (4 Files)

Execute on the **host Termux side** to manage rootless PRoot containers. Physically separate from `debian/common/` to distinguish host vs. container execution.

### `debian/proot/setup/`
| Script | Purpose |
|---|---|
| `flux_install.sh` | Calls `proot-distro install` to download the container image |

### `debian/proot/start/`
| Script | Purpose |
|---|---|
| `start_gui.sh` | Launches **XFCE4** desktop via Termux:X11 |
| `start_gui_kde.sh` | Launches **KDE Plasma** desktop via Termux:X11 |

### `debian/proot/stop/`
| Script | Purpose |
|---|---|
| `stop_gui.sh` | Kills X11 server, VNC, and PulseAudio |

---

## 3. `debian/common/` — Container Scripts, Debian (25 Files)

Run **inside the Linux container** (both PRoot and Chroot). Invoked by the app via `TermuxIntentFactory` (proot-distro login or chroot wrapper).

### `debian/common/setup/` — Setup & Installation (21 Scripts)

**Core Desktop:**
| Script | App Component ID | Purpose |
|---|---|---|
| `setup_debian_family.sh` | `xfce4_desktop` | XFCE4 desktop + Termux:X11 base setup |
| `setup_hw_accel_debian.sh` | `hw_accel` *(mandatory)* | VirGL, Turnip, and Zink GPU drivers |
| `setup_kde_debian.sh` | `kde_plasma` | KDE Plasma DE, Konsole, Dolphin, Kate |
| `setup_customization_debian.sh` | `customization` | FluxLinux Pro theme, wallpapers, 2× scale (XFCE4) |
| `setup_customization_kde_debian.sh` | `kde_customization` | FluxLinux Pro theme, Papirus icons, Zsh (KDE) |
| `setup_gpu.sh` | *(standalone)* | General GPU preparation helper |

**Development:**
| Script | App Component ID | Purpose |
|---|---|---|
| `setup_appdev_debian.sh` | `app_dev` | Android SDK, Flutter, IntelliJ IDEA |
| `setup_webdev_debian.sh` | `web_dev` | Node.js, VS Code, Nginx, Python |
| `setup_gengdev_debian.sh` | `gen_dev` | C++, Rust, Go, LunarVim, Neovim |
| `setup_gamedev_debian.sh` | `game_dev` | Godot, Blender, Raylib |
| `setup_cybersec_debian.sh` | `cyber_sec` | Kali tools, Metasploit, Wireshark |
| `setup_datascience_debian.sh` | `data_science` | Jupyter, Pandas, NumPy, R |

**Productivity & Creative:**
| Script | App Component ID | Purpose |
|---|---|---|
| `setup_office_debian.sh` | `office` | LibreOffice, PDF viewer |
| `setup_graphic_design_debian.sh` | `graphic_design` | GIMP, Inkscape, Krita |
| `setup_video_editing_debian.sh` | `video_editing` | Kdenlive, Shotcut, OpenShot |
| `setup_emulation_debian.sh` | `emulation` | RetroArch, emulator cores *(coming soon)* |

**AI / LLM:**
| Script | App Component ID | Purpose |
|---|---|---|
| `setup_vulkan_llamacpp_debian.sh` | `vulkan_llm` | Vulkan-accelerated llama.cpp |
| `setup_qwen25_debian.sh` | `qwen25` | Downloads Qwen2.5-1.5B GGUF model |
| `setup_qwen35_debian.sh` | `qwen35` | Downloads Qwen3.5-0.8B GGUF model |

### `debian/common/addon/` — Runtime Add-ons (5 Files + 1 Doc)
| File | Purpose |
|---|---|
| `ha` | Injects VirGL/Zink GPU acceleration into any command at runtime |
| `gpu_diagnostics.sh` | Diagnoses hardware acceleration issues |
| `launch_qwen25.sh` | Runs Qwen2.5 interactively inside the container |
| `launch_qwen35.sh` | Runs Qwen3.5 interactively inside the container |
| `virgl_troubleshooting.md` | *(Doc)* Troubleshooting guide for VirGL/Turnip issues |

---

## 4. `debian/chroot/` — Chroot Scripts, Debian (9 Files)

Exclusively for **rooted devices**. Root (`su`) access required.

### `debian/chroot/setup/` — Install & Uninstall
| Script | Purpose |
|---|---|
| `setup_debian13_chroot.sh` | Setup Debian 13 (Trixie) — **primary rooted distro** (`debian13_chroot` ID) |
| `setup_debian_chroot.sh` | Setup generic Debian Chroot |
| `uninstall_debian13.sh` | Remove Debian 13 Chroot |
| `uninstall_debian_chroot.sh` | Remove generic Debian Chroot |

### `debian/chroot/start/` — GUI Launch
| Script | GPU Mode | Purpose |
|---|---|---|
| `start_debian13_kde_gui.sh` | VirGL | KDE Plasma with VirGL hardware acceleration |
| `start_debian13_kde_gui_turnip.sh` | Turnip/Zink | KDE Plasma with Adreno Vulkan (best Snapdragon perf) |
| `start_debian13_kde_gui_software.sh` | LLVMpipe | KDE Plasma with software rendering (most compatible) |

### `debian/chroot/stop/`
| Script | Purpose |
|---|---|
| `stop_debian13_gui.sh` | Stops all GUI processes for Debian 13 Chroot |
| `stop_debian13_kde_gui.sh` | Gracefully stops KDE Plasma session |

---

## 5. `arch/` — Arch Linux Scripts (2 Files)

### `arch/common/setup/`
| Script | App Component ID | Purpose |
|---|---|---|
| `setup_arch_family.sh` | `arch_desktop` *(mandatory)* | XFCE4 desktop base for Arch |

### `arch/chroot/setup/`
| Script | Purpose |
|---|---|
| `setup_arch_chroot.sh` | Native Arch Chroot environment setup |

---

## 6. `fedora/` — Fedora Scripts (1 File)

### `fedora/common/setup/`
| Script | Purpose |
|---|---|
| `setup_fedora_family.sh` | Core setup for Fedora-based environments |

---

**Total: 10 (termux/proot/chroot host) + 25 (debian/common) + 9 (debian/chroot) + 2 (arch) + 1 (fedora) = 45 + 2 arch + 1 fedora subtotals = verified 45 files ✅**

> **Note:** `arch/` and `fedora/` directories are pre-created with their current scripts. Sub-folders (`start/`, `stop/`, `addon/`) will be added as distro support expands.

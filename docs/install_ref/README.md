# Installation Reference

Complete reference for all packages, tools, paths, and environments installed by FluxLinux Pro.

---

## Quick Reference

| Stack | Script | Documentation |
|-------|--------|---------------|
| 📱 App Development | `setup_appdev_debian.sh` | [appdev.md](appdev.md) |
| 🌐 Web Development | `setup_webdev_debian.sh` | [webdev.md](webdev.md) |
| 🧬 Data Science | `setup_datascience_debian.sh` | [datascience.md](datascience.md) |
| 🎮 Game Development | `setup_gamedev_debian.sh` | [gamedev.md](gamedev.md) |
| 🎨 Graphic Design | `setup_graphic_design_debian.sh` | [graphic_design.md](graphic_design.md) |
| 🎬 Video Editing | `setup_video_editing_debian.sh` | [video_editing.md](video_editing.md) |
| 🔐 Cybersecurity | `setup_cybersec_debian.sh` | [cybersec.md](cybersec.md) |
| 📄 Office Suite | `setup_office_debian.sh` | [office.md](office.md) |
| 🎮 Hardware Acceleration | `setup_hw_accel_debian.sh` | [hw_accel.md](hw_accel.md) |

---

## Key Paths

| Item | Path |
|------|------|
| Android SDK | `/opt/android-sdk` |
| Flutter | `/opt/flutter` |
| Gradle | `/opt/gradle` |
| Julia | `/opt/julia` |
| PyCharm | `/opt/pycharm` |
| VS Code | `/usr/share/code` |
| Data Science venv | `/home/flux/data_env` |
| Metasploit | `/opt/metasploit-framework` |

---

## Environment Setup

Add to `~/.bashrc` or `~/.zshrc`:

```bash
# Android SDK
export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Flutter
export PATH=$PATH:/opt/flutter/bin

# Gradle
export PATH=$PATH:/opt/gradle/bin

# Julia
export PATH=$PATH:/opt/julia/bin

# Data Science (activate before use)
# source ~/data_env/bin/activate
```

---

## GPU Compatibility

| Application | VirGL | Turnip |
|-------------|-------|--------|
| GIMP | 🟢 | 🟢 |
| Inkscape | 🟢 | 🟢 |
| Kdenlive | 🟢 | 🟢 |
| XFCE4 | 🟢 | 🟢 |
| **Blender** | 🔴 | 🟢 |
| **Godot** | 🔴 | 🟢 |

> ⚠️ Blender and Godot require Turnip (Adreno GPUs only)

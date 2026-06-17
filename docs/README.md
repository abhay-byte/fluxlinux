<div align="center">
  <img src="../assets/logo/logo.webp" width="120" />
  <h1>📚 FluxLinux Pro Documentation</h1>
  <p><em>Complete technical documentation for FluxLinux Pro</em></p>
</div>

---

## 🗂️ Quick Navigation

<div align="center">
<table>
<tr>
<td align="center" width="200">

### 📦 Installation
[**Installation Reference**](install_ref/)<br/>
Packages, paths, versions

</td>
<td align="center" width="200">

### 🔧 Scripts
[**Scripts Reference**](scripts_reference.md)<br/>
All setup scripts

</td>
<td align="center" width="200">

### 🎮 GPU
[**Hardware Acceleration**](hardware_acceleration.md)<br/>
Turnip & VirGL setup

</td>
</tr>
<tr>
<td align="center">

### 🧪 Testing
[**Testing Reference**](testing_reference.md)<br/>
Sample projects

</td>
<td align="center">

### 🎨 Assets
[**Assets Reference**](assets_reference.md)<br/>
Themes, icons, wallpapers

</td>
<td align="center">

### 🏗️ Architecture
[**Architecture**](architecture.md)<br/>
System design

</td>
</tr>
</table>
</div>

---

## 📖 Documentation Index

### Core Documentation

| Document | Description | Status |
|----------|-------------|--------|
| [**Setup & Onboarding Guide**](tutorial/setup_fluxlinux.md) | Step-by-step visual installation tutorial | ✅ Complete |
| [**Debian PRoot Setup Guide**](tutorial/setup_debian_proot.md) | Step-by-step Debian PRoot configuration tutorial | ✅ Complete |
| [Installation Reference](install_ref/) | Packages, paths, versions, environments | ✅ Complete |
| [Scripts Reference](scripts_reference.md) | All installation and setup scripts | ✅ Complete |
| [Hardware Acceleration](hardware_acceleration.md) | GPU setup guide (Turnip/VirGL) | ✅ Complete |
| [Testing Reference](testing_reference.md) | Sample projects for testing | ✅ Complete |
| [Assets Reference](assets_reference.md) | Themes, icons, wallpapers | ✅ Complete |
| [Script Execution Workflow](script_execution_workflow.md) | How scripts are executed | ✅ Complete |

### Architecture & Design

| Document | Description |
|----------|-------------|
| [Architecture](architecture.md) | System design overview |
| [Components](components.md) | Component deep dive |
| [Technical Specs](technical_specs.md) | Technical specifications |
| [UI/UX Design](ui_ux_design.md) | Interface design guide |
| [UI Design](ui_design.md) | UI components |

### Planning & Roadmap

| Document | Description |
|----------|-------------|
| [Roadmap](roadmap.md) | Development roadmap |
| [Problem Statement](problem_statement.md) | Project motivation |
| [To Be Fixed](to_be_fixed.md) | Known issues |
| [Distro Classification](distro_classification_matrix.md) | Distro support matrix |

### Guides

| Document | Description |
|----------|-------------|
| [Termux X11 Guide](termux_x11_preference_guide.md) | Display configuration |
| [Chroot GPU Guide](CHROOT_HARDWARE_ACCEL.md) | GPU in chroot environment |

---

## 🖼️ Screenshots

<div align="center">
<table>
<tr>
<td align="center"><img src="../assets/screenshots/xfce_desktop.png" width="300" /><br/><b>XFCE Desktop</b></td>
<td align="center"><img src="../assets/screenshots/flutter.png" width="300" /><br/><b>Flutter Dev</b></td>
</tr>
<tr>
<td align="center"><img src="../assets/screenshots/jupyter_tf.png" width="300" /><br/><b>Jupyter + TensorFlow</b></td>
<td align="center"><img src="../assets/screenshots/gimp.png" width="300" /><br/><b>GIMP</b></td>
</tr>
</table>
</div>

---

## 📦 Installation Reference

Quick links to each development stack:

| Stack | Documentation | Key Components |
|-------|---------------|----------------|
| 📱 **App Development** | [appdev.md](install_ref/appdev.md) | Android SDK, Flutter, Kotlin, NDK |
| 🌐 **Web Development** | [webdev.md](install_ref/webdev.md) | Node.js, Python, VS Code |
| 🧬 **Data Science** | [datascience.md](install_ref/datascience.md) | Jupyter, TensorFlow, PyTorch |
| 🎮 **Game Development** | [gamedev.md](install_ref/gamedev.md) | Godot Engine |
| 🎨 **Graphic Design** | [graphic_design.md](install_ref/graphic_design.md) | GIMP, Inkscape, Blender |
| 🎬 **Video Editing** | [video_editing.md](install_ref/video_editing.md) | Kdenlive, Pitivi, FFmpeg |
| 🔐 **Cybersecurity** | [cybersec.md](install_ref/cybersec.md) | Nmap, Wireshark, Metasploit |
| 📄 **Office Suite** | [office.md](install_ref/office.md) | LibreOffice, Thunderbird |
| 🎮 **Hardware Accel** | [hw_accel.md](install_ref/hw_accel.md) | GPU drivers, gpu-launch |

---

## 🎮 GPU Compatibility

| Application | VirGL | Turnip (Adreno) |
|-------------|-------|-----------------|
| XFCE4 | 🟢 Works | 🟢 Works |
| GIMP | 🟢 Works | 🟢 Works |
| Inkscape | 🟢 Works | 🟢 Works |
| Kdenlive | 🟢 Works | 🟢 Works |
| **Blender** | 🔴 Fails | 🟢 Works |
| **Godot** | 🔴 Fails | 🟢 Works |

---

## 📁 Directory Structure

```
docs/
├── install_ref/          # Installation reference
│   ├── appdev.md
│   ├── webdev.md
│   ├── datascience.md
│   └── ...
├── images/               # Documentation images
├── phases/               # Development phases
├── hardware_acceleration.md
├── scripts_reference.md
├── testing_reference.md
└── ...
```

---

<div align="center">
  <p>📖 <a href="../README.md">Back to Main README</a></p>
</div>

<div align="center">
  <img src="assets/logo/logo.webp" width="150" />
  <h1>FluxLinux</h1>
  <p><strong>Run full Linux desktops on Android — 12 distros, PRoot or Chroot, XFCE4, X11, and PulseAudio</strong></p>

  <a href="https://github.com/abhay-byte/fluxlinux">
    <img src="docs/readme/showcase.gif" alt="FluxLinux Showcase" width="100%" />
  </a>
  <br/><br/>

  <a href="https://f-droid.org/packages/com.ivarna.fluxlinux">
    <img src="assets/logo/fdroid-badge.png" alt="Get it on F-Droid" height="54" align="middle"/>
  </a>
  <a href="https://play.google.com/store/apps/details?id=com.zenithblue.fluxlinux">
    <img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" height="60" align="middle"/>
  </a>

  <br/><br/>

  <!-- Row 1 -->
  <a href="https://discord.gg/tag9kXAs2x"><img src="https://img.shields.io/badge/Join_Server-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://github.com/abhay-byte/fluxlinux/releases"><img src="https://img.shields.io/github/downloads/abhay-byte/fluxlinux/total?style=for-the-badge&logo=github&logoColor=white&labelColor=24292e&color=success" alt="Downloads"></a>
  <a href="https://github.com/abhay-byte/fluxlinux/stargazers"><img src="https://img.shields.io/github/stars/abhay-byte/fluxlinux?style=for-the-badge&logo=github&logoColor=white&labelColor=24292e&color=yellow" alt="Stars"></a>
  
  <br/>

  <!-- Row 2 -->
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL_v3-2ea44f?style=for-the-badge&logo=gnu&logoColor=white" alt="License"></a>
  <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <a href="https://github.com/abhay-byte/fluxlinux/releases/tag/v2.0.0"><img src="https://img.shields.io/badge/Release-v2.0.0-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Latest Release"></a>

</div>

---

## 📱 Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><img src="assets/screenshots/home.png" width="200" /><br/><b>Home</b></td>
      <td align="center"><img src="assets/screenshots/distros.png" width="200" /><br/><b>Distros</b></td>
      <td align="center"><img src="assets/screenshots/install.png" width="200" /><br/><b>Install</b></td>
    </tr>
    <tr>
      <td align="center"><img src="assets/screenshots/settings.png" width="200" /><br/><b>Settings</b></td>
      <td align="center"><img src="assets/screenshots/desktop.png" width="200" /><br/><b>Desktop</b></td>
      <td align="center"><img src="assets/screenshots/terminal.png" width="200" /><br/><b>Terminal</b></td>
    </tr>
  </table>
</div>

---

## 🐧 Supported Distros

Twelve guests. **Every one** installs as **PRoot** (no root) or **Chroot** (rooted), with **XFCE4** on the in-app X11 display and **host PulseAudio**. Rootfs archives download on demand from the GitHub [`rootfs`](https://github.com/abhay-byte/fluxlinux/releases/tag/rootfs) tag after an in-app opt-in.

<div align="center">
  <table>
    <tr>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_debian.webp" width="48"/><br/><b>Debian</b><br/>13 Trixie</td>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_alpine.webp" width="48"/><br/><b>Alpine</b><br/>3.24</td>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_fedora.webp" width="48"/><br/><b>Fedora</b><br/>44</td>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_void.webp" width="48"/><br/><b>Void</b><br/>Rolling</td>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_opensuse.webp" width="48"/><br/><b>openSUSE</b><br/>Tumbleweed</td>
      <td align="center" width="16%"><img src="app/src/main/res/drawable/distro_deepin.webp" width="48"/><br/><b>Deepin</b><br/>25</td>
    </tr>
    <tr>
      <td align="center"><img src="app/src/main/res/drawable/distro_chimera.webp" width="48"/><br/><b>Chimera</b><br/>musl / apk v3</td>
      <td align="center"><img src="app/src/main/res/drawable/distro_manjaro.webp" width="48"/><br/><b>Manjaro</b><br/>ARM</td>
      <td align="center"><img src="app/src/main/res/drawable/distro_ubuntu.webp" width="48"/><br/><b>Ubuntu</b><br/>26.04</td>
      <td align="center"><img src="app/src/main/res/drawable/distro_kali.webp" width="48"/><br/><b>Kali</b><br/>Rolling</td>
      <td align="center"><img src="app/src/main/res/drawable/distro_parrot.webp" width="48"/><br/><b>Parrot</b><br/>7.2</td>
      <td align="center"><img src="app/src/main/res/drawable/distro_arch.webp" width="48"/><br/><b>Arch</b><br/>ARM</td>
    </tr>
  </table>
</div>

<br/>

| | Distro | Release | Packages | PRoot | Chroot | XFCE4 + X11 | PulseAudio |
|---|--------|---------|----------|:-----:|:------:|:-----------:|:----------:|
| <img src="app/src/main/res/drawable/distro_debian.webp" width="28"/> | **Debian** | 13 Trixie | apt | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_alpine.webp" width="28"/> | **Alpine** | 3.24 | apk | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_fedora.webp" width="28"/> | **Fedora** | 44 | dnf | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_void.webp" width="28"/> | **Void** | Rolling | xbps | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_opensuse.webp" width="28"/> | **openSUSE** | Tumbleweed | zypper | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_deepin.webp" width="28"/> | **Deepin** | 25 | apt | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_chimera.webp" width="28"/> | **Chimera** | Rolling | apk v3 | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_manjaro.webp" width="28"/> | **Manjaro** | ARM | pacman | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_ubuntu.webp" width="28"/> | **Ubuntu** | 26.04 | apt | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_kali.webp" width="28"/> | **Kali** | Rolling | apt | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_parrot.webp" width="28"/> | **Parrot** | 7.2 | apt | ✓ | ✓ | ✓ | ✓ |
| <img src="app/src/main/res/drawable/distro_arch.webp" width="28"/> | **Arch** | ARM | pacman | ✓ | ✓ | ✓ | ✓ |

Debian also ships the optional extra stacks (KDE Plasma, app/web/data/game/office/graphics). The other eleven use the shared XFCE4 + GPU + theme path.

Per-distro notes: [docs/distro/](docs/distro/).

---

## 🚀 Vision

Modern Android hardware is powerful enough to run desktop workloads, but the software ecosystem limits it. **FluxLinux** bridges this gap, enabling:

- 🌐 **Full-Stack Web Development** — Node.js, Python, React, VS Code
- 🎮 **Desktop Gaming** — Box64/Wine *(coming soon)*
- 🔐 **Cybersecurity** — Nmap, Wireshark, Metasploit
- 📊 **Data Science** — Jupyter, TensorFlow, PyTorch
- 🎨 **Creative Tools** — GIMP, Blender, Inkscape
- 📄 **Productivity** — LibreOffice, Firefox Desktop

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🐧 **12 Distros** | Debian, Alpine, Fedora, Void, openSUSE, Deepin, Chimera, Manjaro, Ubuntu, Kali, Parrot, Arch — each PRoot and Chroot |
| 🖥️ **XFCE4 + X11** | In-app X11 display (no external Termux:X11). XFCE4 on every guest |
| 🔊 **PulseAudio** | Host Pulse as the app uid; guests connect over `127.0.0.1` TCP |
| 🔓 **Rootless Mode** | Works on any Android 8+ device via PRoot |
| ⚡ **Turbo Mode** | Native chroot for rooted devices (Magisk / KernelSU / APatch BusyBox) |
| 🎮 **GPU Acceleration** | Turnip (Adreno) + VirGL for graphics |
| 🎨 **Custom Themes** | XFCE4 Space theme, Papirus icons, wallpapers |
| 📦 **Dev Stacks** | Extra Debian environments for coding, office, and graphics |

---

## 🖼️ Desktop Experience

<div align="center">
  <img src="assets/screenshots/xfce_desktop.png" width="700" />
  <p><em>Full XFCE4 desktop with hardware acceleration</em></p>
</div>

### 🚀 Development in Action

<div align="center">
<table>
<tr>
<td align="center"><img src="assets/screenshots/flutter.png" width="350" /><br/><b>Flutter Development</b></td>
<td align="center"><img src="assets/screenshots/react.png" width="350" /><br/><b>React Web App</b></td>
</tr>
<tr>
<td align="center"><img src="assets/screenshots/jupyter_tf.png" width="350" /><br/><b>Jupyter + TensorFlow</b></td>
<td align="center"><img src="assets/screenshots/kotlin.png" width="350" /><br/><b>Kotlin/Gradle Build</b></td>
</tr>
<tr>
<td align="center"><img src="assets/screenshots/gimp.png" width="350" /><br/><b>GIMP Image Editor</b></td>
<td align="center"><img src="assets/screenshots/libre-writer.png" width="350" /><br/><b>LibreOffice Writer</b></td>
</tr>
<tr>
<td align="center" colspan="2"><img src="assets/screenshots/pitivi.png" width="500" /><br/><b>Pitivi Video Editor</b></td>
</tr>
</table>
</div>

### Included Development Stacks

<div align="center">
  <table>
    <tr>
      <td align="center">🌐<br/><b>Web Dev</b><br/>Node.js, React, VS Code</td>
      <td align="center">📱<br/><b>App Dev</b><br/>Flutter, Kotlin, Android SDK</td>
      <td align="center">🧬<br/><b>Data Science</b><br/>Jupyter, TensorFlow</td>
    </tr>
    <tr>
      <td align="center">🎮<br/><b>Game Dev</b><br/>Godot Engine</td>
      <td align="center">🔐<br/><b>Security</b><br/>Kali Tools</td>
      <td align="center">🎨<br/><b>Graphics</b><br/>GIMP, Blender</td>
    </tr>
  </table>
</div>

---

## 🛠 Architecture

```mermaid
flowchart TB
    subgraph Android["📱 Android Device"]
        FluxLinux["🚀 FluxLinux App<br/>(Kotlin + Jetpack Compose)"]

        subgraph Host["🔧 Embedded host"]
            TermuxHost["bash · proot · PulseAudio"]
            X11["In-app X11 display"]
        end

        subgraph Container["Linux container"]
            PRoot["🔓 PRoot<br/>(rootless)"]
            Chroot["⚡ Chroot<br/>(rooted)"]
        end

        subgraph Distro["🐧 12 guests"]
            XFCE["XFCE4 Desktop"]
            DevTools["Optional Debian stacks"]
        end

        subgraph Display["🖥️ Graphics & audio"]
            GPU["GPU Acceleration<br/>(Turnip / VirGL)"]
            Pulse["Host PulseAudio<br/>TCP 127.0.0.1"]
        end
    end

    FluxLinux --> TermuxHost
    FluxLinux --> X11
    TermuxHost --> PRoot
    TermuxHost --> Chroot
    PRoot --> Distro
    Chroot --> Distro
    Distro --> X11
    X11 --> GPU
    Distro --> Pulse
    TermuxHost --> Pulse
```

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [**Setup & Onboarding Guide**](docs/tutorial/setup_fluxlinux.md) | Step-by-step visual installation tutorial |
| [**Debian PRoot Setup Guide**](docs/tutorial/setup_debian_proot.md) | Step-by-step Debian PRoot configuration tutorial |
| [**Debian Chroot Setup Guide**](docs/tutorial/setup_debian_chroot.md) | Step-by-step Debian Chroot configuration tutorial |
| [**Distro reference**](docs/distro/) | Per-guest notes (Alpine, Fedora, Ubuntu, Kali, …) |
| [**Installation Reference**](docs/install_ref/) | Packages, paths, versions, environments |
| [**Scripts Reference**](docs/scripts_reference.md) | All installation and setup scripts |
| [**Hardware Acceleration**](docs/hardware_acceleration.md) | GPU setup guide (Turnip/VirGL) |
| [**Script Execution Workflow**](docs/script_execution_workflow.md) | How scripts are executed |
| [**Testing Reference**](docs/testing_reference.md) | Sample projects for testing |
| [**Assets Reference**](docs/assets_reference.md) | Themes, icons, wallpapers, rootfs |
| [**Architecture**](docs/architecture.md) | System design overview |
| [**Roadmap**](docs/roadmap.md) | Development roadmap |

---

## 📦 Installation

### Requirements

- Android 8.0+ (API 26+)
- No external Termux or Termux:X11 APK — host shell, PRoot, X11, and PulseAudio ship inside FluxLinux
- Chroot needs a rooted device with Magisk, KernelSU, or APatch BusyBox

### Install

1. Get FluxLinux from [F-Droid](https://f-droid.org/packages/com.ivarna.fluxlinux), [Google Play](https://play.google.com/store/apps/details?id=com.zenithblue.fluxlinux), or [GitHub Releases](https://github.com/abhay-byte/fluxlinux/releases)
2. Open the app and finish the first-run host setup
3. Pick a distro and install as PRoot or Chroot — the rootfs downloads after you opt in

<div align="center">
  <img src="assets/screenshots/setup_wizard.png" width="250" />
  <p><em>Easy setup wizard</em></p>
</div>

---

## 🎮 GPU Acceleration

FluxLinux supports hardware-accelerated graphics:

<table>
<tr>
<td width="50%">

| GPU Type | Driver | Performance |
|----------|--------|-------------|
| Adreno (Qualcomm) | Turnip + Zink | 🟢 Excellent |
| Mali (ARM) | VirGL | 🟡 Good |
| Mali/PowerVR (MediaTek) | VirGL | 🟡 Good |
| Other | VirGL | 🟡 Good |

📖 [Hardware Acceleration Guide](docs/hardware_acceleration.md)

</td>
<td width="50%" align="center">

<img src="assets/screenshots/hardware_acceleration/1.png" width="300" />
<br/><em>GPU Driver Selection</em>

</td>
</tr>
</table>

---

## 🤝 Contributing

Contributions are welcome! Please check the [Roadmap](docs/roadmap.md) to see active development phases.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

See [LICENSE](LICENSE) for details.

**Embedded components:** The in-app terminal stack bundles [termux-app](https://github.com/termux/termux-app) v0.118.0 (GPLv3) for `TerminalSession`/`TerminalView`; host userland packages are rebuilt from the Termux package repo (`native/`). FluxLinux remains open source under GPLv3, consistent with these dependencies.

---

<div align="center">
  <p>Made with ❤️ by <a href="https://github.com/abhay-byte">Abhay Raj</a></p>
  <p>
    <a href="https://github.com/abhay-byte/fluxlinux">GitHub</a> •
    <a href="https://github.com/abhay-byte/fluxlinux/issues">Issues</a> •
    <a href="docs/">Documentation</a> •
    <a href="https://discord.gg/tag9kXAs2x">Discord</a>
  </p>
</div>

---

<div align="center">
  <a href="https://discord.gg/tag9kXAs2x">
    <img src="https://img.shields.io/badge/💬_Join_our_Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white&labelColor=5865F2&height=40" height="40" alt="Join our Discord">
  </a>
  <br>
  <p><strong>Get help, share setups, and discuss features</strong></p>
</div>

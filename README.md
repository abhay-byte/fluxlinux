# FluxLinux - Linux On Android

**FluxLinux** is an advanced orchestrator application that transforms your Android device into a versatile Linux workstation. It seamlessly integrates **Termux**, **PRoot**, and **Termux:X11** to provide a full desktop experience (CLI & GUI) without requiring root access, while offering high-performance acceleration for rooted devices.

## 🚀 Vision
Modern Android hardware is powerful enough to run desktop workloads, but the software ecosystem limits it. FluxLinux bridges this gap, enabling:
*   **Full-Stack Web Development** (Node.js, Python, VS Code)
*   **Desktop Gaming** (Box64/Wine for Windows/Linux games)
*   **Cybersecurity** (Kali Linux tools)
*   **Productivity** (LibreOffice, Firefox Desktop)

## ✨ Key Features
*   **Rootless Containerization:** Uses `proot-distro` to run Ubuntu, Debian, Arch, and more on any Android 8+ device.
*   **Hardware Acceleration:**
    *   **Adreno GPUs:** Native Vulkan/OpenGL support via Turnip + Zink.
    *   **Universal:** VirglRenderer support.
*   **GUI & CLI:** One-click launch for Desktop Environments (XFCE, MATE, GNOME) or Headless shells.
*   **Smart Orchestrator:** Automates complex setups using Termux Intents (no manual copy-pasting required).
*   **Rooted "Turbo" Mode:** Detects root access to offer `chroot` based containers for native performance (zero overhead).

## 🛠 Architecture
FluxLinux acts as a "Launcher" and "Manager" for the underlying Termux ecosystem.
*   **Host:** Android OS
*   **Environment:** Termux (Bionic Libc)
*   **Display:** Termux:X11 (Wayland/XServer)
*   **Engine:** PRoot (Rootless) OR Chroot (Rooted)

For detailed technical documentation, please refer to the [docs/](docs/) directory:
*   [Reference Index](docs/reference.md)
*   [Architecture & Design](docs/architecture.md)
*   [Component Deep Dive](docs/components.md)
*   [Technical Specifications](docs/technical_specs.md)
*   [Implementation Roadmap](docs/roadmap.md)

## 📦 Installation & Usage
*(Coming Soon - Application under active development)*

## 🤝 Contributing
Contributions are welcome! Please check the [Roadmap](docs/roadmap.md) to see active development phases.

## 📄 License
TBD

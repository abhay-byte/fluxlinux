# FluxLinux Pro Script Execution Flowchart

This flowchart visualises the lifecycle of a FluxLinux Pro installation using the restructured `scripts/` filesystem, grouped by **distro → execution type → action**.

```mermaid
flowchart TD
    classDef termux  fill:#1a2e1a,stroke:#4CAF50,stroke-width:2px,color:#aef3ae
    classDef proot   fill:#1a2035,stroke:#4299e1,stroke-width:2px,color:#a8c8ff
    classDef chroot  fill:#2e1a35,stroke:#ab47bc,stroke-width:2px,color:#e8b4f8
    classDef common  fill:#0d2620,stroke:#26a69a,stroke-width:2px,color:#80cbc4
    classDef action  fill:#2e1010,stroke:#ef5350,stroke-width:2px,color:#ffb3b0
    classDef arch    fill:#2a2000,stroke:#fbc02d,stroke-width:2px,color:#fff59d

    subgraph T ["termux/ — Host Setup"]
        direction TB
        T1["setup_termux.sh\n(Required — Step 7)"]:::termux
        T2["termux_tweaks.sh\n(Optional — Step 7)"]:::termux
        T1 --> T2
    end

    subgraph Routes ["Installation Route"]
        direction LR
        T2 -- "Rootless (PRoot)" --> DP["debian/proot/setup/\nflux_install.sh"]:::proot
        T2 -- "Rooted (Chroot)" --> DC["debian/chroot/setup/\nsetup_debian13_chroot.sh\nsetup_debian_chroot.sh"]:::chroot
        T2 -- "Arch" --> AC["arch/chroot/setup/\nsetup_arch_chroot.sh"]:::arch
    end

    subgraph Container ["debian/common/ — Inside Container (Shared)"]
        direction TB
        S1["setup/\nsetup_debian_family.sh\nsetup_arch_family.sh"]:::common
        S2["setup/\nsetup_hw_accel_debian.sh\n(Mandatory component)"]:::common
        S3["setup/\nDesktop:\nsetup_kde_debian.sh\nsetup_customization_*.sh"]:::common
        S4["setup/\nWorkflows:\nsetup_appdev / webdev / gengdev\ncybersec / datascience / gamedev\nvideo_editing / office / graphic_design\nvulkan_llamacpp / qwen25 / qwen35"]:::common
        S5["addon/\nha · gpu_diagnostics.sh\nlaunch_qwen25.sh · launch_qwen35.sh"]:::common
        S1 --> S2 --> S3 --> S4 --> S5
    end

    DP --> S1
    DC --> S1
    AC --> S1

    subgraph Launch ["GUI Launch"]
        direction LR
        S5 -- "PRoot XFCE4" --> L1["debian/proot/start/\nstart_gui.sh"]:::proot
        S5 -- "PRoot KDE"   --> L2["debian/proot/start/\nstart_gui_kde.sh"]:::proot
        S5 -- "Chroot KDE (VirGL)"   --> L3["debian/chroot/start/\nstart_debian13_kde_gui.sh"]:::chroot
        S5 -- "Chroot KDE (Turnip)"  --> L4["debian/chroot/start/\nstart_debian13_kde_gui_turnip.sh"]:::chroot
        S5 -- "Chroot KDE (SW)"      --> L5["debian/chroot/start/\nstart_debian13_kde_gui_software.sh"]:::chroot
    end

    subgraph Stop ["Session Stop"]
        L1 --> ST1["debian/proot/stop/\nstop_gui.sh"]:::action
        L2 --> ST1
        L3 --> ST2["debian/chroot/stop/\nstop_debian13_kde_gui.sh\nstop_debian13_gui.sh"]:::action
        L4 --> ST2
        L5 --> ST2
    end
```

---

### Directory Structure Summary

```
scripts/
├── termux/                          # Host Termux — always runs first
│   ├── setup_termux.sh              # Required: PrerequisitesScreen Step 7
│   ├── termux_tweaks.sh             # Optional: PulseAudio & X11 config
│   ├── install.sh                   # Standalone bootstrap helper
│   ├── install_apps.sh              # Standalone package helper
│   └── setup_theme.sh              # Standalone terminal theming
│
├── debian/
│   ├── common/
│   │   ├── setup/                   # 21 scripts — run inside container
│   │   └── addon/                   # 5 files — runtime helpers & launchers
│   ├── proot/
│   │   ├── setup/   flux_install.sh
│   │   ├── start/   start_gui.sh · start_gui_kde.sh
│   │   └── stop/    stop_gui.sh
│   └── chroot/
│       ├── setup/   setup_debian13_chroot.sh · setup_debian_chroot.sh · uninstall_*
│       ├── start/   start_debian13_kde_gui*.sh  (3 GPU variants)
│       └── stop/    stop_debian13_*.sh
│
├── arch/
│   ├── common/setup/  setup_arch_family.sh
│   └── chroot/setup/  setup_arch_chroot.sh
│
└── fedora/
    └── common/setup/  setup_fedora_family.sh
```

### Flow Notes

1. **Host Setup (Green):** The app always runs `termux/setup_termux.sh` first (mandatory), then optionally `termux/termux_tweaks.sh`. The `install.sh`, `install_apps.sh`, and `setup_theme.sh` files are standalone helpers.

2. **Installation Routes (Blue/Purple/Yellow):** After host setup, the user selects:
   - **PRoot (Rootless):** `debian/proot/setup/flux_install.sh` pulls the container image
   - **Chroot (Rooted):** `debian/chroot/setup/setup_debian13_chroot.sh` (primary), or generic Debian/Arch variants
   - **Arch:** `arch/chroot/setup/setup_arch_chroot.sh`

3. **Common Container Scripts (Teal):** All routes converge into `debian/common/setup/` — the same scripts run inside PRoot and Chroot alike. Hardware acceleration (`setup_hw_accel_debian.sh`) is always first as a mandatory component.

4. **GUI Launch & Stop (Red):** Launch mode depends on container type + desktop choice:
   - PRoot → `debian/proot/start/start_gui.sh` (XFCE4) or `start_gui_kde.sh` (KDE)
   - Chroot → one of 3 GPU variants in `debian/chroot/start/`
   - Each has a corresponding stop script in the `stop/` sibling folder.

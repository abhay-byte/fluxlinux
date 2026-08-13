# Fedora 44 in FluxLinux

| | |
|---|---|
| Cards | `fedora` (proot) · `fedora_chroot` (`/data/local/tmp/chrootFedora`) |
| Rootfs | `fedora_44_rootfs.tar.xz` (Fedora 44 Generic-Minimal container, aarch64) |
| Package manager | `dnf` / `dnf5` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |

Guest scripts: `setup_fedora_family.sh`, shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
Chroot host: shared `setup_guest_chroot.sh` + `start_guest_gui.sh`.

Device notes: dnf uses `tsflags=nodocs,noscripts` + `SYSTEMD_OFFLINE=1`. Fedora gdk-pixbuf is glycin-only — guests get a bwrap shim so PNG/SVG load. Icons default to Adwaita. Existing Fedora 43 guests must be uninstalled and reinstalled.

See [docs/plan/fedora-void-opensuse.md](../plan/fedora-void-opensuse.md).

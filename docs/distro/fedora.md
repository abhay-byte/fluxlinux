# Fedora 43 in FluxLinux

| | |
|---|---|
| Cards | `fedora` (proot) · `fedora_chroot` (`/data/local/tmp/chrootFedora`) |
| Rootfs | `fedora_43_rootfs.tar.xz` (Fedora 43 container, aarch64) |
| Package manager | `dnf` / `dnf5` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |

Guest scripts: `setup_fedora_family.sh`, shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
Chroot host: shared `setup_guest_chroot.sh` + `start_guest_gui.sh`.

Device notes: dnf uses `tsflags=nodocs,noscripts` + `SYSTEMD_OFFLINE=1`. Fedora 43 gdk-pixbuf is glycin-only — guests get a bwrap shim so PNG/SVG load. Icons default to Adwaita.

See [docs/plan/fedora-void-opensuse.md](../plan/fedora-void-opensuse.md).

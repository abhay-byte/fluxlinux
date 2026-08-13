# Void Linux in FluxLinux

| | |
|---|---|
| Cards | `void` (proot) · `void_chroot` (`/data/local/tmp/chrootVoid`) |
| Rootfs | `void_20250202_rootfs.tar.xz` (glibc aarch64, 2025-02-02) |
| Package manager | `xbps-install` (repo `…/current/aarch64`, not musl) |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |

Guest scripts: `setup_void_family.sh`, shared customization + HW accel.  
Chroot host: shared `setup_guest_chroot.sh` + `start_guest_gui.sh`.

Device notes: update xbps first (`xbps-install -u xbps`), then targeted packages — skip full `-Syu` and the `xfce4` metapackage. Incomplete Papirus falls back to Adwaita.

See [docs/plan/fedora-void-opensuse.md](../plan/fedora-void-opensuse.md).

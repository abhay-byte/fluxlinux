# Ubuntu 26.04 in FluxLinux

| | |
|---|---|
| Cards | `ubuntu` (proot) · `ubuntu_chroot` (`/data/local/tmp/chrootUbuntu`) |
| Rootfs | `ubuntu_26.04_rootfs.tar.xz` (Ubuntu 26.04 LTS Resolute arm64 base) |
| Package manager | `apt-get` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |
| Repos | `http://ports.ubuntu.com/ubuntu-ports` only (never `archive.ubuntu.com`) |

Guest scripts: `setup_ubuntu_family.sh`, shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
Chroot host: shared `setup_guest_chroot.sh` + `start_guest_gui.sh`.

Family rewrites DEB822 `ubuntu.sources` URIs from amd64 archive/security to Ubuntu Ports. Targeted XFCE (no `ubuntu-desktop`). `xz-utils` installed for guest theme extract.

See [docs/plan/ubuntu-kali-parrot-arch.md](../plan/ubuntu-kali-parrot-arch.md).

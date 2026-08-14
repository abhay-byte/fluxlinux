# Kali Rolling in FluxLinux

| | |
|---|---|
| Cards | `kali` (proot) · `kali_chroot` (`/data/local/tmp/chrootKali`) |
| Rootfs | `kali_2026_2_rootfs.tar.xz` (NetHunter-minimal arm64, flattened — treated as Kali Rolling) |
| Package manager | `apt-get` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` uid 1000 / `flux`, NOPASSWD sudo. Leftover `kali` uid 100000 is locked. |
| Repos | `kali-rolling` only (never Debian) |

Guest scripts: `setup_kali_family.sh`, shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
Not NetHunter Magisk. Forbidden: `kali-desktop-*`, `kali-linux-default`, `kali-tools-*`.

See [docs/plan/ubuntu-kali-parrot-arch.md](../plan/ubuntu-kali-parrot-arch.md).

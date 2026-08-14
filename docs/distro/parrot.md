# Parrot 7.2 in FluxLinux

| | |
|---|---|
| Cards | `parrot` (proot) · `parrot_chroot` (`/data/local/tmp/chrootParrot`) |
| Rootfs | `parrot_7.2_rootfs.tar.xz` (Parrot Security 7.2 echo, flattened) |
| Package manager | `apt-get` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |
| Repos | `https://deb.parrot.sh/parrot` only (never `deb.debian.org`) |

Guest scripts: `setup_parrot_family.sh`, shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
Detection is card id `parrot` — `os-release` `ID=debian` is a lie. Forbidden: `parrot-tools`, `parrot-interface`, `parrot-desktop-*`.

See [docs/plan/ubuntu-kali-parrot-arch.md](../plan/ubuntu-kali-parrot-arch.md).

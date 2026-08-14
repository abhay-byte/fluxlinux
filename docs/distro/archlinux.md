# Arch Linux ARM in FluxLinux

| | |
|---|---|
| Cards | `archlinux` (proot) · `archlinux_chroot` (`/data/local/tmp/chrootArch`) |
| Rootfs | `archlinux_arm_rootfs.tar.xz` (slim ALARM userspace) |
| Package manager | `pacman` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux` (renamed from `alarm` uid 1000) |
| Repos | ALARM `http://mirror.archlinuxarm.org/$arch/$repo` (never Manjaro `arm-stable`) |

Guest scripts: `setup_arch_family.sh` (rewritten FVO-style), shared `setup_customization_xfce.sh`, `setup_hw_accel_guest.sh`.  
`pacman-key --init` + `--populate archlinuxarm`. `HoldPkg` stays `pacman glibc`. Distinct from Manjaro.

See [docs/plan/ubuntu-kali-parrot-arch.md](../plan/ubuntu-kali-parrot-arch.md) and [docs/plan/archlinux-alarm-slim-rootfs.md](../plan/archlinux-alarm-slim-rootfs.md).

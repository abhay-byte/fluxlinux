# openSUSE Tumbleweed in FluxLinux

| | |
|---|---|
| Cards | `opensuse` (proot) · `opensuse_chroot` (`/data/local/tmp/chrootOpenSUSE`) |
| Rootfs | `opensuse_tumbleweed_rootfs.tar.xz` (snapshot 20251127, aarch64) |
| Package manager | `zypper` |
| Desktop | XFCE4 + Flux theme/icons/font + Mesa/VirGL |
| User | `flux` / `flux`, NOPASSWD sudo |

Guest scripts: `setup_opensuse_family.sh`, shared customization + HW accel.  
Chroot host: shared `setup_guest_chroot.sh` + `start_guest_gui.sh`.

Device notes: do not install `curl` (keeps `libcurl-mini`). `libevp_md2.so` + ld.so.preload for sudo. Session dbus comes from package `dbus-1-daemon`. Prefer `runuser` over `su` in chroot.

See [docs/plan/fedora-void-opensuse.md](../plan/fedora-void-opensuse.md).

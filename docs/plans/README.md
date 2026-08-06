# Plans

Implementation plans for larger FluxLinux workstreams.

| Plan | Status | Summary |
|------|--------|---------|
| [termux-native-packages-dual-appid.md](./termux-native-packages-dual-appid.md) | **COMPLETE** | Custom Termux packages / `bootstrap.tar` + jniLibs for **both** `com.ivarna.fluxlinux` and `com.zenithblue.fluxlinux` (termux-lib SSOT 62/62). |
| [embedded-terminal-bootstrap-proot-chroot.md](./embedded-terminal-bootstrap-proot-chroot.md) | **PARTIAL (Pass 2)** | Pass 1 port landed; Pass 2: Gradle packaging gate, dual-path kill, extract/rootfs recovery, cohesion, R1–R11. Debian → termux-flux-terminal; Debian Rooted → chroot-root-shell; Termux Native card dropped. |

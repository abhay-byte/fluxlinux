# Plans

Implementation plans for larger FluxLinux workstreams.

| Plan | Status | Summary |
|------|--------|---------|
| [termux-native-packages-dual-appid.md](./termux-native-packages-dual-appid.md) | **COMPLETE** | Custom Termux packages / `bootstrap.tar` + jniLibs for **both** `com.ivarna.fluxlinux` and `com.zenithblue.fluxlinux` (termux-lib SSOT 62/62). |
| [embedded-terminal-bootstrap-proot-chroot.md](./embedded-terminal-bootstrap-proot-chroot.md) | **PARTIAL (Pass 2)** | Pass 1 port landed; Pass 2: Gradle packaging gate, dual-path kill, extract/rootfs recovery, cohesion, R1–R11. Debian → termux-flux-terminal; Debian Rooted → chroot-root-shell; Termux Native card dropped. |
| [onboarding-simplified-terminal-display.md](./onboarding-simplified-terminal-display.md) | **IN PROGRESS (device smoke)** | B1–B7 + review-2 quality pass landed (cancel, exit codes, shared progress UI, data-driven deployer). Device smoke + KDE residual open. |
| [terminal-grid-extrakeys-interactive.md](./terminal-grid-extrakeys-interactive.md) | **IMPLEMENTED (T1–T5)** | Terminal tab: nativecode-style **icon grid** (Debian shell cards), interactive `TerminalView` (focus/IME/clipboard), full **ExtraKeys** (`injectKey` + ModifierState lock) ported. T6 build verification in progress; T7 docs done. |

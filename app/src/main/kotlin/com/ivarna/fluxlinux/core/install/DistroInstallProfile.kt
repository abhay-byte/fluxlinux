package com.ivarna.fluxlinux.core.install

import com.ivarna.fluxlinux.core.root.ChrootPaths

/**
 * Per-distro install SSOT: rootfs archive identity, script paths, proot container
 * name, and chroot path. Callers must not hardcode debian/alpine file names.
 *
 * High cohesion: all install-time identity for a card lives here.
 * Low coupling: UI/runner only need [forId]; scripts stay dumb env consumers.
 */
data class DistroInstallProfile(
    /** Distro card id (`debian`, `alpine`, `debian13_chroot`, …). */
    val distroId: String,
    /** proot-distro container name (proot only; empty for chroot-only cards). */
    val prootName: String,
    /** Install method: `proot` or `chroot`. */
    val method: String,
    /**
     * Legacy APK asset path under `assets/rootfs/`. Unused after rootfs left
     * the APK (GitHub release download). Kept for test stability (aapt2
     * `.gz`-rename contract). Not the manual-placement name — that is
     * [rootfsFileName].
     */
    val rootfsAsset: String,
    /** Deployed filename under `$HOME`. */
    val rootfsFileName: String,
    val rootfsSha256: String,
    /** Minimum accepted size after deploy (bytes). */
    val rootfsMinBytes: Long,
    /** GitHub release download URL for the pinned rootfs archive (D1/D4). */
    val rootfsUrl: String,
    /** Guest family setup asset (under `scripts/`). */
    val familyScript: String,
    /** Guest customization asset (under `scripts/`). */
    val customizationScript: String,
    /** Optional guest HW-accel asset (under `scripts/`). */
    val hwAccelScript: String? = null,
    /** Host chroot setup asset path (chroot only). */
    val chrootSetupAsset: String? = null,
    /** Host chroot uninstall asset path (chroot only). */
    val chrootUninstallAsset: String? = null,
    /** Absolute chroot rootfs path (chroot only). */
    val chrootPath: String? = null,
    /** Host GUI guest script name under `$HOME` (chroot only). */
    val chrootStartGuiScript: String? = null,
    val chrootStopGuiScript: String? = null,
    /** Display name for logs / FGS. */
    val displayName: String,
) {
    val homeRootfsPath: String
        get() = rootfsFileName

    companion object {
        /** Base URL of the GitHub release tag that hosts all rootfs archives (D1). */
        const val ROOTFS_RELEASE_BASE =
            "https://github.com/abhay-byte/fluxlinux/releases/download/rootfs"

        // Debian 13 pinned rootfs (existing)
        const val DEBIAN_ROOTFS_NAME = "debian_13_rootfs.tar.xz"
        const val DEBIAN_ROOTFS_SHA256 =
            "13e29f6099c3b805e84694507ede460c03886ffb364c03317272691cf84e6803"
        const val DEBIAN_ROOTFS_MIN_BYTES = 50L * 1024L * 1024L
        const val DEBIAN_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$DEBIAN_ROOTFS_NAME"

        // Alpine 3.24.1 minirootfs aarch64
        // Deployed home name stays *.tar.gz (scripts / proot-distro / tar -xzf).
        // APK asset must NOT end in .gz — aapt2 auto-decompresses and renames
        // *.tar.gz → *.tar, breaking SHA and AssetManager.open(path).
        // The release asset keeps the real .tar.gz name (D8); .minirootfs was
        // APK-packaging-only and is never used on the runtime/release path.
        const val ALPINE_ROOTFS_NAME = "alpine_3.24_rootfs.tar.gz"
        /** Packaged asset path (gzip bytes, non-.gz name) — legacy APK name. */
        const val ALPINE_ROOTFS_ASSET = "rootfs/alpine_3.24_rootfs.minirootfs"
        const val ALPINE_ROOTFS_SHA256 =
            "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"
        const val ALPINE_ROOTFS_MIN_BYTES = 1L * 1024L * 1024L
        const val ALPINE_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$ALPINE_ROOTFS_NAME"

        const val FEDORA_ROOTFS_NAME = "fedora_44_rootfs.tar.xz"
        const val FEDORA_ROOTFS_SHA256 =
            "2d89fe437973e4596d56bf096f71c182d273942a307e7e1e51462dba43db1bd4"
        const val FEDORA_ROOTFS_MIN_BYTES = 20L * 1024L * 1024L
        const val FEDORA_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$FEDORA_ROOTFS_NAME"

        const val VOID_ROOTFS_NAME = "void_20250202_rootfs.tar.xz"
        const val VOID_ROOTFS_SHA256 =
            "01a30f17ae06d4d5b322cd579ca971bc479e02cc284ec1e5a4255bea6bac3ce6"
        const val VOID_ROOTFS_MIN_BYTES = 20L * 1024L * 1024L
        const val VOID_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$VOID_ROOTFS_NAME"

        const val OPENSUSE_ROOTFS_NAME = "opensuse_tumbleweed_rootfs.tar.xz"
        const val OPENSUSE_ROOTFS_SHA256 =
            "bdcb8522a9672cfa513081313b2788f8844340e800918d16a2154e4ed785a12a"
        const val OPENSUSE_ROOTFS_MIN_BYTES = 15L * 1024L * 1024L
        const val OPENSUSE_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$OPENSUSE_ROOTFS_NAME"

        // Chimera Linux 2025-12-20 bootstrap (aarch64, musl, apk v3).
        // Packaged as xz: aapt2 strips *.gz, so gzip bytes were recompressed.
        const val CHIMERA_ROOTFS_NAME = "chimera_20251220_rootfs.tar.xz"
        const val CHIMERA_ROOTFS_SHA256 =
            "0900e3f2554faaf005c14a6850596dadae1e7d8a996138180eebb0b4694a4a6c"
        const val CHIMERA_ROOTFS_MIN_BYTES = 4L * 1024L * 1024L
        const val CHIMERA_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$CHIMERA_ROOTFS_NAME"

        // Deepin 25 (crimson/beige) docker rootfs aarch64 (glibc, apt).
        const val DEEPIN_ROOTFS_NAME = "deepin_25_rootfs.tar.xz"
        const val DEEPIN_ROOTFS_SHA256 =
            "2c7abfe859db36249459251d0b29f853e9ffb79cd1b42c7661e997ba99193698"
        const val DEEPIN_ROOTFS_MIN_BYTES = 40L * 1024L * 1024L
        const val DEEPIN_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$DEEPIN_ROOTFS_NAME"

        // Manjaro ARM aarch64 rootfs (glibc, pacman).
        const val MANJARO_ROOTFS_NAME = "manjaro_arm_rootfs.tar.xz"
        const val MANJARO_ROOTFS_SHA256 =
            "b7339bcc289e8bbb40d1ffdc6ece4404865383d14d4b7f0fb83aa81e01720156"
        const val MANJARO_ROOTFS_MIN_BYTES = 80L * 1024L * 1024L
        const val MANJARO_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$MANJARO_ROOTFS_NAME"

        // Ubuntu 26.04 LTS (Resolute) arm64 base — gzip recompressed to xz
        // (aapt2 auto-decompresses *.gz). SHA is of the packaged xz.
        const val UBUNTU_ROOTFS_NAME = "ubuntu_26.04_rootfs.tar.xz"
        const val UBUNTU_ROOTFS_SHA256 =
            "e648a5302dd273c476e5658e652f88d1e66ece69b487431521c5caef4b960efc"
        const val UBUNTU_ROOTFS_MIN_BYTES = 15L * 1024L * 1024L
        const val UBUNTU_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$UBUNTU_ROOTFS_NAME"

        // Kali Rolling 2026.2 (NetHunter-minimal, flattened — no kali-arm64/).
        const val KALI_ROOTFS_NAME = "kali_2026_2_rootfs.tar.xz"
        const val KALI_ROOTFS_SHA256 =
            "01c48a29ebb543954ef200e766076a143cf42744760d7ccdc31683a19f670689"
        const val KALI_ROOTFS_MIN_BYTES = 40L * 1024L * 1024L
        const val KALI_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$KALI_ROOTFS_NAME"

        // Parrot Security 7.2 (flattened — no parrot-arm64/).
        const val PARROT_ROOTFS_NAME = "parrot_7.2_rootfs.tar.xz"
        const val PARROT_ROOTFS_SHA256 =
            "49f4c2899ef9574cc3b0d9aaa6eaff38c4b32a9ac1abea2faec73cfbaf8094d4"
        const val PARROT_ROOTFS_MIN_BYTES = 30L * 1024L * 1024L
        const val PARROT_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$PARROT_ROOTFS_NAME"

        // Arch Linux ARM slim userspace (already in tree).
        const val ARCH_ROOTFS_NAME = "archlinux_arm_rootfs.tar.xz"
        const val ARCH_ROOTFS_SHA256 =
            "40209ef6318d3aad732299d46ce224c6a0ecded80b6f8091f5e38b40fa031d75"
        const val ARCH_ROOTFS_MIN_BYTES = 40L * 1024L * 1024L
        const val ARCH_ROOTFS_URL = "$ROOTFS_RELEASE_BASE/$ARCH_ROOTFS_NAME"

        private const val XFCE_CUSTOM = "common/setup/setup_customization_xfce.sh"
        private const val HW_ACCEL_GUEST = "common/setup/setup_hw_accel_guest.sh"
        private const val GUEST_CHROOT_SETUP = "scripts/chroot/setup_guest_chroot.sh"
        private const val GUEST_CHROOT_UNINSTALL = "scripts/chroot/uninstall_guest_chroot.sh"

        private val DEBIAN_PROOT = DistroInstallProfile(
            distroId = "debian",
            prootName = "debian",
            method = "proot",
            rootfsAsset = "rootfs/$DEBIAN_ROOTFS_NAME",
            rootfsFileName = DEBIAN_ROOTFS_NAME,
            rootfsSha256 = DEBIAN_ROOTFS_SHA256,
            rootfsMinBytes = DEBIAN_ROOTFS_MIN_BYTES,
            rootfsUrl = DEBIAN_ROOTFS_URL,
            familyScript = "debian/common/setup/setup_debian_family.sh",
            customizationScript = "debian/common/setup/setup_customization_debian.sh",
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Debian",
        )

        private val DEBIAN_CHROOT = DistroInstallProfile(
            distroId = "debian13_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$DEBIAN_ROOTFS_NAME",
            rootfsFileName = DEBIAN_ROOTFS_NAME,
            rootfsSha256 = DEBIAN_ROOTFS_SHA256,
            rootfsMinBytes = DEBIAN_ROOTFS_MIN_BYTES,
            rootfsUrl = DEBIAN_ROOTFS_URL,
            familyScript = "debian/common/setup/setup_debian_family.sh",
            customizationScript = "debian/common/setup/setup_customization_debian.sh",
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = "scripts/chroot/setup_debian13_chroot.sh",
            chrootUninstallAsset = "scripts/chroot/uninstall_debian13_chroot.sh",
            chrootPath = ChrootPaths.DEBIAN_CHROOT_PATH,
            chrootStartGuiScript = "start_debian13_gui.sh",
            chrootStopGuiScript = "stop_debian13_gui.sh",
            displayName = "Debian (Rooted)",
        )

        private val ALPINE_PROOT = DistroInstallProfile(
            distroId = "alpine",
            prootName = "alpine",
            method = "proot",
            rootfsAsset = ALPINE_ROOTFS_ASSET,
            rootfsFileName = ALPINE_ROOTFS_NAME,
            rootfsSha256 = ALPINE_ROOTFS_SHA256,
            rootfsMinBytes = ALPINE_ROOTFS_MIN_BYTES,
            rootfsUrl = ALPINE_ROOTFS_URL,
            familyScript = "alpine/common/setup/setup_alpine_family.sh",
            customizationScript = "alpine/common/setup/setup_customization_alpine.sh",
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Alpine",
        )

        private val ALPINE_CHROOT = DistroInstallProfile(
            distroId = "alpine_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = ALPINE_ROOTFS_ASSET,
            rootfsFileName = ALPINE_ROOTFS_NAME,
            rootfsSha256 = ALPINE_ROOTFS_SHA256,
            rootfsMinBytes = ALPINE_ROOTFS_MIN_BYTES,
            rootfsUrl = ALPINE_ROOTFS_URL,
            familyScript = "alpine/common/setup/setup_alpine_family.sh",
            customizationScript = "alpine/common/setup/setup_customization_alpine.sh",
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = "scripts/chroot/setup_alpine_chroot.sh",
            chrootUninstallAsset = "scripts/chroot/uninstall_alpine_chroot.sh",
            chrootPath = ChrootPaths.ALPINE_CHROOT_PATH,
            chrootStartGuiScript = "start_alpine_gui.sh",
            chrootStopGuiScript = "stop_alpine_gui.sh",
            displayName = "Alpine (Rooted)",
        )

        private val FEDORA_PROOT = DistroInstallProfile(
            distroId = "fedora",
            prootName = "fedora",
            method = "proot",
            rootfsAsset = "rootfs/$FEDORA_ROOTFS_NAME",
            rootfsFileName = FEDORA_ROOTFS_NAME,
            rootfsSha256 = FEDORA_ROOTFS_SHA256,
            rootfsMinBytes = FEDORA_ROOTFS_MIN_BYTES,
            rootfsUrl = FEDORA_ROOTFS_URL,
            familyScript = "fedora/common/setup/setup_fedora_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Fedora",
        )

        private val FEDORA_CHROOT = DistroInstallProfile(
            distroId = "fedora_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$FEDORA_ROOTFS_NAME",
            rootfsFileName = FEDORA_ROOTFS_NAME,
            rootfsSha256 = FEDORA_ROOTFS_SHA256,
            rootfsMinBytes = FEDORA_ROOTFS_MIN_BYTES,
            rootfsUrl = FEDORA_ROOTFS_URL,
            familyScript = "fedora/common/setup/setup_fedora_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.FEDORA_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Fedora (Rooted)",
        )

        private val VOID_PROOT = DistroInstallProfile(
            distroId = "void",
            prootName = "void",
            method = "proot",
            rootfsAsset = "rootfs/$VOID_ROOTFS_NAME",
            rootfsFileName = VOID_ROOTFS_NAME,
            rootfsSha256 = VOID_ROOTFS_SHA256,
            rootfsMinBytes = VOID_ROOTFS_MIN_BYTES,
            rootfsUrl = VOID_ROOTFS_URL,
            familyScript = "void/common/setup/setup_void_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Void",
        )

        private val VOID_CHROOT = DistroInstallProfile(
            distroId = "void_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$VOID_ROOTFS_NAME",
            rootfsFileName = VOID_ROOTFS_NAME,
            rootfsSha256 = VOID_ROOTFS_SHA256,
            rootfsMinBytes = VOID_ROOTFS_MIN_BYTES,
            rootfsUrl = VOID_ROOTFS_URL,
            familyScript = "void/common/setup/setup_void_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.VOID_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Void (Rooted)",
        )

        private val OPENSUSE_PROOT = DistroInstallProfile(
            distroId = "opensuse",
            prootName = "opensuse",
            method = "proot",
            rootfsAsset = "rootfs/$OPENSUSE_ROOTFS_NAME",
            rootfsFileName = OPENSUSE_ROOTFS_NAME,
            rootfsSha256 = OPENSUSE_ROOTFS_SHA256,
            rootfsMinBytes = OPENSUSE_ROOTFS_MIN_BYTES,
            rootfsUrl = OPENSUSE_ROOTFS_URL,
            familyScript = "opensuse/common/setup/setup_opensuse_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "openSUSE",
        )

        private val OPENSUSE_CHROOT = DistroInstallProfile(
            distroId = "opensuse_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$OPENSUSE_ROOTFS_NAME",
            rootfsFileName = OPENSUSE_ROOTFS_NAME,
            rootfsSha256 = OPENSUSE_ROOTFS_SHA256,
            rootfsMinBytes = OPENSUSE_ROOTFS_MIN_BYTES,
            rootfsUrl = OPENSUSE_ROOTFS_URL,
            familyScript = "opensuse/common/setup/setup_opensuse_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.OPENSUSE_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "openSUSE (Rooted)",
        )

        private val DEEPIN_PROOT = DistroInstallProfile(
            distroId = "deepin",
            prootName = "deepin",
            method = "proot",
            rootfsAsset = "rootfs/$DEEPIN_ROOTFS_NAME",
            rootfsFileName = DEEPIN_ROOTFS_NAME,
            rootfsSha256 = DEEPIN_ROOTFS_SHA256,
            rootfsMinBytes = DEEPIN_ROOTFS_MIN_BYTES,
            rootfsUrl = DEEPIN_ROOTFS_URL,
            familyScript = "deepin/common/setup/setup_deepin_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Deepin",
        )

        private val DEEPIN_CHROOT = DistroInstallProfile(
            distroId = "deepin_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$DEEPIN_ROOTFS_NAME",
            rootfsFileName = DEEPIN_ROOTFS_NAME,
            rootfsSha256 = DEEPIN_ROOTFS_SHA256,
            rootfsMinBytes = DEEPIN_ROOTFS_MIN_BYTES,
            rootfsUrl = DEEPIN_ROOTFS_URL,
            familyScript = "deepin/common/setup/setup_deepin_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.DEEPIN_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Deepin (Rooted)",
        )

        private val CHIMERA_PROOT = DistroInstallProfile(
            distroId = "chimera",
            prootName = "chimera",
            method = "proot",
            rootfsAsset = "rootfs/$CHIMERA_ROOTFS_NAME",
            rootfsFileName = CHIMERA_ROOTFS_NAME,
            rootfsSha256 = CHIMERA_ROOTFS_SHA256,
            rootfsMinBytes = CHIMERA_ROOTFS_MIN_BYTES,
            rootfsUrl = CHIMERA_ROOTFS_URL,
            familyScript = "chimera/common/setup/setup_chimera_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Chimera",
        )

        private val CHIMERA_CHROOT = DistroInstallProfile(
            distroId = "chimera_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$CHIMERA_ROOTFS_NAME",
            rootfsFileName = CHIMERA_ROOTFS_NAME,
            rootfsSha256 = CHIMERA_ROOTFS_SHA256,
            rootfsMinBytes = CHIMERA_ROOTFS_MIN_BYTES,
            rootfsUrl = CHIMERA_ROOTFS_URL,
            familyScript = "chimera/common/setup/setup_chimera_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.CHIMERA_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Chimera (Rooted)",
        )

        private val MANJARO_PROOT = DistroInstallProfile(
            distroId = "manjaro",
            prootName = "manjaro",
            method = "proot",
            rootfsAsset = "rootfs/$MANJARO_ROOTFS_NAME",
            rootfsFileName = MANJARO_ROOTFS_NAME,
            rootfsSha256 = MANJARO_ROOTFS_SHA256,
            rootfsMinBytes = MANJARO_ROOTFS_MIN_BYTES,
            rootfsUrl = MANJARO_ROOTFS_URL,
            familyScript = "manjaro/common/setup/setup_manjaro_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Manjaro",
        )

        private val MANJARO_CHROOT = DistroInstallProfile(
            distroId = "manjaro_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$MANJARO_ROOTFS_NAME",
            rootfsFileName = MANJARO_ROOTFS_NAME,
            rootfsSha256 = MANJARO_ROOTFS_SHA256,
            rootfsMinBytes = MANJARO_ROOTFS_MIN_BYTES,
            rootfsUrl = MANJARO_ROOTFS_URL,
            familyScript = "manjaro/common/setup/setup_manjaro_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.MANJARO_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Manjaro (Rooted)",
        )

        private val UBUNTU_PROOT = DistroInstallProfile(
            distroId = "ubuntu",
            prootName = "ubuntu",
            method = "proot",
            rootfsAsset = "rootfs/$UBUNTU_ROOTFS_NAME",
            rootfsFileName = UBUNTU_ROOTFS_NAME,
            rootfsSha256 = UBUNTU_ROOTFS_SHA256,
            rootfsMinBytes = UBUNTU_ROOTFS_MIN_BYTES,
            rootfsUrl = UBUNTU_ROOTFS_URL,
            familyScript = "ubuntu/common/setup/setup_ubuntu_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Ubuntu",
        )

        private val UBUNTU_CHROOT = DistroInstallProfile(
            distroId = "ubuntu_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$UBUNTU_ROOTFS_NAME",
            rootfsFileName = UBUNTU_ROOTFS_NAME,
            rootfsSha256 = UBUNTU_ROOTFS_SHA256,
            rootfsMinBytes = UBUNTU_ROOTFS_MIN_BYTES,
            rootfsUrl = UBUNTU_ROOTFS_URL,
            familyScript = "ubuntu/common/setup/setup_ubuntu_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.UBUNTU_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Ubuntu (Rooted)",
        )

        private val KALI_PROOT = DistroInstallProfile(
            distroId = "kali",
            prootName = "kali",
            method = "proot",
            rootfsAsset = "rootfs/$KALI_ROOTFS_NAME",
            rootfsFileName = KALI_ROOTFS_NAME,
            rootfsSha256 = KALI_ROOTFS_SHA256,
            rootfsMinBytes = KALI_ROOTFS_MIN_BYTES,
            rootfsUrl = KALI_ROOTFS_URL,
            familyScript = "kali/common/setup/setup_kali_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Kali",
        )

        private val KALI_CHROOT = DistroInstallProfile(
            distroId = "kali_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$KALI_ROOTFS_NAME",
            rootfsFileName = KALI_ROOTFS_NAME,
            rootfsSha256 = KALI_ROOTFS_SHA256,
            rootfsMinBytes = KALI_ROOTFS_MIN_BYTES,
            rootfsUrl = KALI_ROOTFS_URL,
            familyScript = "kali/common/setup/setup_kali_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.KALI_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Kali (Rooted)",
        )

        private val PARROT_PROOT = DistroInstallProfile(
            distroId = "parrot",
            prootName = "parrot",
            method = "proot",
            rootfsAsset = "rootfs/$PARROT_ROOTFS_NAME",
            rootfsFileName = PARROT_ROOTFS_NAME,
            rootfsSha256 = PARROT_ROOTFS_SHA256,
            rootfsMinBytes = PARROT_ROOTFS_MIN_BYTES,
            rootfsUrl = PARROT_ROOTFS_URL,
            familyScript = "parrot/common/setup/setup_parrot_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Parrot",
        )

        private val PARROT_CHROOT = DistroInstallProfile(
            distroId = "parrot_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$PARROT_ROOTFS_NAME",
            rootfsFileName = PARROT_ROOTFS_NAME,
            rootfsSha256 = PARROT_ROOTFS_SHA256,
            rootfsMinBytes = PARROT_ROOTFS_MIN_BYTES,
            rootfsUrl = PARROT_ROOTFS_URL,
            familyScript = "parrot/common/setup/setup_parrot_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.PARROT_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Parrot (Rooted)",
        )

        private val ARCH_PROOT = DistroInstallProfile(
            distroId = "archlinux",
            prootName = "archlinux",
            method = "proot",
            rootfsAsset = "rootfs/$ARCH_ROOTFS_NAME",
            rootfsFileName = ARCH_ROOTFS_NAME,
            rootfsSha256 = ARCH_ROOTFS_SHA256,
            rootfsMinBytes = ARCH_ROOTFS_MIN_BYTES,
            rootfsUrl = ARCH_ROOTFS_URL,
            familyScript = "arch/common/setup/setup_arch_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            displayName = "Arch",
        )

        private val ARCH_CHROOT = DistroInstallProfile(
            distroId = "archlinux_chroot",
            prootName = "",
            method = "chroot",
            rootfsAsset = "rootfs/$ARCH_ROOTFS_NAME",
            rootfsFileName = ARCH_ROOTFS_NAME,
            rootfsSha256 = ARCH_ROOTFS_SHA256,
            rootfsMinBytes = ARCH_ROOTFS_MIN_BYTES,
            rootfsUrl = ARCH_ROOTFS_URL,
            familyScript = "arch/common/setup/setup_arch_family.sh",
            customizationScript = XFCE_CUSTOM,
            hwAccelScript = HW_ACCEL_GUEST,
            chrootSetupAsset = GUEST_CHROOT_SETUP,
            chrootUninstallAsset = GUEST_CHROOT_UNINSTALL,
            chrootPath = ChrootPaths.ARCH_CHROOT_PATH,
            chrootStartGuiScript = "start_guest_gui.sh",
            chrootStopGuiScript = "stop_guest_gui.sh",
            displayName = "Arch (Rooted)",
        )

        private val BY_ID: Map<String, DistroInstallProfile> = mapOf(
            "debian" to DEBIAN_PROOT,
            "debian13_chroot" to DEBIAN_CHROOT,
            "debian_chroot" to DEBIAN_CHROOT,
            "alpine" to ALPINE_PROOT,
            "alpine_chroot" to ALPINE_CHROOT,
            "fedora" to FEDORA_PROOT,
            "fedora_chroot" to FEDORA_CHROOT,
            "void" to VOID_PROOT,
            "void_chroot" to VOID_CHROOT,
            "opensuse" to OPENSUSE_PROOT,
            "opensuse_chroot" to OPENSUSE_CHROOT,
            "deepin" to DEEPIN_PROOT,
            "deepin_chroot" to DEEPIN_CHROOT,
            "chimera" to CHIMERA_PROOT,
            "chimera_chroot" to CHIMERA_CHROOT,
            "manjaro" to MANJARO_PROOT,
            "manjaro_chroot" to MANJARO_CHROOT,
            "ubuntu" to UBUNTU_PROOT,
            "ubuntu_chroot" to UBUNTU_CHROOT,
            "kali" to KALI_PROOT,
            "kali_chroot" to KALI_CHROOT,
            "parrot" to PARROT_PROOT,
            "parrot_chroot" to PARROT_CHROOT,
            "archlinux" to ARCH_PROOT,
            "archlinux_chroot" to ARCH_CHROOT,
        )

        /** All profiles that ship a distinct rootfs archive (deduped by file name). */
        fun allRootfsProfiles(): List<DistroInstallProfile> = listOf(
            DEBIAN_PROOT, ALPINE_PROOT, FEDORA_PROOT, VOID_PROOT, OPENSUSE_PROOT,
            DEEPIN_PROOT, CHIMERA_PROOT, MANJARO_PROOT,
            UBUNTU_PROOT, KALI_PROOT, PARROT_PROOT, ARCH_PROOT,
        )

        /** Installable cards in catalog order (proot then matching chroot). */
        fun allInstallable(): List<DistroInstallProfile> = listOf(
            DEBIAN_PROOT, ALPINE_PROOT, FEDORA_PROOT, VOID_PROOT, OPENSUSE_PROOT,
            DEBIAN_CHROOT, ALPINE_CHROOT, FEDORA_CHROOT, VOID_CHROOT, OPENSUSE_CHROOT,
            DEEPIN_PROOT, CHIMERA_PROOT, MANJARO_PROOT,
            DEEPIN_CHROOT, CHIMERA_CHROOT, MANJARO_CHROOT,
            UBUNTU_PROOT, KALI_PROOT, PARROT_PROOT, ARCH_PROOT,
            UBUNTU_CHROOT, KALI_CHROOT, PARROT_CHROOT, ARCH_CHROOT,
        )

        fun forId(distroId: String): DistroInstallProfile? = BY_ID[distroId]

        fun require(distroId: String): DistroInstallProfile =
            forId(distroId)
                ?: throw IllegalArgumentException("unsupported install distro: $distroId")

        fun methodFor(distroId: String): String = forId(distroId)?.method ?: "proot"

        fun isInstallable(distroId: String): Boolean = forId(distroId) != null
    }
}

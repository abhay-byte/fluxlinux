package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.R
import com.ivarna.fluxlinux.core.root.ChrootPaths

/**
 * SSOT for Terminal page shell cards (nativecode `ToolLauncherCatalog` parity).
 *
 * Flux ships **system shells** per installed guest (Debian + Alpine, proot/chroot)
 * plus optional host. Each card carries explicit `method` + `distroId` so session
 * open never falls back to ambient method or wrong container/chroot path.
 */
data class TerminalShellDef(
    val type: String, // "shell" | "shell-root" | "host"
    val label: String,
    val desc: String,
    val method: String, // "proot" | "chroot" | "host"
    val iconRes: Int,
    /** Distro card id for proot name / chroot path (`debian`, `alpine`, …). */
    val distroId: String? = null
)

/** Render-ready card: definition + availability (enabled + disabled reason). */
data class TerminalShellCardUi(
    val def: TerminalShellDef,
    val enabled: Boolean,
    val disabledReason: String?
)

/** One grid section: header + 2-column card group. */
data class TerminalShellSection(
    val title: String,
    val subtitle: String,
    val cards: List<TerminalShellCardUi>
)

/**
 * Filesystem / runtime availability snapshot for the selector grid.
 */
data class TerminalShellAvailability(
    val debianProot: Boolean,
    val alpineProot: Boolean,
    val fedoraProot: Boolean = false,
    val voidProot: Boolean = false,
    val opensuseProot: Boolean = false,
    val deepinProot: Boolean = false,
    val chimeraProot: Boolean = false,
    val manjaroProot: Boolean = false,
    val ubuntuProot: Boolean = false,
    val kaliProot: Boolean = false,
    val parrotProot: Boolean = false,
    val archlinuxProot: Boolean = false,
    val debianChroot: Boolean,
    val alpineChroot: Boolean,
    val fedoraChroot: Boolean = false,
    val voidChroot: Boolean = false,
    val opensuseChroot: Boolean = false,
    val deepinChroot: Boolean = false,
    val chimeraChroot: Boolean = false,
    val manjaroChroot: Boolean = false,
    val ubuntuChroot: Boolean = false,
    val kaliChroot: Boolean = false,
    val parrotChroot: Boolean = false,
    val archlinuxChroot: Boolean = false,
    val rootAvailable: Boolean
) {
    /** Back-compat aliases used by older tests / call sites. */
    val prootInstalled: Boolean get() = debianProot
    val chrootInstalled: Boolean get() = debianChroot

    fun prootInstalled(id: String): Boolean = when (id) {
        "debian" -> debianProot
        "alpine" -> alpineProot
        "fedora" -> fedoraProot
        "void" -> voidProot
        "opensuse" -> opensuseProot
        "deepin" -> deepinProot
        "chimera" -> chimeraProot
        "manjaro" -> manjaroProot
        "ubuntu" -> ubuntuProot
        "kali" -> kaliProot
        "parrot" -> parrotProot
        "archlinux" -> archlinuxProot
        else -> false
    }

    fun chrootInstalled(id: String): Boolean = when (id) {
        "debian13_chroot", "debian_chroot" -> debianChroot
        "alpine_chroot" -> alpineChroot
        "fedora_chroot" -> fedoraChroot
        "void_chroot" -> voidChroot
        "opensuse_chroot" -> opensuseChroot
        "deepin_chroot" -> deepinChroot
        "chimera_chroot" -> chimeraChroot
        "manjaro_chroot" -> manjaroChroot
        "ubuntu_chroot" -> ubuntuChroot
        "kali_chroot" -> kaliChroot
        "parrot_chroot" -> parrotChroot
        "archlinux_chroot" -> archlinuxChroot
        else -> false
    }
}

object TerminalShellCatalog {

    fun prootDefs(distroId: String = "debian"): List<TerminalShellDef> {
        val (label, icon) = when (distroId) {
            "alpine" -> "Alpine" to R.drawable.distro_alpine
            "fedora" -> "Fedora" to R.drawable.distro_fedora
            "void" -> "Void" to R.drawable.distro_void
            "opensuse" -> "openSUSE" to R.drawable.distro_opensuse
            "deepin" -> "Deepin" to R.drawable.distro_deepin
            "chimera" -> "Chimera" to R.drawable.distro_chimera
            "manjaro" -> "Manjaro" to R.drawable.distro_manjaro
            "ubuntu" -> "Ubuntu" to R.drawable.distro_ubuntu
            "kali" -> "Kali" to R.drawable.distro_kali
            "parrot" -> "Parrot" to R.drawable.distro_parrot
            "archlinux" -> "Arch" to R.drawable.distro_arch
            else -> "Debian" to R.drawable.distro_debian
        }
        return listOf(
            TerminalShellDef(
                "shell",
                "$label Shell",
                "User: flux",
                "proot",
                icon,
                distroId
            ),
            TerminalShellDef(
                "shell-root",
                "$label Shell Rooted",
                "User: root",
                "proot",
                icon,
                distroId
            )
        )
    }

    fun chrootDefs(distroId: String = "debian13_chroot"): List<TerminalShellDef> {
        val (label, icon, id) = when (distroId) {
            "alpine_chroot", "alpine" ->
                Triple("Alpine Chroot", R.drawable.distro_alpine, "alpine_chroot")
            "fedora_chroot", "fedora" ->
                Triple("Fedora Chroot", R.drawable.distro_fedora, "fedora_chroot")
            "void_chroot", "void" ->
                Triple("Void Chroot", R.drawable.distro_void, "void_chroot")
            "opensuse_chroot", "opensuse" ->
                Triple("openSUSE Chroot", R.drawable.distro_opensuse, "opensuse_chroot")
            "deepin_chroot", "deepin" ->
                Triple("Deepin Chroot", R.drawable.distro_deepin, "deepin_chroot")
            "chimera_chroot", "chimera" ->
                Triple("Chimera Chroot", R.drawable.distro_chimera, "chimera_chroot")
            "manjaro_chroot", "manjaro" ->
                Triple("Manjaro Chroot", R.drawable.distro_manjaro, "manjaro_chroot")
            "ubuntu_chroot", "ubuntu" ->
                Triple("Ubuntu Chroot", R.drawable.distro_ubuntu, "ubuntu_chroot")
            "kali_chroot", "kali" ->
                Triple("Kali Chroot", R.drawable.distro_kali, "kali_chroot")
            "parrot_chroot", "parrot" ->
                Triple("Parrot Chroot", R.drawable.distro_parrot, "parrot_chroot")
            "archlinux_chroot", "archlinux" ->
                Triple("Arch Chroot", R.drawable.distro_arch, "archlinux_chroot")
            else ->
                Triple("Debian Chroot", R.drawable.distro_debian, "debian13_chroot")
        }
        return listOf(
            TerminalShellDef(
                "shell",
                "$label Shell",
                "User: flux",
                "chroot",
                icon,
                id
            ),
            TerminalShellDef(
                "shell-root",
                "$label Rooted",
                "User: root",
                "chroot",
                icon,
                id
            )
        )
    }

    /** @deprecated Prefer [prootDefs] with explicit distro. */
    fun prootDefs(): List<TerminalShellDef> = prootDefs("debian")

    /** @deprecated Prefer [chrootDefs] with explicit distro. */
    fun chrootDefs(): List<TerminalShellDef> = chrootDefs("debian13_chroot")

    fun hostDef(): TerminalShellDef =
        TerminalShellDef("host", "Host Shell", "libbash", "host", R.drawable.distro_termux)

    /** Synchronous filesystem availability (no su probe — caller supplies that). */
    fun availability(ctx: Context, rootAvailable: Boolean = false): TerminalShellAvailability =
        TerminalShellAvailability(
            debianProot = TerminalLauncher.isProotInstalled(ctx, "debian"),
            alpineProot = TerminalLauncher.isProotInstalled(ctx, "alpine"),
            fedoraProot = TerminalLauncher.isProotInstalled(ctx, "fedora"),
            voidProot = TerminalLauncher.isProotInstalled(ctx, "void"),
            opensuseProot = TerminalLauncher.isProotInstalled(ctx, "opensuse"),
            deepinProot = TerminalLauncher.isProotInstalled(ctx, "deepin"),
            chimeraProot = TerminalLauncher.isProotInstalled(ctx, "chimera"),
            manjaroProot = TerminalLauncher.isProotInstalled(ctx, "manjaro"),
            ubuntuProot = TerminalLauncher.isProotInstalled(ctx, "ubuntu"),
            kaliProot = TerminalLauncher.isProotInstalled(ctx, "kali"),
            parrotProot = TerminalLauncher.isProotInstalled(ctx, "parrot"),
            archlinuxProot = TerminalLauncher.isProotInstalled(ctx, "archlinux"),
            debianChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.DEBIAN_CHROOT_PATH),
            alpineChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.ALPINE_CHROOT_PATH),
            fedoraChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.FEDORA_CHROOT_PATH),
            voidChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.VOID_CHROOT_PATH),
            opensuseChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.OPENSUSE_CHROOT_PATH),
            deepinChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.DEEPIN_CHROOT_PATH),
            chimeraChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.CHIMERA_CHROOT_PATH),
            manjaroChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.MANJARO_CHROOT_PATH),
            ubuntuChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.UBUNTU_CHROOT_PATH),
            kaliChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.KALI_CHROOT_PATH),
            parrotChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.PARROT_CHROOT_PATH),
            archlinuxChroot = TerminalLauncher.isChrootInstalled(ChrootPaths.ARCH_CHROOT_PATH),
            rootAvailable = rootAvailable
        )

    /**
     * Build grid sections for the current availability. Proot / chroot cards stay
     * visible but are disabled with a reason when the guest is missing.
     * Chroot sessions always require device root.
     */
    fun sections(ctx: Context, avail: TerminalShellAvailability): List<TerminalShellSection> {
        fun prootSection(title: String, distroId: String, installed: Boolean): TerminalShellSection {
            val cards = prootDefs(distroId).map { def ->
                TerminalShellCardUi(
                    def = def,
                    enabled = installed,
                    disabledReason = if (installed) null else "Install $title in Distros"
                )
            }
            return TerminalShellSection(
                title = "$title SHELL",
                subtitle = "PROOT",
                cards = cards
            )
        }

        fun chrootSection(
            title: String,
            distroId: String,
            installed: Boolean
        ): TerminalShellSection {
            val cards = chrootDefs(distroId).map { def ->
                val enabled = installed && avail.rootAvailable
                TerminalShellCardUi(
                    def = def,
                    enabled = enabled,
                    disabledReason = when {
                        enabled -> null
                        !installed -> "Chroot not installed"
                        else -> "Installed · no root given to app"
                    }
                )
            }
            return TerminalShellSection(
                title = "$title SHELL",
                subtitle = "CHROOT",
                cards = cards
            )
        }

        val hostCard = TerminalShellCardUi(def = hostDef(), enabled = true, disabledReason = null)

        return listOf(
            prootSection("DEBIAN", "debian", avail.prootInstalled("debian")),
            prootSection("ALPINE", "alpine", avail.prootInstalled("alpine")),
            prootSection("FEDORA", "fedora", avail.prootInstalled("fedora")),
            prootSection("VOID", "void", avail.prootInstalled("void")),
            prootSection("OPENSUSE", "opensuse", avail.prootInstalled("opensuse")),
            prootSection("DEEPIN", "deepin", avail.prootInstalled("deepin")),
            prootSection("CHIMERA", "chimera", avail.prootInstalled("chimera")),
            prootSection("MANJARO", "manjaro", avail.prootInstalled("manjaro")),
            prootSection("UBUNTU", "ubuntu", avail.prootInstalled("ubuntu")),
            prootSection("KALI", "kali", avail.prootInstalled("kali")),
            prootSection("PARROT", "parrot", avail.prootInstalled("parrot")),
            prootSection("ARCHLINUX", "archlinux", avail.prootInstalled("archlinux")),
            chrootSection("DEBIAN", "debian13_chroot", avail.chrootInstalled("debian13_chroot")),
            chrootSection("ALPINE", "alpine_chroot", avail.chrootInstalled("alpine_chroot")),
            chrootSection("FEDORA", "fedora_chroot", avail.chrootInstalled("fedora_chroot")),
            chrootSection("VOID", "void_chroot", avail.chrootInstalled("void_chroot")),
            chrootSection("OPENSUSE", "opensuse_chroot", avail.chrootInstalled("opensuse_chroot")),
            chrootSection("DEEPIN", "deepin_chroot", avail.chrootInstalled("deepin_chroot")),
            chrootSection("CHIMERA", "chimera_chroot", avail.chrootInstalled("chimera_chroot")),
            chrootSection("MANJARO", "manjaro_chroot", avail.chrootInstalled("manjaro_chroot")),
            chrootSection("UBUNTU", "ubuntu_chroot", avail.chrootInstalled("ubuntu_chroot")),
            chrootSection("KALI", "kali_chroot", avail.chrootInstalled("kali_chroot")),
            chrootSection("PARROT", "parrot_chroot", avail.chrootInstalled("parrot_chroot")),
            chrootSection("ARCHLINUX", "archlinux_chroot", avail.chrootInstalled("archlinux_chroot")),
            TerminalShellSection(
                title = "HOST",
                subtitle = "OPTIONAL",
                cards = listOf(hostCard)
            )
        )
    }
}

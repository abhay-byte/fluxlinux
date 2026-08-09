package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import com.ivarna.fluxlinux.R

/**
 * SSOT for Terminal page shell cards (nativecode `ToolLauncherCatalog` parity).
 *
 * Flux v1 ships **system shells only** (proot / chroot / optional host) — no AI CLI
 * marketplace. Each card carries an explicit `method` so session-open paths never
 * fall back to the deprecated ambient `LinuxCommandBuilder.currentMethod`
 * (plan §2.6). Icons reuse the app's full-color distro artwork
 * (`distro_debian.webp` / `distro_termux.webp`) — displayed WITHOUT tinting so the
 * color PNGs keep their original branding (nativecode cli PNG parity).
 */
data class TerminalShellDef(
    val type: String,      // "shell" | "shell-root" | "host"
    val label: String,
    val desc: String,
    val method: String,    // "proot" | "chroot" | "host"
    val iconRes: Int       // full-color drawable (no tint)
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
 *  - [prootInstalled]: Debian rootfs exists under the embedded host prefix.
 *  - [chrootInstalled]: Debian 13 chroot rootfs exists under /data/local/tmp.
 *  - [rootAvailable]: working KernelSU / Magisk su (probe is async in UI).
 */
data class TerminalShellAvailability(
    val prootInstalled: Boolean,
    val chrootInstalled: Boolean,
    val rootAvailable: Boolean
)

object TerminalShellCatalog {

    fun prootDefs(): List<TerminalShellDef> = listOf(
        TerminalShellDef("shell", "Debian Shell", "User: flux", "proot", R.drawable.distro_debian),
        TerminalShellDef("shell-root", "Debian Shell Rooted", "User: root", "proot", R.drawable.distro_debian)
    )

    fun chrootDefs(): List<TerminalShellDef> = listOf(
        TerminalShellDef("shell", "Debian Chroot Shell", "User: flux", "chroot", R.drawable.distro_debian),
        TerminalShellDef("shell-root", "Debian Chroot Rooted", "User: root", "chroot", R.drawable.distro_debian)
    )

    fun hostDef(): TerminalShellDef =
        TerminalShellDef("host", "Host Shell", "libbash", "host", R.drawable.distro_termux)

    /** Synchronous filesystem availability (no su probe — caller supplies that). */
    fun availability(ctx: Context, rootAvailable: Boolean = false): TerminalShellAvailability =
        TerminalShellAvailability(
            prootInstalled = TerminalLauncher.isDebianProotInstalled(ctx),
            chrootInstalled = TerminalLauncher.isDebianChrootInstalled(),
            rootAvailable = rootAvailable
        )

    /**
     * Build grid sections for the current availability. Proot / chroot cards stay
     * visible but are disabled (grayed) with a reason when the guest is missing;
     * chroot-root additionally requires device root (su probe).
     */
    fun sections(ctx: Context, avail: TerminalShellAvailability): List<TerminalShellSection> {
        val prootCards = prootDefs().map { def ->
            val enabled = avail.prootInstalled
            TerminalShellCardUi(
                def = def,
                enabled = enabled,
                disabledReason = if (enabled) null else "Install Debian in Distros"
            )
        }
        val chrootCards = chrootDefs().map { def ->
            val enabled = avail.chrootInstalled &&
                (def.type != "shell-root" || avail.rootAvailable)
            TerminalShellCardUi(
                def = def,
                enabled = enabled,
                disabledReason = when {
                    !avail.chrootInstalled -> "Chroot not installed"
                    def.type == "shell-root" && !avail.rootAvailable -> "Root required"
                    else -> null
                }
            )
        }
        val hostCard = TerminalShellCardUi(def = hostDef(), enabled = true, disabledReason = null)

        return listOf(
            TerminalShellSection(
                title = "DEBIAN SHELL",
                subtitle = "PROOT",
                cards = prootCards
            ),
            TerminalShellSection(
                title = "DEBIAN SHELL",
                subtitle = "CHROOT",
                cards = chrootCards
            ),
            TerminalShellSection(
                title = "HOST",
                subtitle = "OPTIONAL",
                cards = listOf(hostCard)
            )
        )
    }
}

package com.ivarna.fluxlinux.core.install

import android.content.Context
import android.util.Base64
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.data.ScriptManager
import com.ivarna.fluxlinux.core.data.terminalComponentFor

/**
 * Minimal base desktop install: rootfs + XFCE ([setup_debian_family]) +
 * customization ([setup_customization_debian]). No feature modules / hw_accel.
 */
object BaseDesktopInstallPlan {

    const val FAMILY_SCRIPT = "debian/common/setup/setup_debian_family.sh"
    const val CUSTOMIZATION_SCRIPT = "debian/common/setup/setup_customization_debian.sh"

    data class Phase(val id: String, val label: String, val weight: Int)

    fun distroById(id: String): Distro? =
        DistroRepository.supportedDistros.find { it.id == id }

    fun methodFor(distroId: String): String = try {
        terminalComponentFor(distroId).method
    } catch (_: Exception) {
        "proot"
    }

    fun phasesFor(method: String): List<Phase> = if (method == "chroot") {
        listOf(
            Phase("R0", "Checking root access…", 5),
            Phase("HOST", "Preparing host environment…", 15),
            Phase("ROOTFS", "Installing Debian chroot rootfs…", 35),
            Phase("XFCE", "Installing XFCE desktop…", 25),
            Phase("CUSTOM", "Applying Flux customization…", 20),
        )
    } else {
        listOf(
            Phase("HOST", "Preparing host environment…", 20),
            Phase("ROOTFS", "Installing Debian rootfs + XFCE…", 50),
            Phase("CUSTOM", "Applying Flux customization…", 30),
        )
    }

    /**
     * Guest payload for proot [flux_install.sh] setup_b64: env + XFCE family only.
     * Customization runs as a separate phase for clearer progress.
     */
    fun familySetupPayload(ctx: Context, theme: String): String {
        val family = ScriptManager(ctx).getScriptContent(FAMILY_SCRIPT)
        return buildString {
            append("export FLUX_THEME='").append(theme).append("'\n")
            append("export FLUX_DESKTOP_ENV='xfce'\n")
            append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n\n")
            append(family)
        }
    }

    fun familySetupB64(ctx: Context, theme: String): String =
        Base64.encodeToString(
            familySetupPayload(ctx, theme).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

    fun customizationPayload(ctx: Context, theme: String): String {
        val body = ScriptManager(ctx).getScriptContent(CUSTOMIZATION_SCRIPT)
        return buildString {
            append("export FLUX_THEME='").append(theme).append("'\n")
            append("export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n\n")
            append(body)
        }
    }
}

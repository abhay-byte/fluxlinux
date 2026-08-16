package com.ivarna.fluxlinux.core.chroot

import android.content.Context
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.install.DistroInstallProfile
import com.ivarna.fluxlinux.core.root.ChrootPaths
import java.io.File

/**
 * Catalog and path helper for Chroot and PRoot storage management.
 * Provides safe path lookup, allowlisting, and installed row generation.
 */
object GuestStorageCatalog {
    const val ALL_CHROOT_ID = "__all_chroot__"
    const val ALL_PROOT_ID = "__all_proot__"

    /** The 12 ChrootPaths constants. Kill/measure refuse anything else. */
    val KNOWN_CHROOT_PATHS: Set<String> = setOf(
        ChrootPaths.DEBIAN_CHROOT_PATH,
        ChrootPaths.ALPINE_CHROOT_PATH,
        ChrootPaths.FEDORA_CHROOT_PATH,
        ChrootPaths.VOID_CHROOT_PATH,
        ChrootPaths.OPENSUSE_CHROOT_PATH,
        ChrootPaths.DEEPIN_CHROOT_PATH,
        ChrootPaths.CHIMERA_CHROOT_PATH,
        ChrootPaths.MANJARO_CHROOT_PATH,
        ChrootPaths.UBUNTU_CHROOT_PATH,
        ChrootPaths.KALI_CHROOT_PATH,
        ChrootPaths.PARROT_CHROOT_PATH,
        ChrootPaths.ARCH_CHROOT_PATH,
    )

    val REFUSED_HOST_PATHS: Set<String> = setOf(
        "",
        "/",
        "/data",
        "/data/",
        "/data/local",
        "/data/local/",
        "/data/local/tmp",
        "/data/local/tmp/"
    )

    data class Row(
        val distroId: String,
        val displayName: String, // Distro.name
        val hostPath: String,
        val iconRes: Int?,
        val method: String, // "chroot" | "proot"
    )

    fun installableChroots(): List<Distro> =
        DistroRepository.supportedDistros.filter {
            !it.comingSoon && it.chrootSupported
        }

    fun installableProots(): List<Distro> =
        DistroRepository.supportedDistros.filter {
            !it.comingSoon && it.prootSupported
        }

    /**
     * Build installed rows from a **caller-supplied** predicate so JVM tests
     * inject FS and the UI never calls isDistroInstalledOnFs on the main thread.
     * List builder does **not** call ChrootPaths.pathForDistro.
     */
    fun installedRows(
        distros: List<Distro>,
        installed: (id: String) -> Boolean,
        hostPath: (id: String) -> String?,
    ): List<Row> {
        val rows = mutableListOf<Row>()
        for (d in distros) {
            if (installed(d.id)) {
                val path = hostPath(d.id) ?: continue
                rows.add(
                    Row(
                        distroId = d.id,
                        displayName = d.name,
                        hostPath = path,
                        iconRes = d.iconRes,
                        method = if (d.chrootSupported) "chroot" else "proot"
                    )
                )
            }
        }
        return rows
    }

    fun chrootPathOrNull(distroId: String): String? {
        if (distroId == ALL_CHROOT_ID) return null
        val p = DistroInstallProfile.forId(distroId) ?: return null
        if (p.method != "chroot") return null
        return p.chrootPath?.takeIf { it in KNOWN_CHROOT_PATHS }
    }

    fun allowedKillPath(path: String): Boolean =
        path in KNOWN_CHROOT_PATHS && path !in REFUSED_HOST_PATHS

    fun prootContainerDir(ctx: Context, distroId: String): File? {
        val p = DistroInstallProfile.forId(distroId) ?: return null
        if (p.method != "proot" || p.prootName.isBlank()) return null
        return File(ctx.filesDir, "usr/var/lib/proot-distro/containers/${p.prootName}")
    }

    fun prootContainerPath(ctx: Context, distroId: String): String? {
        return prootContainerDir(ctx, distroId)?.absolutePath
    }
}

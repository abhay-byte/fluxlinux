package com.ivarna.fluxlinux.core.install

/**
 * Host `bootstrap.tar` identity pins shared by both store flavors.
 *
 * Transport is deliberately owned by the flavor-specific host provider. This
 * common object carries only the filename and verification gate.
 *
 * Filename is stable per applicationId. A SHA change requires a **new**
 * filename (same D9 rule as distro rootfs) so old APKs keep working.
 */
object HostBootstrap {
    const val IVARNA_PACKAGE = "com.ivarna.fluxlinux"
    const val ZENITHBLUE_PACKAGE = "com.zenithblue.fluxlinux"

    val IVARNA = VerifiedPayloadSpec(
        fileName = "bootstrap_com.ivarna.fluxlinux.tar",
        sha256 = "5b16c6597d38380c0cab9471d3cf69a0c7a23d9a4191125bd9dd6ddc77277f5c",
        minBytes = 50L * 1024L * 1024L
    )

    val ZENITHBLUE = VerifiedPayloadSpec(
        fileName = "bootstrap_com.zenithblue.fluxlinux.tar",
        // Measured from the reproducible bootstrap produced by
        // scripts/assemble_bootstrap.py (Worker 01 baseline).
        sha256 = "3ffef7f92820341e2a74b739fb15695a16fe4622e80cfc81d18bd98461712609",
        minBytes = 50L * 1024L * 1024L
    )

    fun forApplicationId(applicationId: String): VerifiedPayloadSpec =
        when (applicationId) {
            ZENITHBLUE_PACKAGE -> ZENITHBLUE
            else -> IVARNA
        }

    /** True when this flavor does **not** package `assets/bootstrap.tar`. */
    fun downloadsFromRelease(applicationId: String): Boolean =
        applicationId == IVARNA_PACKAGE
}

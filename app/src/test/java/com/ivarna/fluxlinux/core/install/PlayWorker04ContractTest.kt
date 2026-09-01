package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayWorker04ContractTest {
    private fun repoFile(rel: String): File {
        return repoCandidates(rel).firstOrNull { it.isFile }
            ?: error("missing $rel from ${File("").absoluteFile}")
    }

    private fun repoCandidates(rel: String): List<File> {
        val candidates = mutableListOf<File>()
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            candidates += File(directory, rel)
            candidates += File(directory, "app/$rel")
            directory = directory.parentFile
        }
        return candidates
    }

    @Test
    fun playCustomizationAssetsContainNoRemoteInstallers() {
        val paths = listOf(
            "src/zenithblue/assets/scripts/common/setup/play_family_local_only.sh",
            "src/zenithblue/assets/scripts/debian/common/setup/setup_debian_family.sh",
            "src/zenithblue/assets/scripts/alpine/common/setup/setup_alpine_family.sh",
            "src/zenithblue/assets/scripts/fedora/common/setup/setup_fedora_family.sh",
            "src/zenithblue/assets/scripts/void/common/setup/setup_void_family.sh",
            "src/zenithblue/assets/scripts/opensuse/common/setup/setup_opensuse_family.sh",
            "src/zenithblue/assets/scripts/chimera/common/setup/setup_chimera_family.sh",
            "src/zenithblue/assets/scripts/deepin/common/setup/setup_deepin_family.sh",
            "src/zenithblue/assets/scripts/manjaro/common/setup/setup_manjaro_family.sh",
            "src/zenithblue/assets/scripts/ubuntu/common/setup/setup_ubuntu_family.sh",
            "src/zenithblue/assets/scripts/kali/common/setup/setup_kali_family.sh",
            "src/zenithblue/assets/scripts/parrot/common/setup/setup_parrot_family.sh",
            "src/zenithblue/assets/scripts/arch/common/setup/setup_arch_family.sh",
            "src/zenithblue/assets/scripts/alpine/common/setup/setup_customization_alpine.sh",
            "src/zenithblue/assets/scripts/debian/common/setup/setup_customization_debian.sh",
            "src/zenithblue/assets/scripts/debian/common/setup/setup_customization_kde_debian.sh",
            "src/zenithblue/assets/scripts/common/setup/setup_customization_xfce.sh",
            "src/zenithblue/assets/scripts/termux/termux_tweaks.sh",
            "src/zenithblue/assets/scripts/termux/setup_theme.sh",
            "src/zenithblue/assets/scripts/termux/setup/setup_customization_termux.sh",
            "src/zenithblue/assets/scripts/termux/setup/setup_customization_kde_termux.sh"
        )
        paths.map(::repoFile).forEach { file ->
            val text = file.readText().lowercase()
            assertFalse(file.name, text.contains("git clone"))
            assertFalse(file.name, text.contains("wget"))
            assertFalse(file.name, text.contains("curl"))
            assertFalse(file.name, text.contains("install.sh"))
            assertFalse(file.name, text.contains("pokemon"))
            assertFalse(file.name, text.contains("oh-my-zsh"))
        }
        assertTrue(repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/install/FlavorCustomizationBridge.kt").exists())
        assertFalse(
            "remote implementation must not be in common Play sources",
            repoCandidates("src/main/kotlin/com/ivarna/fluxlinux/core/install/ProotZshBootstrap.kt")
                .any { it.exists() }
        )
    }

    @Test
    fun alpinePlayPayloadRequiresBaselineMarker() {
        val text = repoFile(
            "src/zenithblue/assets/scripts/alpine/common/setup/setup_alpine_family.sh"
        ).readText()
        assertTrue(text.contains("play-baseline-v1"))
        assertTrue(text.contains("refusing runtime package-network customization"))
    }

    @Test
    fun playFamilySelectionUsesTheSharedLocalOnlyFinalizer() {
        val plan = repoFile(
            "src/main/kotlin/com/ivarna/fluxlinux/core/install/BaseDesktopInstallPlan.kt"
        ).readText()
        assertTrue(plan.contains("common/setup/play_family_local_only.sh"))
        assertTrue(plan.contains("BuildConfig.FLAVOR == \"zenithblue\""))
    }

    @Test
    fun allTwelvePlayDistroModulesHaveBuilderManifests() {
        val manifest = repoFile("scripts/play_rootfs/manifests.json").readText()
        listOf(
            "debian", "alpine", "fedora", "void", "opensuse", "chimera",
            "deepin", "manjaro", "ubuntu", "kali", "parrot", "archlinux"
        ).forEach { assertTrue("missing $it", manifest.contains("\"id\": \"$it\"")) }
        assertTrue(repoFile("scripts/play_rootfs/build_play_rootfs.py").isFile)
        assertTrue(repoFile("scripts/verify_play_rootfs_provenance.py").isFile)
    }
}

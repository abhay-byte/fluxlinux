package com.ivarna.fluxlinux.core.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks UKPA host dispatch: start/stop chroot twins must share the four
 * card-id arms, * stays Debian 13, and uninstall allowlists UKPA paths.
 */
class UkpaHostDispatchContractTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(
            File(cwd, rel),
            File(cwd, "app/$rel"),
            File(cwd.parentFile, rel),
            File(cwd.parentFile, "app/$rel")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("missing $rel (cwd=$cwd)")
    }

    private val ukpaArms = listOf(
        "ubuntu|ubuntu_chroot",
        "kali|kali_chroot",
        "parrot|parrot_chroot",
        "archlinux|archlinux_chroot"
    )

    private val ukpaPaths = listOf(
        "chrootUbuntu",
        "chrootKali",
        "chrootParrot",
        "chrootArch"
    )

    @Test
    fun startAndStopChroot_dispatchUkpaArms() {
        val start = repoFile("src/main/assets/scripts/chroot/start_gui_chroot.sh").readText()
        val stop = repoFile("src/main/assets/scripts/chroot/stop_gui_chroot.sh").readText()
        for (arm in ukpaArms) {
            assertTrue("start_gui_chroot.sh missing $arm", start.contains(arm))
            assertTrue("stop_gui_chroot.sh missing $arm", stop.contains(arm))
        }
        for (path in ukpaPaths) {
            assertTrue("start_gui_chroot.sh missing $path", start.contains(path))
            assertTrue("stop_gui_chroot.sh missing $path", stop.contains(path))
        }
        for (arm in ukpaArms) {
            val startBlock = start.substringAfter(arm).substringBefore(";;")
            assertTrue(
                "start $arm must use start_guest_gui.sh",
                startBlock.contains("start_guest_gui.sh")
            )
            val stopBlock = stop.substringAfter(arm).substringBefore(";;")
            assertTrue(
                "stop $arm must use stop_guest_gui.sh",
                stopBlock.contains("stop_guest_gui.sh")
            )
        }
    }

    @Test
    fun startAndStopChroot_defaultStillDebian13_alpineDedicated() {
        val start = repoFile("src/main/assets/scripts/chroot/start_gui_chroot.sh").readText()
        val stop = repoFile("src/main/assets/scripts/chroot/stop_gui_chroot.sh").readText()

        val startDefault = start.substringAfter("*)").substringBefore("esac")
        assertTrue(startDefault.contains("chrootDebian13"))
        assertTrue(startDefault.contains("start_debian13_gui.sh"))

        val stopDefault = stop.substringAfter("*)").substringBefore("esac")
        assertTrue(stopDefault.contains("chrootDebian13"))
        assertTrue(stopDefault.contains("stop_debian13_gui.sh"))

        val startAlpine = start.substringAfter("alpine|alpine_chroot").substringBefore(";;")
        assertTrue(startAlpine.contains("start_alpine_gui.sh"))
        val stopAlpine = stop.substringAfter("alpine|alpine_chroot").substringBefore(";;")
        assertTrue(stopAlpine.contains("stop_alpine_gui.sh"))
    }

    @Test
    fun uninstallGuestChroot_allowlistIncludesUkpaAndExisting() {
        val text = repoFile(
            "src/main/assets/scripts/chroot/uninstall_guest_chroot.sh"
        ).readText()
        for (path in listOf(
            "/data/local/tmp/chrootUbuntu",
            "/data/local/tmp/chrootKali",
            "/data/local/tmp/chrootParrot",
            "/data/local/tmp/chrootArch",
            "/data/local/tmp/chrootDebian13",
            "/data/local/tmp/chrootManjaro"
        )) {
            assertTrue("uninstall allowlist missing $path", text.contains(path))
        }
        assertTrue(text.contains("Refusing to remove unexpected path"))
        val caseBody = text.substringAfter("case \"\$GUESTPATH\" in").substringBefore("esac")
        assertTrue(
            "unknown paths must still refuse",
            caseBody.contains("*)") && caseBody.contains("Refusing to remove unexpected path")
        )
        assertFalse(
            "allowlist must not accept every path",
            caseBody.contains("*) ;;") || caseBody.contains("*) ;;")
        )
    }

    @Test
    fun ubuntuFamily_portsOnly() {
        val text = repoFile(
            "src/main/assets/scripts/ubuntu/common/setup/setup_ubuntu_family.sh"
        ).readText()
        assertTrue(text.contains("ports.ubuntu.com/ubuntu-ports"))
        assertFalse(text.contains("deb.debian.org"))
    }

    @Test
    fun kaliFamily_locksKaliUser_noDesktopMeta() {
        val text = repoFile(
            "src/main/assets/scripts/kali/common/setup/setup_kali_family.sh"
        ).readText()
        assertTrue(text.contains("usermod -L kali"))
        assertFalse(
            "must not install kali-desktop-xfce",
            Regex("""apt(?:-get)?\s+install[^\n]*kali-desktop-xfce""")
                .containsMatchIn(text)
        )
    }

    @Test
    fun parrotFamily_parrotMirrors_noMetas() {
        val text = repoFile(
            "src/main/assets/scripts/parrot/common/setup/setup_parrot_family.sh"
        ).readText()
        assertTrue(text.contains("deb.parrot.sh"))
        assertFalse(
            "must not install parrot-tools",
            Regex("""apt(?:-get)?\s+install[^\n]*parrot-tools""")
                .containsMatchIn(text)
        )
        assertFalse(
            "must not install parrot-interface",
            Regex("""apt(?:-get)?\s+install[^\n]*parrot-interface""")
                .containsMatchIn(text)
        )
    }

    @Test
    fun archFamily_alarmMirrors_disableSandbox() {
        val text = repoFile(
            "src/main/assets/scripts/arch/common/setup/setup_arch_family.sh"
        ).readText()
        assertTrue(text.contains("archlinuxarm"))
        assertTrue(text.contains("DisableSandbox"))
        assertFalse(
            "must not write Manjaro arm-stable mirrors",
            Regex("""Server\s*=\s*\S*arm-stable""").containsMatchIn(text)
        )
    }

    @Test
    fun archFamily_localeFailClosed() {
        val arch = repoFile(
            "src/main/assets/scripts/arch/common/setup/setup_arch_family.sh"
        ).readText()
        assertTrue(arch.contains("_flux_ensure_en_us_locale"))
        assertTrue(
            "gzip is required to unpack UTF-8.gz for localedef",
            arch.contains("python sed gzip")
        )
        assertFalse(
            "--needed glibc is a no-op on ALARM and does not restore i18n",
            arch.contains("pacman -S --noconfirm --needed glibc")
        )
        assertTrue(arch.contains("pacman -S --noconfirm glibc"))
        assertTrue(
            "family must fail closed when locale -a has no UTF-8 name",
            arch.contains("locale -a has neither en_US.utf8 nor C.utf8")
        )
        assertTrue(arch.contains("_flux_ensure_en_us_locale || exit 1"))
    }

    @Test
    fun localeHelpers_trustLocaleDashA_equateUtf8Spellings() {
        val common = repoFile(
            "src/main/assets/scripts/common/setup/flux_guest_common.sh"
        ).readText()
        val hasEnUs = common.substringAfter("_flux_locale_has_en_us() {")
            .substringBefore("_flux_locale_has_c_utf8")
        assertFalse(
            "directory names under /usr/lib/locale are not loadable locales",
            hasEnUs.contains("ls /usr/lib/locale")
        )
        assertTrue(common.contains("\${_want%.UTF-8}.utf8"))
        assertTrue(common.contains("\${_want%.utf8}.UTF-8"))
        assertTrue(common.contains("no UTF-8 locale in locale -a"))
        assertFalse(common.contains("using POSIX C"))
        val custom = repoFile(
            "src/main/assets/scripts/common/setup/setup_customization_xfce.sh"
        ).readText()
        assertTrue(custom.contains("FontName="))
        assertTrue(custom.contains("terminalrc"))
        assertTrue(custom.contains("xfce4-terminal.xml"))
        assertTrue(custom.contains("no UTF-8 locale in locale -a"))
    }
}

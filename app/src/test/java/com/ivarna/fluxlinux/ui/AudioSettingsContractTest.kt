package com.ivarna.fluxlinux.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioSettingsContractTest {

    private fun repoFile(rel: String): File {
        val cwd = File("").absoluteFile
        val candidates = listOf(
            File(cwd, rel),
            File(cwd, "app/$rel"),
            File(cwd.parentFile, rel),
            File(cwd.parentFile, "app/$rel")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("missing $rel (cwd=$cwd)")
    }

    @Test
    fun settingsHub_hasAudioCard() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/ui/screens/SettingsScreen.kt")
            .readText()
        assertTrue(src.contains("onNavigateToAudioSettings"))
        assertTrue(src.contains("title = \"Audio\""))
        assertTrue(src.contains("Host PulseAudio status"))
    }

    @Test
    fun mainActivity_wiresAudioScreen() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/MainActivity.kt").readText()
        assertTrue(src.contains("SETTINGS_AUDIO"))
        assertTrue(src.contains("AudioSettingsScreen"))
        assertTrue(src.contains("onNavigateToAudioSettings"))
    }

    @Test
    fun audioScreen_hasRepairGuests() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/ui/screens/AudioSettingsScreen.kt")
            .readText()
        assertTrue(src.contains("Repair guests"))
        assertTrue(src.contains("PulseHost.repairGuests"))
        assertTrue(src.contains("PulseHost::repairToast"))
    }

    @Test
    fun pulseHost_queryDoesNotPassNldPathToEnv() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/terminal/PulseHost.kt")
            .readText()
        assertFalse(src.contains("env -u LD_PRELOAD"))
        assertTrue(src.contains("PULSE_SERVER"))
        assertTrue(src.contains("libPactl"))
        assertFalse(src.contains("--check"))
        assertFalse(src.contains("--kill"))
    }

    @Test
    fun deployer_listsRepairAndSupervisor() {
        val src = repoFile("src/main/kotlin/com/ivarna/fluxlinux/core/terminal/HostScriptDeployer.kt")
            .readText()
        assertTrue(src.contains("start_pulse_host.sh"))
        assertTrue(src.contains("repair_pulse_guests.sh"))
        assertTrue(src.contains("setup_pulse_guest.sh"))
    }

    @Test
    fun supervisor_tcpOkUsesEnvI() {
        val src = repoFile("src/main/assets/scripts/host/start_pulse_host.sh").readText()
        assertTrue(src.startsWith("#!/bin/bash"))
        assertTrue(src.contains("pa_env PULSE_SERVER=tcp:127.0.0.1"))
        assertTrue(src.contains("first_pulse_pid"))
        assertTrue(src.contains("tcp_ours_loopback"))
        assertTrue(src.contains("tcp_foreign_uid"))
        assertTrue(src.contains("tcp_bound_localhost_only || ! tcp_ok"))
        assertFalse(src.contains("PULSE_SERVER=tcp:127.0.0.1 run_pactl"))
        assertTrue(src.contains("chmod 755"))
        assertTrue(src.contains("libsoxr.so"))
        assertTrue(src.contains("PD_PULSEAUDIO_BIN"))
        assertTrue(src.contains("libpulseaudio.so"))
        assertTrue(src.contains("unset LD_PRELOAD"))
        assertFalse(src.contains("PA=\"\$PREFIX/bin/pulseaudio\""))
        // Android nativeLibraryDir contains `=`; env must not see that path as COMMAND.
        assertTrue(src.contains("bin=\"\$1\"; shift; exec \"\$bin\" \"\$@\""))
        assertTrue(src.contains("--daemonize=yes"))
        assertFalse(src.contains("run_pa --start"))
        assertFalse(src.contains("run_pa --check"))
        assertFalse(src.contains("run_pa --kill"))
    }

    @Test
    fun guestCommon_noPulseDaemonFallback() {
        val common = repoFile("src/main/assets/scripts/common/setup/flux_guest_common.sh").readText()
        assertTrue(common.contains("Never fall back to the `pulseaudio` server package"))
        assertFalse(common.contains("_flux_try_install pulseaudio ||"))
        assertTrue(common.contains("pulse client already present"))
        assertTrue(common.contains("_flux_sed_i"))
        assertTrue(common.contains("_flux_guest_pactl"))
        assertTrue(common.contains("/usr/bin/pactl /usr/sbin/pactl /bin/pactl"))
        assertFalse(
            common.contains(
                "PATH=\"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\${PATH:+:\$PATH}\""
            )
        )
        val voidFamily = repoFile("src/main/assets/scripts/void/common/setup/setup_void_family.sh")
            .readText()
        assertFalse(voidFamily.contains("_flux_try_install pulseaudio ||"))
        assertFalse(voidFamily.contains("# --- inlined flux_guest_common.sh ---"))
        assertTrue(voidFamily.contains("_flux_setup_pulse"))
    }

    @Test
    fun repairGuests_usesEnvIAndGuestPath() {
        val src = repoFile("src/main/assets/scripts/host/repair_pulse_guests.sh").readText()
        assertTrue(src.contains("env -i HOME=/root USER=root"))
        assertTrue(src.contains("PATH=\"\$_gp\""))
        assertTrue(src.contains("PULSE_SERVER=tcp:127.0.0.1"))
        assertFalse(
            src.contains("proot-distro login \"\$_name\" --shared-tmp --user root -- \\\n        /bin/sh /tmp/setup_pulse_guest.sh")
        )
    }

    @Test
    fun debianAlpine_doNotTruncateEnvironment() {
        for (rel in listOf(
            "src/main/assets/scripts/debian/common/setup/setup_debian_family.sh",
            "src/main/assets/scripts/alpine/common/setup/setup_alpine_family.sh"
        )) {
            val src = repoFile(rel).readText()
            assertTrue(rel, src.contains("_flux_write_pulse_client"))
            assertTrue(rel, src.contains("grep -q '^PULSE_SERVER='"))
            assertFalse(
                rel,
                src.contains("printf 'PULSE_SERVER=tcp:127.0.0.1\\n' > /etc/environment\ncat >")
            )
        }
    }
}

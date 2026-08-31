package com.ivarna.fluxlinux.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestDnsConfiguratorTest {
    @Test
    fun encodesIpv4Ipv6AndMultipleResolvers() {
        val encoded = GuestDnsConfigurator.encode(
            listOf("192.168.1.1", "2001:db8::53", "192.168.1.1")
        )
        assertTrue(encoded.startsWith("192.168.1.1,"))
        assertEquals(2, GuestDnsConfigurator.decode(encoded).size)
    }

    @Test
    fun rejectsEmptyAndInvalidValues() {
        assertEquals("", GuestDnsConfigurator.encode(listOf("", "not-a-dns", "1.2.3.999")))
        assertEquals(emptyList<String>(), GuestDnsConfigurator.decode(null))
        assertEquals(emptyList<String>(), GuestDnsConfigurator.decode("bad;command,1.2.3"))
    }

    @Test
    fun publicResolversAreOnlyTheFinalFallback() {
        val android = GuestDnsConfigurator.decode("192.168.1.1,2001:db8::53")
        assertEquals(android, android.ifEmpty { GuestDnsConfigurator.PUBLIC_FALLBACK })
        assertEquals(
            GuestDnsConfigurator.PUBLIC_FALLBACK,
            GuestDnsConfigurator.decode("").ifEmpty { GuestDnsConfigurator.PUBLIC_FALLBACK }
        )
    }
}

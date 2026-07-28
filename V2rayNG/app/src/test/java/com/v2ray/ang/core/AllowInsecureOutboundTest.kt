package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork guard: the insecure flag must reach the generated core config.
 *
 * ProfileItem.insecure -> tlsSettings.allowInsecure is the only path that actually
 * makes the core skip certificate verification. See AllowInsecureFmtTest for the
 * URL parse/serialize half of the same guarantee.
 */
class AllowInsecureOutboundTest {

    private fun tlsProfile(block: ProfileItem.() -> Unit = {}) =
        ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            // Non-empty sni: skips the Utils.isDomainName branch.
            sni = "example.com"
            // Non-empty finalMask: skips updateOutboundFragment() (CoreOutboundBuilder.kt:575),
            // whose first statement reads MMKV settings and would need an initialised Android
            // runtime. Packet fragmentation is unrelated to what these tests assert.
            finalMask = "fragment"
            block()
        }

    @Test
    fun test_insecureTrue_setsAllowInsecureOnTlsSettings() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = true }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertEquals(AppConfig.TLS, stream.security)
        assertNotNull(stream.tlsSettings)
        assertTrue(
            "allowInsecure must reach tlsSettings — this fork keeps the feature alive",
            stream.tlsSettings!!.allowInsecure
        )
    }

    @Test
    fun test_insecureFalse_leavesAllowInsecureOff() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = false }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertNotNull(stream.tlsSettings)
        assertFalse(stream.tlsSettings!!.allowInsecure)
    }

    @Test
    fun test_pinnedCertificateSuppressesAllowInsecure() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile {
            insecure = true
            pinnedCA256 = "0123456789abcdef"
        }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertNotNull(stream.tlsSettings)
        assertFalse(
            "A pinned certificate must win over allowInsecure",
            stream.tlsSettings!!.allowInsecure
        )
        assertEquals("0123456789abcdef", stream.tlsSettings!!.pinnedPeerCertSha256)
    }

    @Test
    fun test_serverNameIsCarriedThrough() {
        val stream = OutboundBean.StreamSettingsBean()
        val profile = tlsProfile { insecure = true }

        CoreOutboundBuilder.populateTlsSettings(stream, profile, null)

        assertEquals("example.com", stream.tlsSettings!!.serverName)
    }
}

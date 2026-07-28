package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fork guard: allowInsecure must survive upstream merges.
 *
 * Upstream announced (toast_allow_insecure_deprecated) that skipping certificate
 * verification would be disabled in August 2026. This fork deliberately keeps it.
 * If a merge removes the parse/serialize support, these tests must fail loudly.
 */
class AllowInsecureFmtTest {

    private val fmt = FmtBase()

    private fun parse(vararg pairs: Pair<String, String>): ProfileItem {
        val config = ProfileItem.create(EConfigType.VLESS)
        fmt.getItemFormQuery(config, mapOf("security" to AppConfig.TLS, *pairs))
        return config
    }

    @Test
    fun test_parse_insecureKey_enablesInsecure() {
        assertEquals(true, parse("insecure" to "1").insecure)
    }

    @Test
    fun test_parse_allowInsecureKey_enablesInsecure() {
        assertEquals(true, parse("allowInsecure" to "1").insecure)
    }

    @Test
    fun test_parse_allowUnderscoreInsecureKey_enablesInsecure() {
        assertEquals(true, parse("allow_insecure" to "1").insecure)
    }

    @Test
    fun test_parse_zeroValue_disablesInsecure() {
        assertEquals(false, parse("insecure" to "0").insecure)
        assertEquals(false, parse("allowInsecure" to "0").insecure)
        assertEquals(false, parse("allow_insecure" to "0").insecure)
    }

    @Test
    fun test_parse_absentKey_defaultsToFalse() {
        assertEquals(false, parse().insecure)
    }

    @Test
    fun test_serialize_emitsBothInsecureAndAllowInsecureKeys() {
        val config = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = true
        }

        val dic = fmt.getQueryDic(config)

        assertEquals("1", dic["insecure"])
        assertEquals("1", dic["allowInsecure"])
    }

    @Test
    fun test_serialize_emitsZeroWhenInsecureDisabled() {
        val config = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = false
        }

        val dic = fmt.getQueryDic(config)

        assertEquals("0", dic["insecure"])
        assertEquals("0", dic["allowInsecure"])
    }

    @Test
    fun test_roundTrip_preservesInsecureFlag() {
        val original = ProfileItem.create(EConfigType.VLESS).apply {
            security = AppConfig.TLS
            network = NetworkType.TCP.type
            insecure = true
        }

        val dic = fmt.getQueryDic(original)
        val reparsed = ProfileItem.create(EConfigType.VLESS)
        fmt.getItemFormQuery(reparsed, dic)

        assertEquals(true, reparsed.insecure)
    }
}

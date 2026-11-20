package io.github.markovcd.capynib.core.memos

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PlainHttpTest {
    private fun refusal(url: String, address: String): String? = PlainHttp.refusal(url.toHttpUrl(), InetAddress.getByName(address))

    @Test
    fun Plain_http_reaches_only_a_local_network_address() {
        val local = listOf(
            "127.0.0.1", "::1", "10.1.2.3", "172.16.0.5", "172.31.255.255", "192.168.1.10",
            "169.254.1.1", "100.64.0.1", "100.127.255.254", "fd12:3456::1", "fe80::1",
        )
        val public = listOf("93.184.216.34", "8.8.8.8", "172.32.0.1", "100.128.0.1", "2606:4700::1111")

        val wrong = local.filter { refusal("http://memos.home:5230/", it) != null }.map { "$it refused" } +
            public.filter { refusal("http://memos.home:5230/", it) == null }.map { "$it allowed" }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun Https_reaches_any_address() {
        assertNull(refusal("https://memos.example.com/", "93.184.216.34"))
    }

    @Test
    fun A_refused_plain_http_address_names_the_host_and_the_address_it_reached() {
        val message = refusal("http://memos.example.com/", "93.184.216.34")!!
        assertTrue(message, "memos.example.com" in message && "93.184.216.34" in message)
    }
}

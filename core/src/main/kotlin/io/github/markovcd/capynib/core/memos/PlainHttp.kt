package io.github.markovcd.capynib.core.memos

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Lets a plain `http://` call through only to a local network address, so the token and notes
 * never cross the internet unencrypted. Checked on the connected address, after DNS, before the
 * request is written.
 */
internal object PlainHttp : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val address = chain.connection()?.route()?.socketAddress?.address
        val refused = address?.let { refusal(chain.request().url, it) }
        if (refused != null) throw IOException(refused)
        return chain.proceed(chain.request())
    }

    /** Why [url] may not be sent to [address], or null when it may. */
    fun refusal(url: HttpUrl, address: InetAddress): String? =
        if (url.isHttps || isLocal(address)) null
        else "plain http goes only to a local network; ${url.host} is ${address.hostAddress}, use https"

    private fun isLocal(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        val bytes = address.address
        return when (address) {
            // 100.64.0.0/10, the shared range VPNs such as Tailscale hand out.
            is Inet4Address -> bytes[0].toInt() == 100 && bytes[1].toInt() and 0xC0 == 0x40
            // fc00::/7, unique local addresses.
            is Inet6Address -> bytes[0].toInt() and 0xFE == 0xFC
            else -> false
        }
    }
}

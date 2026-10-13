package io.github.markovcd.capynib.core.memos

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A checked memos server address, such as `https://memos.example.com`. */
@JvmInline
value class ServerAddress private constructor(val url: HttpUrl) {
    val host: String get() = url.host

    override fun toString(): String = url.toString().trimEnd('/')

    companion object {
        /** Returns null for anything that is not an http(s) URL. A missing scheme means https. */
        fun parse(text: String): ServerAddress? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            return ServerAddress(url)
        }
    }
}

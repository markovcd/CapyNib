package io.github.markovcd.capynib.core.memos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens on this device's loopback address for the browser coming back from a sign-in provider.
 * Anything but a request to [redirectUri] carrying the expected state is answered and ignored, so
 * another app cannot end the wait.
 */
class LoopbackReceiver private constructor(private val socket: ServerSocket) : Closeable {
    val redirectUri: String = "http://$HOST:${socket.localPort}$PATH"

    /**
     * Waits until the browser arrives with [state]. Each connection is served on its own, so an idle
     * one a browser opened ahead of time does not hold up the real request.
     */
    suspend fun await(state: String): BrowserCallback = coroutineScope {
        val arrived = CompletableDeferred<BrowserCallback>()
        val open = ConcurrentHashMap.newKeySet<Socket>()
        launch(Dispatchers.IO) {
            while (!arrived.isCompleted) {
                val connection = try {
                    socket.accept()
                } catch (e: IOException) {
                    arrived.complete(BrowserCallback.Error("stopped listening for the browser: ${e.message}"))
                    break
                }
                open += connection
                launch {
                    connection.use { serve(it, state)?.let(arrived::complete) }
                    open -= connection
                }
            }
        }
        try {
            arrived.await()
        } finally {
            socket.close()
            open.forEach { it.close() }
        }
    }

    override fun close() = socket.close()

    private fun serve(connection: Socket, state: String): BrowserCallback? {
        connection.soTimeout = READ_TIMEOUT_MS
        val target = try {
            readTarget(connection.getInputStream())
        } catch (e: IOException) {
            null
        }
        val url = target?.let { "http://$HOST$it".toHttpUrlOrNull() }
        val callback = when {
            url == null || url.encodedPath != PATH -> null
            url.queryParameter("state") != state -> null
            else -> url.queryParameter("error")?.let { BrowserCallback.Error(it) }
                ?: url.queryParameter("code")?.takeIf { it.isNotEmpty() }?.let { BrowserCallback.Code(it) }
        }
        respond(connection, callback)
        return callback
    }

    /** The request target of an HTTP request line, such as `/capynib?code=…`; null for anything else. */
    private fun readTarget(input: InputStream): String? {
        val line = StringBuilder()
        while (line.length < MAX_REQUEST_LINE) {
            val byte = input.read()
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        val parts = line.split(' ')
        return if (parts.size == 3 && parts[0] == "GET" && parts[1].startsWith("/")) parts[1] else null
    }

    private fun respond(connection: Socket, callback: BrowserCallback?) {
        val (status, text) = when (callback) {
            is BrowserCallback.Code -> "200 OK" to "Capynib got the sign-in. You can close this tab and go back to the app."
            is BrowserCallback.Error -> "200 OK" to "Sign-in did not finish (${callback.error}). You can close this tab."
            null -> "404 Not Found" to "Not a Capynib sign-in."
        }
        val body = "<!doctype html><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">" +
            "<title>Capynib</title><p style=\"font:18px sans-serif;margin:2em\">$text</p>"
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        try {
            connection.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        } catch (e: IOException) {
            // The browser went away; the callback it carried still counts.
        }
    }

    companion object {
        /** The port Capynib's redirect address uses; the sign-in provider must allow that address. */
        const val PORT = 47815
        private const val HOST = "127.0.0.1"
        private const val PATH = "/capynib"
        private const val MAX_REQUEST_LINE = 8192
        private const val READ_TIMEOUT_MS = 10_000

        /** Starts listening on [port], 0 for any free port; fails when the port is taken. */
        fun open(port: Int = PORT): ApiResult<LoopbackReceiver> =
            try {
                ApiResult.Ok(LoopbackReceiver(ServerSocket(port, 4, InetAddress.getByName(HOST))))
            } catch (e: IOException) {
                ApiResult.Failed(SyncFailure.Unreachable("cannot listen on $HOST:$port for the sign-in: ${e.message}"))
            }
    }
}

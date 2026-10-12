package io.github.markovcd.quicknib

import androidx.test.core.app.ApplicationProvider
import io.github.markovcd.quicknib.core.memos.FakeMemosServer
import io.github.markovcd.quicknib.settings.TokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** An [AppGraph] over an in-memory database, a reversible fake cipher and a fake memos server. */
object TestGraphs {
    fun create(server: FakeMemosServer = FakeMemosServer()): AppGraph = AppGraph(
        context = ApplicationProvider.getApplicationContext(),
        databaseName = null,
        cipher = ReversingCipher,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        api = { _, _ -> server },
    )

    object ReversingCipher : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()

        override fun decrypt(encrypted: String) = encrypted.removePrefix("enc:").takeIf { encrypted.startsWith("enc:") }?.reversed()
    }
}

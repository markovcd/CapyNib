package io.github.markovcd.capynib.core.memos

/** A site the memos server lets people sign in through, such as Google. [name] is the server's id for it. */
data class SignInProvider(
    val name: String,
    val title: String,
    val authUrl: String,
    val clientId: String,
    val scopes: List<String>,
)

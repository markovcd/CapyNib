package io.github.markovcd.capynib.core.memos

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Signs in to a memos server through one of its [SignInProvider]s in the browser, then has the
 * server make an access token for the app. The provider sends the browser back to a
 * [LoopbackReceiver] on this device, so the provider must allow [LoopbackReceiver.redirectUri].
 */
class BrowserSignIn(
    server: ServerAddress,
    private val tokenDescription: String,
    private val port: Int = LoopbackReceiver.PORT,
    client: OkHttpClient = MemosHttp.defaultClient,
    private val random: SecureRandom = SecureRandom(),
) {
    private val http = MemosHttp(server, client)

    /** The providers the server offers that a browser can sign in through. */
    suspend fun providers(): ApiResult<List<SignInProvider>> =
        when (val result = http.call<IdentityProvidersDto>(Request.Builder().url(http.api("identity-providers")).get(), null)) {
            is ApiResult.Ok -> ApiResult.Ok(
                result.value.identityProviders.mapNotNull { dto ->
                    val oauth2 = dto.config.oauth2Config
                    if (dto.type != "OAUTH2" || oauth2 == null || oauth2.authUrl.toHttpUrlOrNull() == null) return@mapNotNull null
                    SignInProvider(dto.name, dto.title, oauth2.authUrl, oauth2.clientId, oauth2.scopes)
                },
            )
            is ApiResult.Failed -> result
            ApiResult.NotFound, ApiResult.AlreadyExists -> http.unexpected("identity-providers")
        }

    /** Hands [openBrowser] the provider's sign-in page, then waits for the browser to come back. */
    suspend fun signIn(provider: SignInProvider, openBrowser: (String) -> Unit): SignInResult {
        val receiver = when (val opened = LoopbackReceiver.open(port)) {
            is ApiResult.Ok -> opened.value
            is ApiResult.Failed -> return SignInResult.Failed(opened.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> error("opening a socket finds no memo")
        }
        val state = randomToken()
        val verifier = randomToken()
        val redirectUri = receiver.redirectUri
        val arrived = receiver.use {
            openBrowser(authorizationUrl(provider, redirectUri, state, challenge(verifier)))
            it.await(state)
        }
        return when (arrived) {
            is BrowserCallback.Error -> SignInResult.Refused("${provider.title} sign-in: ${arrived.error}")
            is BrowserCallback.Code -> finish(provider, arrived.code, verifier, redirectUri)
        }
    }

    private suspend fun finish(provider: SignInProvider, code: String, verifier: String, redirectUri: String): SignInResult {
        val body = buildJsonObject {
            putJsonObject("ssoCredentials") {
                put("idpName", provider.name)
                put("code", code)
                put("redirectUri", redirectUri)
                put("codeVerifier", verifier)
            }
        }
        val signIn = Request.Builder().url(http.api("auth/signin")).post(body.toString().toRequestBody(MemosHttp.jsonType))
        val session = when (val result = http.call<SignInReplyDto>(signIn, null)) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failed -> return refusedOrFailed(result.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> return SignInResult.Failed(http.unexpected("auth/signin").failure)
        }
        val user = session.user?.takeIf { it.name.isNotEmpty() && session.accessToken.isNotEmpty() }
            ?: return SignInResult.Failed(http.unexpected("auth/signin without a user or token").failure)

        val tokenBody = buildJsonObject {
            put("parent", user.name)
            put("description", tokenDescription)
            put("expiresInDays", TOKEN_LIFETIME_DAYS)
        }
        val create = Request.Builder().url(http.api("${user.name}/personalAccessTokens")).post(tokenBody.toString().toRequestBody(MemosHttp.jsonType))
        return when (val result = http.call<PersonalAccessTokenReplyDto>(create, session.accessToken)) {
            is ApiResult.Ok -> result.value.token.takeIf { it.isNotEmpty() }?.let { SignInResult.SignedIn(user.username, it) }
                ?: SignInResult.Failed(http.unexpected("personalAccessTokens without a token").failure)
            is ApiResult.Failed -> SignInResult.Failed(result.failure)
            ApiResult.NotFound, ApiResult.AlreadyExists -> SignInResult.Failed(http.unexpected("personalAccessTokens").failure)
        }
    }

    /** The server turning the sign-in down (bad code, user not allowed) is a refusal, not a broken server. */
    private fun refusedOrFailed(failure: SyncFailure): SignInResult = when {
        failure is SyncFailure.Unauthorized -> SignInResult.Refused(failure.message)
        failure is SyncFailure.Server && failure.httpStatus in REFUSED_STATUSES -> SignInResult.Refused(failure.message)
        else -> SignInResult.Failed(failure)
    }

    private fun authorizationUrl(provider: SignInProvider, redirectUri: String, state: String, challenge: String): String =
        requireNotNull(provider.authUrl.toHttpUrlOrNull()) { "provider ${provider.name} has no usable sign-in address" }
            .newBuilder()
            .addQueryParameter("client_id", provider.clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("state", state)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", provider.scopes.joinToString(" "))
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

    private fun randomToken(): String = ByteArray(32).also(random::nextBytes).let(base64Url::encodeToString)

    private companion object {
        val base64Url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        /** Bad request and permission denied: the server read the sign-in and turned it down. */
        val REFUSED_STATUSES = setOf(400, 403)

        /** How long the access token lasts; after that the server refuses it and the user signs in again. */
        const val TOKEN_LIFETIME_DAYS = 30

        /** The PKCE S256 challenge for [verifier] (RFC 7636). */
        fun challenge(verifier: String): String =
            base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }
}

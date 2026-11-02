package io.github.markovcd.capynib.core.memos

/**
 * Which shape of the memos API a server speaks. memos 26.10 dropped the `v1` from its routes and
 * renamed some fields, and answers the old routes with 410 Gone.
 */
internal enum class ApiGeneration(val root: String, val signInProviderField: String) {
    /** memos 0.31 and older: `/api/v1/...`. */
    V1("api/v1/", "idpName"),

    /** memos 26.10 and later: `/api/...`. */
    Unversioned("api/", "identityProvider"),
}

package io.github.markovcd.capynib.settings

/** Encrypts the access token at rest. The device boundary: the keystore in the app, a fake in tests. */
interface TokenCipher {
    fun encrypt(plain: String): String

    /** Null when the text cannot be decrypted, such as after the key was lost with a reinstall. */
    fun decrypt(encrypted: String): String?
}

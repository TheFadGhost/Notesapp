package com.fadghost.notesapp.data.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hand-rolled Android-Keystore-wrapped AES-GCM (PLAN.md §3/§5 — "Keystore-wrapped
 * AES-GCM blob, NOT the deprecated EncryptedSharedPreferences"). The AES key
 * lives inside the AndroidKeyStore (non-exportable, hardware-backed where
 * available); we only ever hold ciphertext at rest. Blob layout:
 *
 *   [1 byte IV length][IV bytes][GCM ciphertext+tag]
 *
 * The plaintext API key exists only transiently in memory during encrypt/decrypt
 * and is never written to logs, prefs, or backups.
 */
class KeystoreCrypto(
    private val alias: String = DEFAULT_ALIAS
) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Encrypt [plaintext] UTF-8 bytes into a self-describing blob. */
    fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return ByteArray(1 + iv.size + ct.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(ct, 0, out, 1 + iv.size, ct.size)
        }
    }

    /** Decrypt a blob produced by [encrypt]; null if the blob is malformed/undecryptable. */
    fun decrypt(blob: ByteArray): String? = runCatching {
        if (blob.isEmpty()) return null
        val ivLen = blob[0].toInt() and 0xFF
        if (blob.size < 1 + ivLen) return null
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey() ?: return null, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    /** Remove the wrapping key (called when the user clears their API key). */
    fun deleteKey() {
        runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    private fun getKey(): SecretKey? =
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun getOrCreateKey(): SecretKey =
        getKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "notesapp_openrouter_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

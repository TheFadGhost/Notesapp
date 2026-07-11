package com.fadghost.notesapp.data.ai

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the OpenRouter API key as a Keystore-wrapped AES-GCM blob (PLAN.md
 * §3/§5). The ciphertext (base64) is the only thing on disk; the AES key never
 * leaves the AndroidKeyStore. This DataStore is deliberately separate from notes
 * data and is NEVER serialised into a backup/export (backups only carry
 * [com.fadghost.notesapp.data.backup.BackupData], which has no secret fields),
 * and `android:allowBackup=false` keeps it out of system auto-backup too.
 *
 * The plaintext key is only ever returned by [get] for the moment a request is
 * built; it is never logged. [redact] produces a safe display form for any UI or
 * error surface.
 */
private val Context.aiSecretsStore by preferencesDataStore(name = "ai_secrets")

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto
) {
    private val blobKey = stringPreferencesKey("openrouter_key_blob")

    /** Whether a key is stored — drives no-key UX without ever decrypting. */
    val hasKey: Flow<Boolean> = context.aiSecretsStore.data.map { it[blobKey] != null }

    /** Encrypt and store [plaintext]. Blank clears the key. */
    suspend fun set(plaintext: String) {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) { clear(); return }
        val blob = crypto.encrypt(trimmed)
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        context.aiSecretsStore.edit { it[blobKey] = encoded }
    }

    /** Decrypt and return the key, or null if none is stored / decryption fails. */
    suspend fun get(): String? {
        val encoded = context.aiSecretsStore.data.first()[blobKey] ?: return null
        val blob = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        return crypto.decrypt(blob)
    }

    suspend fun hasKeyNow(): Boolean = get() != null

    /** Wipe the stored blob and the wrapping Keystore key. */
    suspend fun clear() {
        context.aiSecretsStore.edit { it.remove(blobKey) }
        crypto.deleteKey()
    }

    companion object {
        /** Mask a key for display/error surfaces, e.g. `sk-or-…a1b2`. */
        fun redact(key: String?): String {
            if (key.isNullOrBlank()) return "(none)"
            val visible = key.takeLast(4)
            val prefix = key.take(6).takeIf { key.length > 10 } ?: ""
            return if (prefix.isNotEmpty()) "$prefix…$visible" else "…$visible"
        }
    }
}

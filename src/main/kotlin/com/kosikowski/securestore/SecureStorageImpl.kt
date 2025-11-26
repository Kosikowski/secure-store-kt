package com.kosikowski.securestore

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Secure storage implementation using Google Tink for encryption.
 *
 * Architecture:
 * - Uses Tink AEAD (Authenticated Encryption with Associated Data) for all encryption
 * - Leverages Android Keystore (via Tink's integration) for master key protection
 * - Separate keysets for files and SharedPreferences for defense in depth
 * - All file operations are synchronized to prevent concurrent access corruption
 *
 * Security features:
 * - Hardware-backed encryption keys when available (StrongBox/TEE)
 * - AES-256-GCM encryption
 * - Authenticated encryption prevents tampering
 * - Keys never leave the secure hardware
 *
 * @param context Android application context
 * @param ioDispatcher Coroutine dispatcher for IO operations (defaults to Dispatchers.IO)
 */
class SecureStorageImpl(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecureStorage {

    init {
        // Register Tink AEAD primitives
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("Failed to initialize Tink AEAD", e)
        }
    }

    private val appContext: Context = context.applicationContext

    private val json: Json by lazy {
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    /**
     * Storage context that uses device-protected storage on API 24+.
     * This keeps secrets available even before the user unlocks the device,
     * which is appropriate for unattended terminal-style apps.
     */
    private val storageContext: Context by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }
    }

    /**
     * AEAD primitive for file encryption/decryption.
     * Uses Android Keystore integration to protect the encryption key.
     * Thread-safe: Uses synchronized lazy initialization.
     */
    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val keysetHandle =
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(storageContext, TINK_KEYSET_PREF, TINK_KEYSET_NAME)
                    .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle

            keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create AEAD primitive", e)
        }
    }

    /**
     * SharedPreferences with Tink encryption for key-value storage.
     * Uses a separate keyset from file encryption for defense in depth.
     * Thread-safe: Uses synchronized lazy initialization.
     */
    private val sharedPreferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            val keysetHandle =
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(storageContext, TINK_PREFS_KEYSET_PREF, TINK_PREFS_KEYSET_NAME)
                    .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle

            val aeadForPrefs = keysetHandle.getPrimitive(Aead::class.java)

            // Create encrypted SharedPreferences using manual Tink encryption
            TinkEncryptedSharedPreferences(
                storageContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE),
                aeadForPrefs,
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create encrypted SharedPreferences", e)
        }
    }

    private val storageDirectory: File by lazy {
        File(storageContext.filesDir, SECURE_FILE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    // File-level locks to prevent concurrent access to the same file
    private val fileLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    private fun getFileLock(fileName: String): Any = fileLocks.getOrPut(fileName) { Any() }

    override suspend fun <T> putObject(
        key: String,
        value: T,
        serializer: KSerializer<T>,
    ) = withContext(ioDispatcher) {
        val payload = json.encodeToString(serializer, value)
        sharedPreferences
            .edit()
            .putString(key, payload)
            .commitOrThrow()
    }

    override suspend fun <T> getObject(
        key: String,
        serializer: KSerializer<T>,
    ): T? =
        withContext(ioDispatcher) {
            sharedPreferences.getString(key, null)?.let { raw ->
                runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
            }
        }

    override suspend fun putString(
        key: String,
        value: String,
    ) = withContext(ioDispatcher) {
        sharedPreferences
            .edit()
            .putString(key, value)
            .commitOrThrow()
    }

    override suspend fun getString(key: String): String? =
        withContext(ioDispatcher) {
            sharedPreferences.getString(key, null)
        }

    override suspend fun removeString(key: String) =
        withContext(ioDispatcher) {
            sharedPreferences
                .edit()
                .remove(key)
                .commitOrThrow()
        }

    override suspend fun saveBlob(
        fileName: String,
        payload: ByteArray,
    ) = withContext(ioDispatcher) {
        synchronized(getFileLock(fileName)) {
            val ciphertext = aead.encrypt(payload, null)
            getFile(fileName).writeBytes(ciphertext)
        }
    }

    override suspend fun readBlob(fileName: String): ByteArray? =
        withContext(ioDispatcher) {
            synchronized(getFileLock(fileName)) {
                val target = getFile(fileName)
                if (!target.exists()) return@withContext null
                runCatching {
                    aead.decrypt(target.readBytes(), null)
                }.getOrNull()
            }
        }

    override suspend fun deleteBlob(fileName: String): Boolean =
        withContext(ioDispatcher) {
            synchronized(getFileLock(fileName)) {
                val result = getFile(fileName).delete()
                // Clean up the lock if file was deleted
                if (result) {
                    fileLocks.remove(fileName)
                }
                result
            }
        }

    override suspend fun clearAll(): Unit =
        withContext(ioDispatcher) {
            // Use a separate lock for clearing all to prevent concurrent clear operations
            synchronized(this@SecureStorageImpl) {
                sharedPreferences
                    .edit()
                    .clear()
                    .commitOrThrow()

                storageDirectory.listFiles()?.forEach { file ->
                    synchronized(getFileLock(file.name)) {
                        file.delete()
                    }
                }
                // Clear all file locks after clearing storage
                fileLocks.clear()
            }
        }

    private fun getFile(fileName: String): File = File(storageDirectory, fileName)

    private fun SharedPreferences.Editor.commitOrThrow() {
        if (!commit()) {
            throw IOException("Failed to write secure preferences")
        }
    }

    /**
     * Wrapper for SharedPreferences that encrypts values using Tink AEAD.
     * Keys are stored in plaintext for simplicity (they're obfuscated by the storage location).
     */
    private class TinkEncryptedSharedPreferences(
        private val delegate: SharedPreferences,
        private val aead: Aead,
    ) : SharedPreferences by delegate {
        override fun getString(
            key: String?,
            defValue: String?,
        ): String? {
            if (key == null) return defValue
            val encrypted = delegate.getString(key, null) ?: return defValue
            return try {
                val decrypted =
                    aead.decrypt(
                        android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT),
                        null,
                    )
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                defValue
            }
        }

        override fun edit(): SharedPreferences.Editor = TinkEditor(delegate.edit(), aead)
    }

    /**
     * Editor that encrypts values before storing them.
     */
    private class TinkEditor(
        private val delegate: SharedPreferences.Editor,
        private val aead: Aead,
    ) : SharedPreferences.Editor by delegate {
        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor {
            if (key == null || value == null) return this
            return try {
                val encrypted = aead.encrypt(value.toByteArray(Charsets.UTF_8), null)
                val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
                delegate.putString(key, encoded)
            } catch (e: Exception) {
                // If encryption fails, don't store the value
                this
            }
        }
    }

    private companion object {
        private const val SHARED_PREFS_NAME = "secure_storage_prefs"
        private const val SECURE_FILE_DIR = "secure_blobs"
        private const val TINK_KEYSET_PREF = "secure_storage_tink_keyset_pref"
        private const val TINK_KEYSET_NAME = "secure_storage_tink_key"
        private const val TINK_PREFS_KEYSET_PREF = "secure_storage_prefs_keyset_pref"
        private const val TINK_PREFS_KEYSET_NAME = "secure_storage_prefs_key"

        // Android Keystore URI for master key protection
        private const val MASTER_KEY_URI = "android-keystore://secure_store_master_key"
    }
}


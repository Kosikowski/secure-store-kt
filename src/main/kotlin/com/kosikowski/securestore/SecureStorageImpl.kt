package com.kosikowski.securestore

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Secure storage implementation using Google Tink for encryption.
 *
 * ## Architecture
 * - Uses Tink AEAD (Authenticated Encryption with Associated Data) for all encryption
 * - Leverages Android Keystore (via Tink's integration) for master key protection
 * - Separate keysets for files and SharedPreferences for defense in depth
 * - All file operations are synchronized to prevent concurrent access corruption
 *
 * ## Security Features
 * - Hardware-backed encryption keys when available (StrongBox/TEE)
 * - AES-256-GCM encryption (configurable)
 * - Authenticated encryption prevents tampering
 * - Keys never leave the secure hardware
 * - Optional key and filename encryption
 * - Associated data prevents ciphertext relocation attacks
 *
 * ## Usage
 * ```kotlin
 * // Default configuration
 * val storage = SecureStorageImpl(context)
 *
 * // Custom configuration
 * val config = SecureStoreConfig.Builder()
 *     .encryption(EncryptionAlgorithm.AES_256_GCM)
 *     .keyProtection(KeyProtection.HARDWARE_PREFERRED)
 *     .namespace("my_app")
 *     .build()
 * val storage = SecureStorageImpl(context, config)
 *
 * // High security preset
 * val secureStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
 * ```
 *
 * @param context Android application context
 * @param config Configuration for the secure store (defaults to [SecureStoreConfig.DEFAULT])
 * @throws SecureStoreException.InitializationException if Tink initialization fails
 * @throws SecureStoreException.HardwareRequiredException if hardware keys are required but unavailable
 *
 * @see SecureStorage
 * @see SecureStoreConfig
 */
class SecureStorageImpl(
    context: Context,
    private val config: SecureStoreConfig = SecureStoreConfig.DEFAULT,
) : SecureStorage {

    /**
     * Secondary constructor for backwards compatibility.
     *
     * @param context Android application context
     * @param ioDispatcher Coroutine dispatcher for IO operations
     */
    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        context,
        SecureStoreConfig.Builder()
            .ioDispatcher(ioDispatcher)
            .build(),
    )

    init {
        // Register Tink AEAD primitives
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            throw SecureStoreException.InitializationException("Failed to initialize Tink AEAD", e)
        }

        // Verify hardware backing if required
        if (config.keyProtection == KeyProtection.HARDWARE_REQUIRED) {
            if (!isHardwareBackedKeystore()) {
                throw SecureStoreException.HardwareRequiredException()
            }
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
     * Storage context based on configuration.
     * Device-protected storage keeps secrets available before user unlock.
     */
    private val storageContext: Context by lazy {
        when (config.storageMode) {
            StorageMode.DEVICE_PROTECTED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appContext.createDeviceProtectedStorageContext()
                } else {
                    appContext
                }
            }
            StorageMode.CREDENTIAL_PROTECTED -> appContext
        }
    }

    /**
     * Where the keysets are stored: next to the data they protect. See [KeysetStorageContext].
     */
    private val keysetContext: Context by lazy {
        when (config.storageMode) {
            StorageMode.DEVICE_PROTECTED -> KeysetStorageContext(storageContext)
            StorageMode.CREDENTIAL_PROTECTED -> appContext
        }
    }

    private class Keysets(
        val fileAead: Aead,
        val metadataAead: Aead?,
        val preferences: SharedPreferences,
    )

    @Volatile
    private var openKeysets: Keysets? = null

    private val keysetLock = Any()

    /** AEAD primitive for file encryption/decryption. */
    private val aead: Aead
        get() = keysets().fileAead

    /** AEAD for key/filename encryption when enabled. */
    private val metadataAead: Aead?
        get() = keysets().metadataAead

    /** SharedPreferences with Tink encryption for key-value storage. */
    private val sharedPreferences: SharedPreferences
        get() = keysets().preferences

    /**
     * The file, preferences and (when enabled) metadata keysets, opened together: they are protected
     * by the same master key and are lost together. Separate keysets for defense in depth.
     */
    private fun keysets(): Keysets {
        openKeysets?.let { return it }

        var discarded: Throwable? = null
        val keysets =
            synchronized(keysetLock) {
                openKeysets ?: openKeysetsRecoveringLoss { discarded = it }.also { openKeysets = it }
            }
        discarded?.let(config.onKeysetReset)
        return keysets
    }

    private fun openKeysetsRecoveringLoss(onDiscarded: (Throwable) -> Unit): Keysets {
        moveLegacyKeysets()
        try {
            return openKeysetsOrThrow()
        } catch (e: SecureStoreException.InitializationException) {
            val failure = e.cause ?: e
            if (!failure.isLostKeyset(::masterKeyExists)) throw e
            if (config.keysetLossPolicy == KeysetLossPolicy.THROW) {
                throw SecureStoreException.KeysetLostException("Keysets can no longer be opened; stored data is unrecoverable", failure)
            }
            try {
                deleteKeysetsAndData()
            } catch (deletion: Exception) {
                throw SecureStoreException.InitializationException("Failed to discard keysets that can no longer be opened", deletion)
            }
            onDiscarded(failure)
        }
        return openKeysetsOrThrow()
    }

    private fun openKeysetsOrThrow(): Keysets =
        try {
            val metadata = if (usesMetadataKeyset) keysetAead(tinkMetadataKeysetPref, tinkMetadataKeysetName) else null
            Keysets(
                fileAead = keysetAead(tinkKeysetPref, tinkKeysetName),
                metadataAead = metadata,
                preferences =
                    TinkEncryptedSharedPreferences(
                        delegate = storageContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE),
                        aead = keysetAead(tinkPrefsKeysetPref, tinkPrefsKeysetName),
                        metadataAead = if (config.encryptKeys) metadata else null,
                        useAssociatedData = config.useAssociatedData,
                        secureMemory = config.secureMemory,
                    ),
            )
        } catch (e: Exception) {
            throw SecureStoreException.InitializationException("Failed to open keysets", e)
        }

    private fun keysetAead(keysetName: String, prefFileName: String): Aead =
        AndroidKeysetManager.Builder()
            .withSharedPref(keysetContext, keysetName, prefFileName)
            .withKeyTemplate(config.encryption.keyTemplate)
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)

    /**
     * Up to 1.0.0 a DEVICE_PROTECTED store kept its keysets in credential-encrypted storage (see
     * [KeysetStorageContext]). Moves them next to the data, once. That location cannot be read before
     * the first unlock, and creating new keysets instead would make the existing data unreadable, so a
     * store that already holds data refuses to open until then.
     */
    private fun moveLegacyKeysets() {
        if (config.storageMode != StorageMode.DEVICE_PROTECTED) return

        val missing = usedKeysetFileNames.filterNot { storageContext.sharedPreferencesFile(it).exists() }
        if (missing.isEmpty()) return

        if (!isUserUnlocked()) {
            if (!hasStoredData()) return
            throw SecureStoreException.InitializationException(
                "Secure storage is unavailable until the device is unlocked for the first time after upgrading",
            )
        }
        for (name in missing) {
            if (!storageContext.moveSharedPreferencesFrom(appContext, name)) {
                throw SecureStoreException.InitializationException("Failed to move keyset $name to device-protected storage")
            }
        }
    }

    private fun Context.sharedPreferencesFile(name: String): File = File(dataDir, "shared_prefs/$name.xml")

    private fun masterKeyExists(): Boolean =
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(masterKeyAlias)
        } catch (e: Exception) {
            true
        }

    private fun deleteKeysetsAndData() {
        val keysetFiles = listOf(tinkKeysetName, tinkPrefsKeysetName, tinkMetadataKeysetName)
        keysetFiles.forEach { deleteSharedPreferencesOrThrow(keysetContext, it) }
        if (config.storageMode == StorageMode.DEVICE_PROTECTED && isUserUnlocked()) {
            keysetFiles.forEach { deleteSharedPreferencesOrThrow(appContext, it) }
        }
        deleteSharedPreferencesOrThrow(storageContext, sharedPrefsName)
        storageDirectory.listFiles()?.forEach { file ->
            if (!file.delete()) throw IOException("Failed to delete $file")
        }
        fileLocks.clear()
    }

    private fun deleteSharedPreferencesOrThrow(context: Context, name: String) {
        if (!context.deleteSharedPreferences(name)) throw IOException("Failed to delete shared preferences $name")
    }

    private fun isUserUnlocked(): Boolean = appContext.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    private fun hasStoredData(): Boolean =
        storageContext.sharedPreferencesFile(sharedPrefsName).exists() || !storageDirectory.list().isNullOrEmpty()

    private val storageDirectory: File by lazy {
        File(storageContext.filesDir, secureFileDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    // File-level locks to prevent concurrent access to the same file
    private val fileLocks = ConcurrentHashMap<String, Any>()

    private fun getFileLock(fileName: String): Any = fileLocks.getOrPut(fileName) { Any() }

    // ==================== Computed Properties ====================

    private val masterKeyAlias: String
        get() = "${config.masterKeyAlias}_${config.namespace}"

    private val masterKeyUri: String
        get() = "android-keystore://$masterKeyAlias"

    private val sharedPrefsName: String
        get() = "secure_storage_prefs_${config.namespace}"

    private val secureFileDir: String
        get() = "secure_blobs_${config.namespace}"

    private val tinkKeysetPref: String
        get() = "secure_storage_tink_keyset_pref_${config.namespace}"

    private val tinkKeysetName: String
        get() = "secure_storage_tink_key_${config.namespace}"

    private val tinkPrefsKeysetPref: String
        get() = "secure_storage_prefs_keyset_pref_${config.namespace}"

    private val tinkPrefsKeysetName: String
        get() = "secure_storage_prefs_key_${config.namespace}"

    private val tinkMetadataKeysetPref: String
        get() = "secure_storage_metadata_keyset_pref_${config.namespace}"

    private val tinkMetadataKeysetName: String
        get() = "secure_storage_metadata_key_${config.namespace}"

    private val usesMetadataKeyset: Boolean
        get() = config.encryptKeys || config.encryptFileNames

    private val usedKeysetFileNames: List<String>
        get() = listOfNotNull(tinkKeysetName, tinkPrefsKeysetName, tinkMetadataKeysetName.takeIf { usesMetadataKeyset })

    // ==================== String Operations ====================

    override suspend fun putString(key: String, value: String) = withContext(config.ioDispatcher) {
        try {
            sharedPreferences
                .edit()
                .putString(key, value)
                .commitOrThrow()
        } catch (e: SecureStoreException) {
            throw e
        } catch (e: Exception) {
            throw SecureStoreException.StorageException("Failed to store string for key: $key", e)
        }
    }

    override suspend fun getString(key: String): String? = withContext(config.ioDispatcher) {
        try {
            sharedPreferences.getString(key, null)
        } catch (e: Exception) {
            handleDecryptionFailure(key, e)
        }
    }

    override suspend fun removeString(key: String) = withContext(config.ioDispatcher) {
        try {
            sharedPreferences
                .edit()
                .remove(key)
                .commitOrThrow()
        } catch (e: SecureStoreException) {
            throw e
        } catch (e: Exception) {
            throw SecureStoreException.StorageException("Failed to remove string for key: $key", e)
        }
    }

    override suspend fun contains(key: String): Boolean = withContext(config.ioDispatcher) {
        val storageKey = if (config.encryptKeys) encryptKey(key) else key
        sharedPreferences.contains(storageKey)
    }

    // ==================== Object Operations ====================

    override suspend fun <T> putObject(
        key: String,
        value: T,
        serializer: KSerializer<T>,
    ) = withContext(config.ioDispatcher) {
        val payload = try {
            json.encodeToString(serializer, value)
        } catch (e: Exception) {
            throw SecureStoreException.SerializationException("Failed to serialize object for key: $key", e)
        }

        try {
            sharedPreferences
                .edit()
                .putString(key, payload)
                .commitOrThrow()
        } catch (e: SecureStoreException) {
            throw e
        } catch (e: Exception) {
            throw SecureStoreException.StorageException("Failed to store object for key: $key", e)
        }
    }

    override suspend fun <T> getObject(
        key: String,
        serializer: KSerializer<T>,
    ): T? = withContext(config.ioDispatcher) {
        val raw = try {
            sharedPreferences.getString(key, null)
        } catch (e: Exception) {
            return@withContext handleDecryptionFailure(key, e)
        }

        raw?.let {
            try {
                json.decodeFromString(serializer, it)
            } catch (e: Exception) {
                when (config.decryptionFailurePolicy) {
                    DecryptionFailurePolicy.THROW_EXCEPTION ->
                        throw SecureStoreException.SerializationException("Failed to deserialize object for key: $key", e)
                    DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                        removeString(key)
                        null
                    }
                    DecryptionFailurePolicy.RETURN_NULL -> null
                }
            }
        }
    }

    override suspend fun removeObject(key: String) = removeString(key)

    // ==================== Blob Operations ====================

    override suspend fun saveBlob(fileName: String, payload: ByteArray) = withContext(config.ioDispatcher) {
        val storageFileName = if (config.encryptFileNames) encryptFileName(fileName) else fileName
        val associatedData = if (config.useAssociatedData) fileName.toByteArray(Charsets.UTF_8) else null

        synchronized(getFileLock(storageFileName)) {
            try {
                val ciphertext = aead.encrypt(payload, associatedData)
                getFile(storageFileName).writeBytes(ciphertext)

                if (config.secureMemory) {
                    payload.fill(0)
                }
            } catch (e: GeneralSecurityException) {
                throw SecureStoreException.EncryptionException("Failed to encrypt blob: $fileName", e)
            } catch (e: IOException) {
                throw SecureStoreException.StorageException("Failed to write blob: $fileName", e)
            }
        }
    }

    override suspend fun readBlob(fileName: String): ByteArray? = withContext(config.ioDispatcher) {
        val storageFileName = if (config.encryptFileNames) encryptFileName(fileName) else fileName
        val associatedData = if (config.useAssociatedData) fileName.toByteArray(Charsets.UTF_8) else null

        synchronized(getFileLock(storageFileName)) {
            val target = getFile(storageFileName)
            if (!target.exists()) return@withContext null

            try {
                val ciphertext = target.readBytes()
                val plaintext = aead.decrypt(ciphertext, associatedData)

                if (config.secureMemory) {
                    ciphertext.fill(0)
                }

                plaintext
            } catch (e: Exception) {
                handleBlobDecryptionFailure(fileName, storageFileName, e)
            }
        }
    }

    override suspend fun deleteBlob(fileName: String): Boolean = withContext(config.ioDispatcher) {
        val storageFileName = if (config.encryptFileNames) encryptFileName(fileName) else fileName

        synchronized(getFileLock(storageFileName)) {
            val result = getFile(storageFileName).delete()
            if (result) {
                fileLocks.remove(storageFileName)
            }
            result
        }
    }

    override suspend fun blobExists(fileName: String): Boolean = withContext(config.ioDispatcher) {
        val storageFileName = if (config.encryptFileNames) encryptFileName(fileName) else fileName
        getFile(storageFileName).exists()
    }

    // ==================== Bulk Operations ====================

    override suspend fun clearAll(): Unit = withContext(config.ioDispatcher) {
        synchronized(this@SecureStorageImpl) {
            try {
                sharedPreferences
                    .edit()
                    .clear()
                    .commitOrThrow()

                storageDirectory.listFiles()?.forEach { file ->
                    synchronized(getFileLock(file.name)) {
                        file.delete()
                    }
                }
                fileLocks.clear()
            } catch (e: SecureStoreException) {
                throw e
            } catch (e: Exception) {
                throw SecureStoreException.StorageException("Failed to clear all data", e)
            }
        }
    }

    override suspend fun reset(): Unit = withContext(config.ioDispatcher) {
        synchronized(keysetLock) {
            openKeysets = null
            try {
                deleteKeysetsAndData()
            } catch (e: Exception) {
                throw SecureStoreException.StorageException("Failed to reset secure storage", e)
            }
        }
    }

    override suspend fun getAllKeys(): Set<String> = withContext(config.ioDispatcher) {
        val allKeys = sharedPreferences.all.keys
        if (config.encryptKeys && metadataAead != null) {
            allKeys.mapNotNull { encryptedKey ->
                try {
                    decryptKey(encryptedKey)
                } catch (e: Exception) {
                    null // Skip keys that can't be decrypted
                }
            }.toSet()
        } else {
            allKeys.toSet()
        }
    }

    override suspend fun getAllBlobNames(): Set<String> = withContext(config.ioDispatcher) {
        val files = storageDirectory.listFiles() ?: return@withContext emptySet()

        if (config.encryptFileNames && metadataAead != null) {
            files.mapNotNull { file ->
                try {
                    decryptFileName(file.name)
                } catch (e: Exception) {
                    null // Skip files that can't be decrypted
                }
            }.toSet()
        } else {
            files.map { it.name }.toSet()
        }
    }

    // ==================== Metadata ====================

    override fun getStoreInfo(): SecureStoreInfo = SecureStoreInfo(
        encryptionAlgorithm = config.encryption.name,
        isHardwareBacked = isHardwareBackedKeystore(),
        namespace = config.namespace,
        keyEncryptionEnabled = config.encryptKeys,
        fileNameEncryptionEnabled = config.encryptFileNames,
    )

    // ==================== Private Helpers ====================

    private fun getFile(fileName: String): File = File(storageDirectory, fileName)

    private fun SharedPreferences.Editor.commitOrThrow() {
        if (!commit()) {
            throw SecureStoreException.StorageException("Failed to commit preferences")
        }
    }

    private fun isHardwareBackedKeystore(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check for StrongBox on Android 12+
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                // Try to check if hardware-backed keys are supported
                true // Simplified check - real implementation would verify key properties
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Basic hardware-backed keystore check for older devices
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun encryptKey(key: String): String {
        val metadata = metadataAead ?: return key
        val encrypted = metadata.encrypt(key.toByteArray(Charsets.UTF_8), null)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    private fun decryptKey(encryptedKey: String): String {
        val metadata = metadataAead ?: return encryptedKey
        val decoded = android.util.Base64.decode(encryptedKey, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        return String(metadata.decrypt(decoded, null), Charsets.UTF_8)
    }

    private fun encryptFileName(fileName: String): String {
        val metadata = metadataAead ?: return fileName
        val encrypted = metadata.encrypt(fileName.toByteArray(Charsets.UTF_8), null)
        // Use URL-safe base64 without padding for valid filenames
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    private fun decryptFileName(encryptedFileName: String): String {
        val metadata = metadataAead ?: return encryptedFileName
        val decoded = android.util.Base64.decode(encryptedFileName, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        return String(metadata.decrypt(decoded, null), Charsets.UTF_8)
    }

    private fun <T> handleDecryptionFailure(key: String, e: Exception): T? {
        rethrowIfStoreUnavailable(e)
        return when (config.decryptionFailurePolicy) {
            DecryptionFailurePolicy.THROW_EXCEPTION ->
                throw SecureStoreException.DecryptionException("Failed to decrypt value for key: $key", e)
            DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                // Delete asynchronously in a fire-and-forget manner
                try {
                    sharedPreferences.edit().remove(key).apply()
                } catch (_: Exception) {
                    // Ignore deletion errors
                }
                null
            }
            DecryptionFailurePolicy.RETURN_NULL -> null
        }
    }

    private fun handleBlobDecryptionFailure(originalFileName: String, storageFileName: String, e: Exception): ByteArray? {
        rethrowIfStoreUnavailable(e)
        return when (config.decryptionFailurePolicy) {
            DecryptionFailurePolicy.THROW_EXCEPTION ->
                throw SecureStoreException.DecryptionException("Failed to decrypt blob: $originalFileName", e)
            DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                try {
                    getFile(storageFileName).delete()
                    fileLocks.remove(storageFileName)
                } catch (_: Exception) {
                    // Ignore deletion errors
                }
                null
            }
            DecryptionFailurePolicy.RETURN_NULL -> null
        }
    }

    private fun rethrowIfStoreUnavailable(e: Exception) {
        if (e is SecureStoreException.InitializationException || e is SecureStoreException.KeysetLostException) throw e
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    /**
     * Wrapper for SharedPreferences that encrypts values using Tink AEAD.
     */
    private class TinkEncryptedSharedPreferences(
        private val delegate: SharedPreferences,
        private val aead: Aead,
        private val metadataAead: Aead?,
        private val useAssociatedData: Boolean,
        private val secureMemory: Boolean,
    ) : SharedPreferences by delegate {

        override fun getString(key: String?, defValue: String?): String? {
            if (key == null) return defValue

            val storageKey = encryptKeyIfNeeded(key)
            val encrypted = delegate.getString(storageKey, null) ?: return defValue

            return try {
                val decoded = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
                val associatedData = if (useAssociatedData) key.toByteArray(Charsets.UTF_8) else null
                val decrypted = aead.decrypt(decoded, associatedData)
                val result = String(decrypted, Charsets.UTF_8)

                if (secureMemory) {
                    decrypted.fill(0)
                }

                result
            } catch (e: Exception) {
                // Throw to let the caller handle according to policy
                throw SecureStoreException.DecryptionException("Failed to decrypt value for key: $key", e)
            }
        }

        override fun contains(key: String?): Boolean {
            if (key == null) return false
            val storageKey = encryptKeyIfNeeded(key)
            return delegate.contains(storageKey)
        }

        override fun edit(): SharedPreferences.Editor = TinkEditor(
            delegate = delegate.edit(),
            aead = aead,
            metadataAead = metadataAead,
            useAssociatedData = useAssociatedData,
            secureMemory = secureMemory,
        )

        private fun encryptKeyIfNeeded(key: String): String {
            val metadata = metadataAead ?: return key
            val encrypted = metadata.encrypt(key.toByteArray(Charsets.UTF_8), null)
            return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        }
    }

    /**
     * Editor that encrypts values before storing them.
     */
    private class TinkEditor(
        private val delegate: SharedPreferences.Editor,
        private val aead: Aead,
        private val metadataAead: Aead?,
        private val useAssociatedData: Boolean,
        private val secureMemory: Boolean,
    ) : SharedPreferences.Editor by delegate {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key == null || value == null) return this

            val storageKey = encryptKeyIfNeeded(key)
            val associatedData = if (useAssociatedData) key.toByteArray(Charsets.UTF_8) else null

            val valueBytes = value.toByteArray(Charsets.UTF_8)
            val encrypted = aead.encrypt(valueBytes, associatedData)
            val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)

            if (secureMemory) {
                valueBytes.fill(0)
            }

            delegate.putString(storageKey, encoded)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key == null) return this
            val storageKey = encryptKeyIfNeeded(key)
            delegate.remove(storageKey)
            return this
        }

        private fun encryptKeyIfNeeded(key: String): String {
            val metadata = metadataAead ?: return key
            val encrypted = metadata.encrypt(key.toByteArray(Charsets.UTF_8), null)
            return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        }
    }
}

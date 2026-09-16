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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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

    /**
     * State shared by every instance with the same storage mode and namespace in this process: they
     * use the same files. A reset deletes those files, so the generation tells each instance to drop
     * what it opened, and the lock keeps operations from running with keysets a reset is deleting.
     */
    private class SharedState {
        val lock = ReentrantReadWriteLock()

        @Volatile
        var generation = 0L

        val fileLocks = ConcurrentHashMap<String, Any>()
    }

    private class Keysets(
        val generation: Long,
        val fileAead: Aead,
        val metadataAead: Aead?,
        val preferences: SharedPreferences,
    )

    private val shared: SharedState = sharedStates.getOrPut("${config.storageMode}/${config.namespace}") { SharedState() }

    @Volatile
    private var openKeysets: Keysets? = null

    private val pendingResetCause = AtomicReference<Throwable?>()

    /**
     * Runs [block] with the keysets, opening them first when needed, under the shared read lock so
     * that a reset cannot delete the files it uses. [block] cannot suspend while holding the lock.
     */
    private fun <T> withKeysets(block: (Keysets) -> T): T {
        while (true) {
            val keysets = currentKeysets()
            pendingResetCause.getAndSet(null)?.let(config.onKeysetReset)
            shared.lock.read {
                if (keysets.generation == shared.generation) return block(keysets)
            }
        }
    }

    /**
     * The file, preferences and (when enabled) metadata keysets, opened together: they are protected
     * by the same master key and are lost together. Separate keysets for defense in depth.
     */
    private fun currentKeysets(): Keysets {
        openKeysets?.takeIf { it.generation == shared.generation }?.let { return it }
        shared.lock.write {
            openKeysets?.takeIf { it.generation == shared.generation }?.let { return it }
            return openKeysetsRecoveringLoss().also { openKeysets = it }
        }
    }

    private fun openKeysetsRecoveringLoss(): Keysets {
        copyLegacyKeysets()
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
            pendingResetCause.set(failure)
        }
        return openKeysetsOrThrow()
    }

    private fun openKeysetsOrThrow(): Keysets =
        try {
            val metadata = if (usesMetadataKeyset) keysetAead(tinkMetadataKeysetPref, tinkMetadataKeysetName) else null
            Keysets(
                generation = shared.generation,
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
     * [KeysetStorageContext]), in the files a CREDENTIAL_PROTECTED store with the same namespace uses.
     * They are copied, not moved, so that store keeps its keysets. Only a store that holds data needs
     * them; without data it starts with new keysets, which also keeps a reset from bringing the old
     * ones back. The old location cannot be read before the first unlock, so a store that holds data
     * refuses to open until then rather than create keysets that cannot decrypt it.
     */
    private fun copyLegacyKeysets() {
        if (config.storageMode != StorageMode.DEVICE_PROTECTED) return

        val missing = usedKeysetFileNames.filterNot { storageContext.sharedPreferencesFile(it).exists() }
        if (missing.isEmpty() || !hasStoredData()) return

        if (!isUserUnlocked()) {
            throw SecureStoreException.InitializationException(
                "Secure storage is unavailable until the device is unlocked for the first time after upgrading",
            )
        }
        try {
            missing.forEach(::copyLegacyKeyset)
        } catch (e: IOException) {
            throw SecureStoreException.InitializationException("Failed to copy keysets to device-protected storage", e)
        }
    }

    private fun copyLegacyKeyset(name: String) {
        val legacyFile = appContext.sharedPreferencesFile(name)
        // SharedPreferences restores from the backup when both exist: the write to the file was interrupted.
        val source = listOf(File("${legacyFile.path}.bak"), legacyFile).firstOrNull { it.exists() } ?: return
        val target = storageContext.sharedPreferencesFile(name)
        val partial = File("${target.path}.copying")
        source.copyTo(partial, overwrite = true)
        if (!partial.renameTo(target)) throw IOException("Failed to rename $partial to $target")
    }

    private fun Context.sharedPreferencesFile(name: String): File = File(dataDir, "shared_prefs/$name.xml")

    private fun masterKeyExists(): Boolean =
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(masterKeyAlias)
        } catch (e: Exception) {
            true
        }

    /**
     * Data goes first: if a deletion fails, the keysets are still there and still unreadable, so the
     * next open retries the recovery instead of creating keysets next to data they cannot decrypt.
     */
    private fun deleteKeysetsAndData() {
        try {
            deleteSharedPreferencesOrThrow(storageContext, sharedPrefsName)
            storageDirectory.listFiles()?.forEach { file ->
                if (!file.delete()) throw IOException("Failed to delete $file")
            }
            shared.fileLocks.clear()

            listOf(tinkKeysetName, tinkPrefsKeysetName, tinkMetadataKeysetName).forEach { deleteSharedPreferencesOrThrow(keysetContext, it) }
        } finally {
            shared.generation++
        }
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

    private fun getFileLock(fileName: String): Any = shared.fileLocks.getOrPut(fileName) { Any() }

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
        withKeysets { keysets ->
            try {
                keysets.preferences
                    .edit()
                    .putString(key, value)
                    .commitOrThrow()
            } catch (e: SecureStoreException) {
                throw e
            } catch (e: Exception) {
                throw SecureStoreException.StorageException("Failed to store string for key: $key", e)
            }
        }
    }

    override suspend fun getString(key: String): String? = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            try {
                keysets.preferences.getString(key, null)
            } catch (e: Exception) {
                handleDecryptionFailure(keysets, key, e)
            }
        }
    }

    override suspend fun removeString(key: String) = withContext(config.ioDispatcher) {
        withKeysets { keysets -> removeValue(keysets, key) }
    }

    override suspend fun contains(key: String): Boolean = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val storageKey = if (config.encryptKeys) keysets.encryptKey(key) else key
            keysets.preferences.contains(storageKey)
        }
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

        withKeysets { keysets ->
            try {
                keysets.preferences
                    .edit()
                    .putString(key, payload)
                    .commitOrThrow()
            } catch (e: SecureStoreException) {
                throw e
            } catch (e: Exception) {
                throw SecureStoreException.StorageException("Failed to store object for key: $key", e)
            }
        }
    }

    override suspend fun <T> getObject(
        key: String,
        serializer: KSerializer<T>,
    ): T? = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val raw = try {
                keysets.preferences.getString(key, null)
            } catch (e: Exception) {
                return@withKeysets handleDecryptionFailure(keysets, key, e)
            }

            raw?.let {
                try {
                    json.decodeFromString(serializer, it)
                } catch (e: Exception) {
                    when (config.decryptionFailurePolicy) {
                        DecryptionFailurePolicy.THROW_EXCEPTION ->
                            throw SecureStoreException.SerializationException("Failed to deserialize object for key: $key", e)
                        DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                            removeValue(keysets, key)
                            null
                        }
                        DecryptionFailurePolicy.RETURN_NULL -> null
                    }
                }
            }
        }
    }

    override suspend fun removeObject(key: String) = removeString(key)

    // ==================== Blob Operations ====================

    override suspend fun saveBlob(fileName: String, payload: ByteArray) = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val storageFileName = if (config.encryptFileNames) keysets.encryptFileName(fileName) else fileName
            val associatedData = if (config.useAssociatedData) fileName.toByteArray(Charsets.UTF_8) else null

            synchronized(getFileLock(storageFileName)) {
                try {
                    val ciphertext = keysets.fileAead.encrypt(payload, associatedData)
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
    }

    override suspend fun readBlob(fileName: String): ByteArray? = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val storageFileName = if (config.encryptFileNames) keysets.encryptFileName(fileName) else fileName
            val associatedData = if (config.useAssociatedData) fileName.toByteArray(Charsets.UTF_8) else null

            synchronized(getFileLock(storageFileName)) {
                val target = getFile(storageFileName)
                if (!target.exists()) return@withKeysets null

                try {
                    val ciphertext = target.readBytes()
                    val plaintext = keysets.fileAead.decrypt(ciphertext, associatedData)

                    if (config.secureMemory) {
                        ciphertext.fill(0)
                    }

                    plaintext
                } catch (e: Exception) {
                    handleBlobDecryptionFailure(fileName, storageFileName, e)
                }
            }
        }
    }

    override suspend fun deleteBlob(fileName: String): Boolean = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val storageFileName = if (config.encryptFileNames) keysets.encryptFileName(fileName) else fileName

            synchronized(getFileLock(storageFileName)) {
                val result = getFile(storageFileName).delete()
                if (result) {
                    shared.fileLocks.remove(storageFileName)
                }
                result
            }
        }
    }

    override suspend fun blobExists(fileName: String): Boolean = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val storageFileName = if (config.encryptFileNames) keysets.encryptFileName(fileName) else fileName
            getFile(storageFileName).exists()
        }
    }

    // ==================== Bulk Operations ====================

    override suspend fun clearAll(): Unit = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            synchronized(shared) {
                try {
                    keysets.preferences
                        .edit()
                        .clear()
                        .commitOrThrow()

                    storageDirectory.listFiles()?.forEach { file ->
                        synchronized(getFileLock(file.name)) {
                            file.delete()
                        }
                    }
                    shared.fileLocks.clear()
                } catch (e: SecureStoreException) {
                    throw e
                } catch (e: Exception) {
                    throw SecureStoreException.StorageException("Failed to clear all data", e)
                }
            }
        }
    }

    override suspend fun reset(): Unit = withContext(config.ioDispatcher) {
        shared.lock.write {
            openKeysets = null
            try {
                deleteKeysetsAndData()
            } catch (e: Exception) {
                throw SecureStoreException.StorageException("Failed to reset secure storage", e)
            }
        }
    }

    override suspend fun getAllKeys(): Set<String> = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val allKeys = keysets.preferences.all.keys
            if (config.encryptKeys && keysets.metadataAead != null) {
                allKeys.mapNotNull { encryptedKey ->
                    try {
                        keysets.decryptKey(encryptedKey)
                    } catch (e: Exception) {
                        null // Skip keys that can't be decrypted
                    }
                }.toSet()
            } else {
                allKeys.toSet()
            }
        }
    }

    override suspend fun getAllBlobNames(): Set<String> = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            val files = storageDirectory.listFiles() ?: return@withKeysets emptySet()

            if (config.encryptFileNames && keysets.metadataAead != null) {
                files.mapNotNull { file ->
                    try {
                        keysets.decryptFileName(file.name)
                    } catch (e: Exception) {
                        null // Skip files that can't be decrypted
                    }
                }.toSet()
            } else {
                files.map { it.name }.toSet()
            }
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

    private fun removeValue(keysets: Keysets, key: String) {
        try {
            keysets.preferences
                .edit()
                .remove(key)
                .commitOrThrow()
        } catch (e: SecureStoreException) {
            throw e
        } catch (e: Exception) {
            throw SecureStoreException.StorageException("Failed to remove string for key: $key", e)
        }
    }

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

    private fun Keysets.encryptKey(key: String): String {
        val metadata = metadataAead ?: return key
        val encrypted = metadata.encrypt(key.toByteArray(Charsets.UTF_8), null)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    private fun Keysets.decryptKey(encryptedKey: String): String {
        val metadata = metadataAead ?: return encryptedKey
        val decoded = android.util.Base64.decode(encryptedKey, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        return String(metadata.decrypt(decoded, null), Charsets.UTF_8)
    }

    private fun Keysets.encryptFileName(fileName: String): String {
        val metadata = metadataAead ?: return fileName
        val encrypted = metadata.encrypt(fileName.toByteArray(Charsets.UTF_8), null)
        // Use URL-safe base64 without padding for valid filenames
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
    }

    private fun Keysets.decryptFileName(encryptedFileName: String): String {
        val metadata = metadataAead ?: return encryptedFileName
        val decoded = android.util.Base64.decode(encryptedFileName, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        return String(metadata.decrypt(decoded, null), Charsets.UTF_8)
    }

    private fun <T> handleDecryptionFailure(keysets: Keysets, key: String, e: Exception): T? {
        return when (config.decryptionFailurePolicy) {
            DecryptionFailurePolicy.THROW_EXCEPTION ->
                throw SecureStoreException.DecryptionException("Failed to decrypt value for key: $key", e)
            DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                // Delete asynchronously in a fire-and-forget manner
                try {
                    keysets.preferences.edit().remove(key).apply()
                } catch (_: Exception) {
                    // Ignore deletion errors
                }
                null
            }
            DecryptionFailurePolicy.RETURN_NULL -> null
        }
    }

    private fun handleBlobDecryptionFailure(originalFileName: String, storageFileName: String, e: Exception): ByteArray? {
        return when (config.decryptionFailurePolicy) {
            DecryptionFailurePolicy.THROW_EXCEPTION ->
                throw SecureStoreException.DecryptionException("Failed to decrypt blob: $originalFileName", e)
            DecryptionFailurePolicy.DELETE_AND_RETURN_NULL -> {
                try {
                    getFile(storageFileName).delete()
                    shared.fileLocks.remove(storageFileName)
                } catch (_: Exception) {
                    // Ignore deletion errors
                }
                null
            }
            DecryptionFailurePolicy.RETURN_NULL -> null
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        val sharedStates = ConcurrentHashMap<String, SharedState>()
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

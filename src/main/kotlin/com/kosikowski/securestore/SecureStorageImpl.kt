package com.kosikowski.securestore

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.daead.DeterministicAeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
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
 * - Keys kept in secure hardware (TEE or StrongBox) when the device has it
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
 * @throws IllegalArgumentException if another store in this process uses the same storage mode and namespace
 *   with a different master key alias
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
            DeterministicAeadConfig.register()
        } catch (e: GeneralSecurityException) {
            throw SecureStoreException.InitializationException("Failed to initialize Tink AEAD", e)
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
    private class SharedState(
        val masterKeyAlias: String,
    ) {
        val lock = ReentrantReadWriteLock()

        @Volatile
        var generation = 0L

        /**
         * A fixed set of locks shared by file name hash. A lock is never removed while a thread may be
         * waiting on it, which would let the next caller create a second lock for the same file.
         */
        val fileLocks = Array(FILE_LOCK_STRIPES) { Any() }
    }

    private class Keysets(
        val generation: Long,
        val fileAead: Aead,
        val names: NameCipher?,
        val preferences: SharedPreferences,
    )

    private val shared: SharedState =
        sharedStates.getOrPut("${config.storageMode}/${config.namespace}") { SharedState(masterKeyAlias) }.also {
            require(it.masterKeyAlias == masterKeyAlias) {
                "Namespace '${config.namespace}' is already used in this process with master key alias '${it.masterKeyAlias}'. " +
                    "Stores that share a namespace must use the same master key alias: with different ones, each would " +
                    "treat the other's keysets as lost and delete them."
            }
        }

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
            notifyPendingReset()
            shared.lock.read {
                if (keysets.generation == shared.generation) return block(keysets)
            }
        }
    }

    /**
     * The file, preferences and (when enabled) name keysets, opened together: they are protected
     * by the same master key and are lost together. Separate keysets for defense in depth.
     */
    private fun currentKeysets(): Keysets {
        openKeysets?.takeIf { it.generation == shared.generation }?.let { return it }
        shared.lock.write {
            openKeysets?.takeIf { it.generation == shared.generation }?.let { return it }
            val keysets = openKeysetsRecoveringLoss()
            if (config.keyProtection == KeyProtection.HARDWARE_REQUIRED && !masterKeyIsHardwareBacked()) {
                throw SecureStoreException.HardwareRequiredException()
            }
            return keysets.also { openKeysets = it }
        }
    }

    private fun notifyPendingReset() {
        val cause = pendingResetCause.getAndSet(null) ?: return
        try {
            config.onKeysetReset(cause)
        } catch (e: Throwable) {
            pendingResetCause.compareAndSet(null, cause)
            throw e
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
            removeEntriesWithLegacyNames()
            val names = if (usesNameEncryption) NameCipher(nameKeyset().getPrimitive(DeterministicAead::class.java)) else null
            Keysets(
                generation = shared.generation,
                fileAead = keysetAead(tinkKeysetPref, tinkKeysetName),
                names = names,
                preferences =
                    TinkEncryptedSharedPreferences(
                        delegate = storageContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE),
                        aead = keysetAead(tinkPrefsKeysetPref, tinkPrefsKeysetName),
                        names = if (config.encryptKeys) names else null,
                        useAssociatedData = config.useAssociatedData,
                        secureMemory = config.secureMemory,
                    ),
            )
        } catch (e: Exception) {
            throw SecureStoreException.InitializationException("Failed to open keysets", e)
        }

    private fun keysetAead(keysetName: String, prefFileName: String): Aead =
        keysetManager(keysetName, prefFileName).keysetHandle.getPrimitive(Aead::class.java)

    private fun keysetManager(keysetName: String, prefFileName: String): AndroidKeysetManager =
        AndroidKeysetManager.Builder()
            .withSharedPref(keysetContext, keysetName, prefFileName)
            .withKeyTemplate(config.encryption.keyTemplate)
            .withMasterKeyUri(masterKeyUri)
            .build()

    private fun addPrimaryKey(keysetName: String, prefFileName: String) {
        val manager = keysetManager(keysetName, prefFileName).add(config.encryption.keyTemplate)
        manager.setPrimary(manager.keysetHandle.keysetInfo.keyInfoList.last().keyId)
    }

    private fun nameKeyset(): KeysetHandle =
        AndroidKeysetManager.Builder()
            .withSharedPref(keysetContext, tinkNamesKeysetPref, tinkNamesKeysetName)
            .withKeyTemplate(DeterministicAeadKeyTemplates.AES256_SIV)
            .withMasterKeyUri(masterKeyUri)
            .build()
            .keysetHandle

    /**
     * Up to 1.0.0 key and file names were encrypted with a randomized AEAD, so a name never matched its
     * stored name again: those entries could not be read, and removing them had no effect. Recovering
     * them could bring back values the app had removed, so they are deleted together with the keyset
     * that encrypted their names.
     */
    private fun removeEntriesWithLegacyNames() {
        if (!keysetContext.sharedPreferencesFile(tinkMetadataKeysetName).exists()) return

        val legacyNames = keysetAead(tinkMetadataKeysetPref, tinkMetadataKeysetName)
        val isLegacyName = { name: String ->
            runCatching { legacyNames.decrypt(android.util.Base64.decode(name, LEGACY_NAME_BASE64_FLAGS), null) }.isSuccess
        }
        val values = storageContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        val legacyKeys = values.all.keys.filter(isLegacyName)
        if (legacyKeys.isNotEmpty()) {
            values.edit().apply { legacyKeys.forEach(::remove) }.commitOrThrow()
        }
        storageDirectory.listFilesOrThrow().filter { isLegacyName(it.name) }.forEach { file ->
            if (!file.delete()) throw IOException("Failed to delete $file")
        }
        deleteSharedPreferencesOrThrow(keysetContext, tinkMetadataKeysetName)
    }

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

        val missing = listOf(tinkKeysetName, tinkPrefsKeysetName).filterNot { storageContext.sharedPreferencesFile(it).exists() }
        if (missing.isEmpty() || !hasStoredData()) return

        if (!isUserUnlocked()) {
            throw SecureStoreException.InitializationException(
                "Secure storage is unavailable until the device is unlocked for the first time after upgrading",
            )
        }
        try {
            (missing + tinkMetadataKeysetName).forEach(::copyLegacyKeyset)
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

    /**
     * False only when the master key is confirmed gone. Keystore lookups report a key as absent when the
     * Keystore fails: up to Android 11 both getKey and containsAlias do so when the Keystore service
     * cannot be reached. Absence is trusted only once the Keystore has created and found a probe key.
     */
    private fun masterKeyExists(): Boolean =
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(masterKeyAlias) || !keystoreResponds(keyStore)
        } catch (e: Exception) {
            true
        }

    private fun keystoreResponds(keyStore: KeyStore): Boolean {
        val probeAlias = "${masterKeyAlias}_probe"
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(probeAlias, KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
            keyStore.containsAlias(probeAlias)
        } catch (e: Exception) {
            false
        } finally {
            runCatching { keyStore.deleteEntry(probeAlias) }
        }
    }

    /**
     * Data goes first: if a deletion fails, the keysets are still there and still unreadable, so the
     * next open retries the recovery instead of creating keysets next to data they cannot decrypt.
     */
    private fun deleteKeysetsAndData() {
        try {
            deleteSharedPreferencesOrThrow(storageContext, sharedPrefsName)
            storageDirectory.listFilesOrThrow().forEach { file ->
                if (!file.delete()) throw IOException("Failed to delete $file")
            }
            deletePartialBlobFiles()

            listOf(tinkKeysetName, tinkPrefsKeysetName, tinkNamesKeysetName, tinkMetadataKeysetName)
                .forEach { deleteSharedPreferencesOrThrow(keysetContext, it) }
        } finally {
            shared.generation++
        }
    }

    private fun deleteSharedPreferencesOrThrow(context: Context, name: String) {
        if (!context.deleteSharedPreferences(name)) throw IOException("Failed to delete shared preferences $name")
    }

    private fun isUserUnlocked(): Boolean = appContext.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    // A blob directory that cannot be listed may hold data, so it counts as holding some.
    private fun hasStoredData(): Boolean =
        storageContext.sharedPreferencesFile(sharedPrefsName).exists() || storageDirectory.list()?.isNotEmpty() ?: true

    /** listFiles() also returns null when the directory cannot be read, which must not pass for "no files". */
    private fun File.listFilesOrThrow(): Array<File> = listFiles() ?: throw IOException("Failed to list $this")

    private val storageDirectory: File by lazy {
        File(storageContext.filesDir, secureFileDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /** Namespaces cannot contain '.', so this cannot be another namespace's blob directory. */
    private val partialBlobDirectory: File by lazy {
        File(storageContext.filesDir, "$secureFileDir.partial").apply { mkdirs() }
    }

    private fun getFileLock(fileName: String): Any = shared.fileLocks[Math.floorMod(fileName.hashCode(), FILE_LOCK_STRIPES)]

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

    private val tinkNamesKeysetPref: String
        get() = "secure_storage_names_keyset_pref_${config.namespace}"

    private val tinkNamesKeysetName: String
        get() = "secure_storage_names_key_${config.namespace}"

    private val usesNameEncryption: Boolean
        get() = config.encryptKeys || config.encryptFileNames

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
            keysets.preferences.contains(key)
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
            val storageFileName = keysets.storedFileName(fileName)
            val associatedData = if (config.useAssociatedData) fileName.toByteArray(Charsets.UTF_8) else null

            synchronized(getFileLock(storageFileName)) {
                try {
                    val ciphertext = keysets.fileAead.encrypt(payload, associatedData)
                    writeBlobFile(storageFileName, ciphertext)

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
            val storageFileName = keysets.storedFileName(fileName)
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
            val storageFileName = keysets.storedFileName(fileName)

            synchronized(getFileLock(storageFileName)) {
                getFile(storageFileName).delete()
            }
        }
    }

    override suspend fun blobExists(fileName: String): Boolean = withContext(config.ioDispatcher) {
        withKeysets { keysets ->
            getFile(keysets.storedFileName(fileName)).exists()
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
                    deletePartialBlobFiles()
                } catch (e: SecureStoreException) {
                    throw e
                } catch (e: Exception) {
                    throw SecureStoreException.StorageException("Failed to clear all data", e)
                }
            }
        }
    }

    override suspend fun rotateKeys(): Unit = withContext(config.ioDispatcher) {
        currentKeysets()
        notifyPendingReset()
        shared.lock.write {
            try {
                addPrimaryKey(tinkKeysetPref, tinkKeysetName)
                addPrimaryKey(tinkPrefsKeysetPref, tinkPrefsKeysetName)
            } catch (e: GeneralSecurityException) {
                throw SecureStoreException.KeystoreException("Failed to rotate keys", e)
            } finally {
                shared.generation++
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
            val names = keysets.names
            if (config.encryptKeys && names != null) {
                allKeys.mapNotNull { encryptedKey ->
                    try {
                        names.decryptKey(encryptedKey)
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

            val names = keysets.names
            if (config.encryptFileNames && names != null) {
                files.mapNotNull { file ->
                    try {
                        names.decryptFileName(file.name)
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
        isHardwareBacked = runCatching { masterKeyIsHardwareBacked() }.getOrDefault(false),
        namespace = config.namespace,
        keyEncryptionEnabled = config.encryptKeys,
        fileNameEncryptionEnabled = config.encryptFileNames,
    )

    // ==================== Private Helpers ====================

    private fun getFile(fileName: String): File = File(storageDirectory, fileName)

    /**
     * A crash while overwriting a blob in place would leave a truncated file that no longer decrypts.
     * The new content is written and synced to a separate file first, then renamed over the blob, so
     * the blob holds either its old or its new content.
     */
    private fun writeBlobFile(storageFileName: String, content: ByteArray) {
        val partial = File(partialBlobDirectory, storageFileName)
        FileOutputStream(partial).use { output ->
            output.write(content)
            output.fd.sync()
        }
        if (!partial.renameTo(getFile(storageFileName))) {
            partial.delete()
            throw IOException("Failed to replace blob file $storageFileName")
        }
    }

    /** A partial file shares its blob's name, so its lock keeps this from deleting a write in progress. */
    private fun deletePartialBlobFiles() {
        partialBlobDirectory.listFiles()?.forEach { file ->
            synchronized(getFileLock(file.name)) { file.delete() }
        }
    }

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

    /**
     * Whether the master key is kept in secure hardware (TEE or StrongBox). Android Keystore decides
     * where a key lives when Tink creates it, so this can only be checked once the key exists.
     */
    private fun masterKeyIsHardwareBacked(): Boolean {
        val key = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.getKey(masterKeyAlias, null) as? SecretKey ?: return false
        val keyInfo = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX ||
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE
        } else {
            @Suppress("DEPRECATION")
            keyInfo.isInsideSecureHardware
        }
    }

    private fun Keysets.storedFileName(fileName: String): String =
        names?.takeIf { config.encryptFileNames }?.encryptFileName(fileName) ?: fileName

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
        const val FILE_LOCK_STRIPES = 32
        const val LEGACY_NAME_BASE64_FLAGS = android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE

        val sharedStates = ConcurrentHashMap<String, SharedState>()
    }

    /**
     * Wrapper for SharedPreferences that encrypts values using Tink AEAD.
     */
    private class TinkEncryptedSharedPreferences(
        private val delegate: SharedPreferences,
        private val aead: Aead,
        private val names: NameCipher?,
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
            names = names,
            useAssociatedData = useAssociatedData,
            secureMemory = secureMemory,
        )

        private fun encryptKeyIfNeeded(key: String): String = names?.encryptKey(key) ?: key
    }

    /**
     * Editor that encrypts values before storing them.
     */
    private class TinkEditor(
        private val delegate: SharedPreferences.Editor,
        private val aead: Aead,
        private val names: NameCipher?,
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

        private fun encryptKeyIfNeeded(key: String): String = names?.encryptKey(key) ?: key
    }
}

package com.kosikowski.securestore

import kotlinx.serialization.KSerializer

/**
 * Interface for storing sensitive data using Android Keystore-backed encryption.
 *
 * Implementations should encrypt everything with hardware-backed keys and expose simple read/write APIs.
 *
 * ## Thread Safety
 * All operations are thread-safe and can be called from multiple coroutines concurrently.
 *
 * ## Error Handling
 * Operations may throw [SecureStoreException] subclasses when configured to do so.
 * By default, read operations return null on failure for graceful degradation.
 *
 * @see SecureStorageImpl
 * @see SecureStoreConfig
 */
interface SecureStorage {

    // ==================== STRING OPERATIONS ====================

    /**
     * Saves a string value under [key].
     *
     * @param key The unique identifier for the value
     * @param value The string to encrypt and store
     * @throws SecureStoreException.EncryptionException if encryption fails
     * @throws SecureStoreException.StorageException if storage fails
     */
    suspend fun putString(key: String, value: String)

    /**
     * Reads a string stored under [key], or null if it does not exist.
     *
     * @param key The unique identifier for the value
     * @return The decrypted string, or null if not found or decryption fails
     * @throws SecureStoreException.DecryptionException if configured to throw on failure
     */
    suspend fun getString(key: String): String?

    /**
     * Removes a value stored under [key].
     *
     * @param key The unique identifier for the value to remove
     * @throws SecureStoreException.StorageException if removal fails
     */
    suspend fun removeString(key: String)

    /**
     * Checks if a key exists in SharedPreferences storage.
     *
     * @param key The key to check
     * @return true if the key exists, false otherwise
     */
    suspend fun contains(key: String): Boolean

    // ==================== OBJECT OPERATIONS ====================

    /**
     * Saves a serializable object under [key].
     *
     * The object is converted to JSON and stored as an encrypted string,
     * which keeps the API storage-agnostic.
     *
     * @param key The unique identifier for the object
     * @param value The object to serialize and store
     * @param serializer The kotlinx.serialization serializer for the object
     * @throws SecureStoreException.SerializationException if serialization fails
     * @throws SecureStoreException.EncryptionException if encryption fails
     * @throws SecureStoreException.StorageException if storage fails
     */
    suspend fun <T> putObject(key: String, value: T, serializer: KSerializer<T>)

    /**
     * Reads an object stored under [key].
     *
     * Returns null when the entry is missing or fails to deserialize.
     *
     * @param key The unique identifier for the object
     * @param serializer The kotlinx.serialization serializer for the object
     * @return The deserialized object, or null if not found or deserialization fails
     * @throws SecureStoreException.DecryptionException if configured to throw on failure
     * @throws SecureStoreException.SerializationException if configured to throw on failure
     */
    suspend fun <T> getObject(key: String, serializer: KSerializer<T>): T?

    /**
     * Removes an object stored under [key].
     *
     * @param key The unique identifier for the object to remove
     * @throws SecureStoreException.StorageException if removal fails
     */
    suspend fun removeObject(key: String)

    // ==================== BLOB OPERATIONS ====================

    /**
     * Stores an arbitrary byte array inside an encrypted file with the given [fileName].
     *
     * @param fileName The name of the encrypted file
     * @param payload The byte array to encrypt and store
     * @throws SecureStoreException.EncryptionException if encryption fails
     * @throws SecureStoreException.StorageException if file write fails
     */
    suspend fun saveBlob(fileName: String, payload: ByteArray)

    /**
     * Reads the blob stored under [fileName].
     *
     * Returns null when the file does not exist yet.
     *
     * @param fileName The name of the encrypted file
     * @return The decrypted byte array, or null if not found or decryption fails
     * @throws SecureStoreException.DecryptionException if configured to throw on failure
     */
    suspend fun readBlob(fileName: String): ByteArray?

    /**
     * Deletes the blob stored under [fileName].
     *
     * @param fileName The name of the file to delete
     * @return true if the file was removed, false if it didn't exist
     * @throws SecureStoreException.StorageException if deletion fails for other reasons
     */
    suspend fun deleteBlob(fileName: String): Boolean

    /**
     * Checks if a blob file exists.
     *
     * @param fileName The name of the file to check
     * @return true if the file exists, false otherwise
     */
    suspend fun blobExists(fileName: String): Boolean

    // ==================== BULK OPERATIONS ====================

    /**
     * Removes all shared preferences entries and encrypted files managed by this storage.
     *
     * @throws SecureStoreException.StorageException if clearing fails
     */
    suspend fun clearAll()

    /**
     * Lists all stored keys in SharedPreferences.
     *
     * Note: If key encryption is enabled, returned keys are decrypted.
     *
     * @return Set of all stored keys
     */
    suspend fun getAllKeys(): Set<String>

    /**
     * Lists all stored blob file names.
     *
     * Note: If filename encryption is enabled, returned names are decrypted.
     *
     * @return Set of all blob file names
     */
    suspend fun getAllBlobNames(): Set<String>

    // ==================== METADATA ====================

    /**
     * Returns metadata about the secure store.
     *
     * This is a non-suspending function as it returns cached configuration data.
     *
     * @return Information about the store configuration and state
     */
    fun getStoreInfo(): SecureStoreInfo
}

/**
 * Metadata about a SecureStore instance.
 *
 * @property encryptionAlgorithm The encryption algorithm in use
 * @property isHardwareBacked Whether keys are backed by hardware (TEE/StrongBox)
 * @property namespace The namespace this store operates in
 * @property keyEncryptionEnabled Whether SharedPreferences keys are encrypted
 * @property fileNameEncryptionEnabled Whether blob file names are encrypted
 */
data class SecureStoreInfo(
    val encryptionAlgorithm: String,
    val isHardwareBacked: Boolean,
    val namespace: String,
    val keyEncryptionEnabled: Boolean,
    val fileNameEncryptionEnabled: Boolean,
)

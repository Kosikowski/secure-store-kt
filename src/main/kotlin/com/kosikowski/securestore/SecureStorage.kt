package com.kosikowski.securestore

import kotlinx.serialization.KSerializer

/**
 * Interface for storing sensitive data using Android Keystore-backed encryption.
 *
 * Implementations should encrypt everything with hardware-backed keys and expose simple read/write APIs.
 */
interface SecureStorage {
    /**
     * Saves a string value under [key].
     */
    suspend fun putString(
        key: String,
        value: String,
    )

    /**
     * Reads a string stored under [key], or null if it does not exist.
     */
    suspend fun getString(key: String): String?

    /**
     * Removes a value stored under [key].
     */
    suspend fun removeString(key: String)

    /**
     * Saves a serializable object under [key].
     *
     * The object is converted to JSON and stored as a string, which keeps the API storage-agnostic.
     */
    suspend fun <T> putObject(
        key: String,
        value: T,
        serializer: KSerializer<T>,
    )

    /**
     * Reads an object stored under [key].
     *
     * Returns null when the entry is missing or fails to deserialize.
     */
    suspend fun <T> getObject(
        key: String,
        serializer: KSerializer<T>,
    ): T?

    /**
     * Stores an arbitrary byte array inside an encrypted file with the given [fileName].
     */
    suspend fun saveBlob(
        fileName: String,
        payload: ByteArray,
    )

    /**
     * Reads the blob stored under [fileName].
     *
     * Returns null when the file does not exist yet.
     */
    suspend fun readBlob(fileName: String): ByteArray?

    /**
     * Deletes the blob stored under [fileName].
     *
     * @return true if the file was removed, false otherwise.
     */
    suspend fun deleteBlob(fileName: String): Boolean

    /**
     * Removes all shared preferences entries and encrypted files managed by this storage.
     */
    suspend fun clearAll()
}

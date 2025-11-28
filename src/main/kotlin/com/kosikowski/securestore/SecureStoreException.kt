package com.kosikowski.securestore

/**
 * Base exception for all SecureStore errors.
 *
 * @property message Human-readable error message
 * @property cause The underlying cause of this exception
 */
sealed class SecureStoreException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Thrown when SecureStore fails to initialize.
     * This typically indicates issues with Tink configuration or Android Keystore.
     */
    class InitializationException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)

    /**
     * Thrown when encryption fails.
     * This may indicate key issues or memory problems.
     */
    class EncryptionException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)

    /**
     * Thrown when decryption fails.
     * This may indicate corrupted data, wrong key, or tampering.
     */
    class DecryptionException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)

    /**
     * Thrown when Android Keystore operations fail.
     * This may indicate hardware issues or keystore corruption.
     */
    class KeystoreException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)

    /**
     * Thrown when storage operations fail.
     * This may indicate disk full, permission issues, or I/O errors.
     */
    class StorageException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)

    /**
     * Thrown when hardware-backed key protection is required but unavailable.
     */
    class HardwareRequiredException(
        message: String = "Hardware-backed key protection is required but not available on this device",
    ) : SecureStoreException(message)

    /**
     * Thrown when serialization or deserialization fails.
     */
    class SerializationException(
        message: String,
        cause: Throwable? = null,
    ) : SecureStoreException(message, cause)
}


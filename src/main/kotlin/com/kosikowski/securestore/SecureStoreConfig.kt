package com.kosikowski.securestore

import com.google.crypto.tink.proto.KeyTemplate
import com.google.crypto.tink.aead.AeadKeyTemplates
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Encryption algorithm options for SecureStore.
 */
enum class EncryptionAlgorithm(internal val keyTemplate: KeyTemplate) {
    /**
     * AES-256-GCM - Recommended for most use cases.
     * Provides authenticated encryption with 256-bit key.
     */
    AES_256_GCM(AeadKeyTemplates.AES256_GCM),

    /**
     * AES-128-GCM - Faster, slightly smaller ciphertext.
     * Still secure for most applications.
     */
    AES_128_GCM(AeadKeyTemplates.AES128_GCM),

    /**
     * ChaCha20-Poly1305 - Alternative to AES.
     * Better performance on devices without AES hardware acceleration.
     */
    CHACHA20_POLY1305(AeadKeyTemplates.CHACHA20_POLY1305),

    /**
     * AES-256-EAX - Alternative authenticated encryption mode.
     */
    AES_256_EAX(AeadKeyTemplates.AES256_EAX),
}

/**
 * Key protection requirements.
 */
enum class KeyProtection {
    /**
     * Use software-backed keys (default).
     * Works on all devices.
     */
    SOFTWARE,

    /**
     * Prefer hardware-backed keys (TEE) when available.
     * Falls back to software if hardware unavailable.
     */
    HARDWARE_PREFERRED,

    /**
     * Require hardware-backed keys (StrongBox or TEE).
     * Throws exception if hardware backing is unavailable.
     */
    HARDWARE_REQUIRED,
}

/**
 * Storage mode for the secure store.
 */
enum class StorageMode {
    /**
     * Credential-encrypted storage.
     * Data accessible only after user unlocks device.
     */
    CREDENTIAL_PROTECTED,

    /**
     * Device-encrypted storage (API 24+).
     * Data accessible before user unlock - for unattended apps.
     */
    DEVICE_PROTECTED,
}

/**
 * Behavior when decryption fails (e.g., corrupted data, key issues).
 */
enum class DecryptionFailurePolicy {
    /**
     * Return null on decryption failure.
     */
    RETURN_NULL,

    /**
     * Throw an exception on decryption failure.
     */
    THROW_EXCEPTION,

    /**
     * Delete the corrupted entry and return null.
     */
    DELETE_AND_RETURN_NULL,
}

/**
 * Behavior when the keysets can no longer be opened because the Keystore master key was deleted, or a
 * keyset was corrupted. Android deletes an app's Keystore keys when its data is cleared, and, for apps
 * sharing a user ID, when the data of any of them is cleared. The stored data cannot be decrypted
 * again in either case.
 */
enum class KeysetLossPolicy {
    /**
     * Delete the keysets and all stored data, create new keysets and continue with an empty store.
     * [SecureStoreConfig.onKeysetReset] is called once with the cause.
     */
    RESET,

    /**
     * Throw [SecureStoreException.KeysetLostException] from every operation except [SecureStorage.getStoreInfo] until
     * [SecureStorage.reset] is called.
     */
    THROW,
}

/**
 * Configuration for SecureStore.
 *
 * Example usage:
 * ```kotlin
 * val config = SecureStoreConfig.Builder()
 *     .encryption(EncryptionAlgorithm.AES_256_GCM)
 *     .keyProtection(KeyProtection.HARDWARE_PREFERRED)
 *     .storageMode(StorageMode.DEVICE_PROTECTED)
 *     .encryptKeys(true)
 *     .useAssociatedData(true)
 *     .build()
 *
 * val secureStorage = SecureStorageImpl(context, config)
 * ```
 *
 * @property encryption Encryption algorithm to use
 * @property keyProtection Key protection level requirement
 * @property storageMode Storage mode (credential vs device protected)
 * @property encryptKeys Whether to encrypt SharedPreferences keys (not just values)
 * @property encryptFileNames Whether to encrypt blob file names
 * @property useAssociatedData Whether to use key/filename as associated data for AEAD
 * @property decryptionFailurePolicy Policy when decryption fails
 * @property namespace Custom namespace for key storage (allows multiple independent stores)
 * @property masterKeyAlias Master key alias in Android Keystore
 * @property ioDispatcher Coroutine dispatcher for IO operations
 * @property secureMemory Whether to wipe sensitive data from memory after use
 * @property enableKeyRotation Enable key rotation support
 * @property keysetLossPolicy Policy when the keysets can no longer be opened
 * @property onKeysetReset Called when [KeysetLossPolicy.RESET] discarded the stored data
 */
class SecureStoreConfig private constructor(
    val encryption: EncryptionAlgorithm,
    val keyProtection: KeyProtection,
    val storageMode: StorageMode,
    val encryptKeys: Boolean,
    val encryptFileNames: Boolean,
    val useAssociatedData: Boolean,
    val decryptionFailurePolicy: DecryptionFailurePolicy,
    val namespace: String,
    val masterKeyAlias: String,
    val ioDispatcher: CoroutineDispatcher,
    val secureMemory: Boolean,
    val enableKeyRotation: Boolean,
    val keysetLossPolicy: KeysetLossPolicy,
    val onKeysetReset: (cause: Throwable) -> Unit,
) {

    /**
     * Builder for [SecureStoreConfig].
     */
    class Builder {
        private var encryption: EncryptionAlgorithm = EncryptionAlgorithm.AES_256_GCM
        private var keyProtection: KeyProtection = KeyProtection.SOFTWARE
        private var storageMode: StorageMode = StorageMode.DEVICE_PROTECTED
        private var encryptKeys: Boolean = false
        private var encryptFileNames: Boolean = false
        private var useAssociatedData: Boolean = true
        private var decryptionFailurePolicy: DecryptionFailurePolicy = DecryptionFailurePolicy.RETURN_NULL
        private var namespace: String = "default"
        private var masterKeyAlias: String = "secure_store_master_key"
        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var secureMemory: Boolean = false
        private var enableKeyRotation: Boolean = false
        private var keysetLossPolicy: KeysetLossPolicy = KeysetLossPolicy.RESET
        private var onKeysetReset: (cause: Throwable) -> Unit = {}

        /**
         * Set the encryption algorithm.
         * Default: AES_256_GCM
         */
        fun encryption(algorithm: EncryptionAlgorithm) = apply { this.encryption = algorithm }

        /**
         * Set the key protection level.
         * Default: SOFTWARE
         */
        fun keyProtection(protection: KeyProtection) = apply { this.keyProtection = protection }

        /**
         * Set the storage mode.
         * Default: DEVICE_PROTECTED
         */
        fun storageMode(mode: StorageMode) = apply { this.storageMode = mode }

        /**
         * Whether to encrypt SharedPreferences keys.
         * Default: false
         */
        fun encryptKeys(encrypt: Boolean) = apply { this.encryptKeys = encrypt }

        /**
         * Whether to encrypt blob file names.
         * Default: false
         */
        fun encryptFileNames(encrypt: Boolean) = apply { this.encryptFileNames = encrypt }

        /**
         * Whether to use key/filename as associated data.
         * Prevents ciphertext relocation attacks.
         * Default: true
         */
        fun useAssociatedData(use: Boolean) = apply { this.useAssociatedData = use }

        /**
         * Set policy for decryption failures.
         * Default: RETURN_NULL
         */
        fun decryptionFailurePolicy(policy: DecryptionFailurePolicy) = apply {
            this.decryptionFailurePolicy = policy
        }

        /**
         * Set namespace for isolated storage instances.
         * Allows multiple independent SecureStore instances.
         * Default: "default"
         */
        fun namespace(ns: String) = apply {
            require(ns.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                "Namespace must contain only alphanumeric characters, underscores, and hyphens"
            }
            this.namespace = ns
        }

        /**
         * Set custom master key alias.
         * Default: "secure_store_master_key"
         */
        fun masterKeyAlias(alias: String) = apply {
            require(alias.isNotBlank()) { "Master key alias cannot be blank" }
            this.masterKeyAlias = alias
        }

        /**
         * Set coroutine dispatcher for IO operations.
         * Default: Dispatchers.IO
         */
        fun ioDispatcher(dispatcher: CoroutineDispatcher) = apply { this.ioDispatcher = dispatcher }

        /**
         * Enable secure memory wiping.
         * When enabled, byte arrays containing sensitive data are zeroed after use.
         * May have slight performance impact.
         * Default: false
         */
        fun secureMemory(enabled: Boolean) = apply { this.secureMemory = enabled }

        /**
         * Enable key rotation support.
         * When enabled, old keys are kept for decryption while new data uses the latest key.
         * Default: false
         */
        fun enableKeyRotation(enabled: Boolean) = apply { this.enableKeyRotation = enabled }

        /**
         * Set policy for keysets that can no longer be opened.
         * Default: RESET
         */
        fun keysetLossPolicy(policy: KeysetLossPolicy) = apply { this.keysetLossPolicy = policy }

        /**
         * Called once, with the cause, after [KeysetLossPolicy.RESET] discarded the stored data. Use it
         * to report the reset and to restore what the app needs, such as signing in again. It runs on
         * the thread of the operation that opened the store, and an exception it throws fails that
         * operation.
         * Default: no-op
         */
        fun onKeysetReset(listener: (cause: Throwable) -> Unit) = apply { this.onKeysetReset = listener }

        /**
         * Build the configuration.
         */
        fun build(): SecureStoreConfig = SecureStoreConfig(
            encryption = encryption,
            keyProtection = keyProtection,
            storageMode = storageMode,
            encryptKeys = encryptKeys,
            encryptFileNames = encryptFileNames,
            useAssociatedData = useAssociatedData,
            decryptionFailurePolicy = decryptionFailurePolicy,
            namespace = namespace,
            masterKeyAlias = masterKeyAlias,
            ioDispatcher = ioDispatcher,
            secureMemory = secureMemory,
            enableKeyRotation = enableKeyRotation,
            keysetLossPolicy = keysetLossPolicy,
            onKeysetReset = onKeysetReset,
        )
    }

    /**
     * Create a new builder pre-populated with this config's values.
     */
    fun toBuilder(): Builder = Builder()
        .encryption(encryption)
        .keyProtection(keyProtection)
        .storageMode(storageMode)
        .encryptKeys(encryptKeys)
        .encryptFileNames(encryptFileNames)
        .useAssociatedData(useAssociatedData)
        .decryptionFailurePolicy(decryptionFailurePolicy)
        .namespace(namespace)
        .masterKeyAlias(masterKeyAlias)
        .ioDispatcher(ioDispatcher)
        .secureMemory(secureMemory)
        .enableKeyRotation(enableKeyRotation)
        .keysetLossPolicy(keysetLossPolicy)
        .onKeysetReset(onKeysetReset)

    companion object {
        /**
         * Default configuration.
         * - AES-256-GCM encryption
         * - Software key protection
         * - Device-protected storage
         * - Associated data enabled
         */
        val DEFAULT: SecureStoreConfig = Builder().build()

        /**
         * High-security configuration with hardware-backed keys and encrypted metadata.
         * - AES-256-GCM encryption
         * - Hardware-required key protection
         * - Encrypted keys and file names
         * - Secure memory wiping
         * - Corrupted entries are deleted
         */
        val HIGH_SECURITY: SecureStoreConfig = Builder()
            .encryption(EncryptionAlgorithm.AES_256_GCM)
            .keyProtection(KeyProtection.HARDWARE_REQUIRED)
            .encryptKeys(true)
            .encryptFileNames(true)
            .useAssociatedData(true)
            .secureMemory(true)
            .decryptionFailurePolicy(DecryptionFailurePolicy.DELETE_AND_RETURN_NULL)
            .build()

        /**
         * Performance-optimized configuration.
         * - ChaCha20-Poly1305 (faster on devices without AES-NI)
         * - Software key protection
         * - No metadata encryption
         * - No secure memory wiping
         */
        val PERFORMANCE: SecureStoreConfig = Builder()
            .encryption(EncryptionAlgorithm.CHACHA20_POLY1305)
            .keyProtection(KeyProtection.SOFTWARE)
            .encryptKeys(false)
            .encryptFileNames(false)
            .useAssociatedData(false)
            .secureMemory(false)
            .build()
    }
}


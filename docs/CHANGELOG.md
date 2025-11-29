# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [1.0.0] - 2025-11-28

### Added

- **Configuration System**: New `SecureStoreConfig` builder for comprehensive customization
  - Configurable encryption algorithms: AES-256-GCM, AES-128-GCM, ChaCha20-Poly1305, AES-256-EAX
  - Key protection levels: SOFTWARE, HARDWARE_PREFERRED, HARDWARE_REQUIRED
  - Storage modes: CREDENTIAL_PROTECTED, DEVICE_PROTECTED
  - Optional key and filename encryption for enhanced metadata protection
  - Associated data support to prevent ciphertext relocation attacks
  - Configurable decryption failure policies
  - Namespace support for isolated storage instances
  - Secure memory wiping option

- **Preset Configurations**:
  - `SecureStoreConfig.DEFAULT` - Standard configuration
  - `SecureStoreConfig.HIGH_SECURITY` - Maximum security with hardware keys and encrypted metadata
  - `SecureStoreConfig.PERFORMANCE` - Optimized for speed with ChaCha20-Poly1305

- **Custom Exception Hierarchy**: New `SecureStoreException` sealed class with specific exception types:
  - `InitializationException` - Tink or Keystore initialization failures
  - `EncryptionException` - Encryption operation failures
  - `DecryptionException` - Decryption operation failures
  - `KeystoreException` - Android Keystore operation failures
  - `StorageException` - File or SharedPreferences I/O failures
  - `HardwareRequiredException` - Hardware-backed keys required but unavailable
  - `SerializationException` - Object serialization/deserialization failures

- **New API Methods**:
  - `contains(key: String): Boolean` - Check if a key exists
  - `removeObject(key: String)` - Remove a stored object
  - `blobExists(fileName: String): Boolean` - Check if a blob file exists
  - `getAllKeys(): Set<String>` - List all stored keys
  - `getAllBlobNames(): Set<String>` - List all stored blob filenames
  - `getStoreInfo(): SecureStoreInfo` - Get store metadata and configuration info

- **SecureStoreInfo Data Class**: Returns information about the store configuration:
  - `encryptionAlgorithm` - Current encryption algorithm
  - `isHardwareBacked` - Whether keys are hardware-backed
  - `namespace` - Current namespace
  - `keyEncryptionEnabled` - Whether key encryption is enabled
  - `fileNameEncryptionEnabled` - Whether filename encryption is enabled

### Changed

- Improved error handling with proper exception throwing instead of silent failures
- Associated data now used by default for AEAD operations (prevents ciphertext relocation)
- Better thread safety documentation and implementation

### Fixed

- Silent encryption failures in `TinkEditor.putString()` now throw proper exceptions
- Decryption failures now follow configurable policy instead of always returning null

### Security

- Added optional key encryption for SharedPreferences (hides what data is stored)
- Added optional filename encryption for blobs (hides file metadata)
- Added associated data binding to prevent ciphertext relocation attacks
- Added secure memory wiping option to clear sensitive data after use
- Added hardware key requirement option for high-security applications

## [0.1.0] - 2025-11-25

### Added

- Initial release of SecureStore library
- `SecureStorage` interface for secure data storage
- `SecureStorageImpl` implementation using Google Tink encryption
- Android Keystore integration for hardware-backed encryption
- Support for storing strings, serializable objects, and binary blobs
- Thread-safe operations with concurrent access support
- Comprehensive test suite with 200+ test cases
- Full KDoc documentation
- Maven Central publishing support

### Security

- AES-256-GCM encryption for all stored data
- Hardware-backed key storage via Android Keystore
- Separate encryption keys for preferences and files (defense in depth)
- Authenticated encryption to prevent tampering
- Device-protected storage support (API 24+)

### Dependencies

- Google Tink 1.12.0
- Kotlin 1.9.22
- kotlinx.coroutines 1.7.3
- kotlinx.serialization 1.6.2
- AndroidX Core KTX 1.12.0

[Unreleased]: https://github.com/Kosikowski/secure-store-kt/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Kosikowski/secure-store-kt/compare/v0.1.0...v1.0.0
[0.1.0]: https://github.com/Kosikowski/secure-store-kt/releases/tag/v0.1.0

# Documentation Guide

This document explains the documentation structure and how to generate API documentation.

## Documentation Structure

```
SecureStore/
├── README.md                    # Main project overview
├── LICENSE                      # Apache 2.0 License
└── docs/
    ├── MODULE.md               # Dokka module documentation
    ├── CHANGELOG.md            # Version history
    ├── QUICKSTART.md           # Getting started guide
    ├── EXAMPLES.md             # Code examples
    ├── CONTRIBUTING.md         # Contribution guidelines
    ├── SECURITY.md             # Security information
    ├── TESTING.md              # Testing guide
    ├── PUBLISHING.md           # Publishing to Maven Central
    ├── DOCUMENTATION.md        # This file
    └── api/                    # Generated API docs (gitignored)
        ├── html/               # HTML documentation
        └── javadoc/            # Javadoc format
```

## Main Classes and Files

### Core API

| File | Description |
|------|-------------|
| `SecureStorage.kt` | Main interface defining all storage operations |
| `SecureStorageImpl.kt` | Full implementation with Tink encryption |
| `SecureStoreConfig.kt` | Configuration builder and presets |
| `SecureStoreException.kt` | Custom exception hierarchy |

### Configuration Classes

| Class | Description |
|-------|-------------|
| `SecureStoreConfig` | Main configuration class with builder pattern |
| `SecureStoreConfig.Builder` | Fluent builder for configuration |
| `EncryptionAlgorithm` | Enum of supported encryption algorithms |
| `KeyProtection` | Enum of key protection levels |
| `StorageMode` | Enum of storage modes |
| `DecryptionFailurePolicy` | Enum of decryption failure behaviors |

### Data Classes

| Class | Description |
|-------|-------------|
| `SecureStoreInfo` | Metadata about a SecureStore instance |

### Exceptions

| Exception | Description |
|-----------|-------------|
| `SecureStoreException` | Base sealed class for all exceptions |
| `InitializationException` | Tink or Keystore init failure |
| `EncryptionException` | Encryption operation failure |
| `DecryptionException` | Decryption operation failure |
| `KeystoreException` | Android Keystore failure |
| `StorageException` | I/O operation failure |
| `HardwareRequiredException` | Hardware keys unavailable |
| `SerializationException` | Object serialization failure |

## Generating API Documentation

SecureStore uses **Dokka** - the official documentation engine for Kotlin.

### Generate HTML Documentation

```bash
./gradlew dokkaHtml
```

Output: `build/dokka/html/index.html`

### Generate Javadoc Documentation

```bash
./gradlew dokkaJavadoc
```

Output: `build/dokka/javadoc/`

### View Documentation Locally

```bash
# Generate and open HTML docs
./gradlew dokkaHtml && open build/dokka/html/index.html

# On Linux
./gradlew dokkaHtml && xdg-open build/dokka/html/index.html
```

## Documentation Features

### 1. **KDoc Comments**

All public APIs are documented with KDoc comments:

```kotlin
/**
 * Securely stores a string value.
 *
 * @param key The unique identifier for the value
 * @param value The string to encrypt and store
 * @throws SecureStoreException.EncryptionException if encryption fails
 * @throws SecureStoreException.StorageException if storage fails
 */
suspend fun putString(key: String, value: String)
```

### 2. **Code Examples**

See [EXAMPLES.md](EXAMPLES.md) for comprehensive usage examples.

### 3. **Source Links**

Generated documentation includes links to source code on GitHub.

### 4. **Search Functionality**

HTML documentation includes full-text search.

## API Overview

### SecureStorage Interface

The main interface providing all storage operations:

```kotlin
interface SecureStorage {
    // String operations
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun removeString(key: String)
    suspend fun contains(key: String): Boolean
    
    // Object operations
    suspend fun <T> putObject(key: String, value: T, serializer: KSerializer<T>)
    suspend fun <T> getObject(key: String, serializer: KSerializer<T>): T?
    suspend fun removeObject(key: String)
    
    // Blob operations
    suspend fun saveBlob(fileName: String, payload: ByteArray)
    suspend fun readBlob(fileName: String): ByteArray?
    suspend fun deleteBlob(fileName: String): Boolean
    suspend fun blobExists(fileName: String): Boolean
    
    // Bulk operations
    suspend fun clearAll()
    suspend fun getAllKeys(): Set<String>
    suspend fun getAllBlobNames(): Set<String>
    
    // Metadata
    fun getStoreInfo(): SecureStoreInfo
}
```

### SecureStoreConfig

Configuration with builder pattern:

```kotlin
val config = SecureStoreConfig.Builder()
    .encryption(EncryptionAlgorithm.AES_256_GCM)
    .keyProtection(KeyProtection.HARDWARE_PREFERRED)
    .storageMode(StorageMode.DEVICE_PROTECTED)
    .namespace("my_app")
    .encryptKeys(true)
    .encryptFileNames(true)
    .useAssociatedData(true)
    .secureMemory(true)
    .decryptionFailurePolicy(DecryptionFailurePolicy.DELETE_AND_RETURN_NULL)
    .ioDispatcher(Dispatchers.IO)
    .build()
```

### Configuration Presets

```kotlin
// Standard configuration
SecureStoreConfig.DEFAULT

// Maximum security
SecureStoreConfig.HIGH_SECURITY

// Optimized for performance
SecureStoreConfig.PERFORMANCE
```

### Exception Handling

```kotlin
try {
    storage.putString("key", "value")
} catch (e: SecureStoreException.EncryptionException) {
    // Handle encryption failure
} catch (e: SecureStoreException.StorageException) {
    // Handle storage failure
} catch (e: SecureStoreException) {
    // Handle any SecureStore error
}
```

## Publishing Documentation

### With Maven Central Release

Documentation JARs are automatically generated and published:

```bash
./gradlew publishReleasePublicationToSonatypeRepository
```

This creates:
- `securestore-1.0.0-javadoc.jar` - Javadoc format
- `securestore-1.0.0-sources.jar` - Source code

### Standalone Documentation

```bash
# Build documentation
./gradlew dokkaHtml

# Copy to docs folder (optional)
cp -r build/dokka/html docs/api/

# Commit and push
git add docs/api/
git commit -m "Update API documentation"
git push
```

## Writing Good Documentation

### For Public APIs

- Add KDoc to all public classes, functions, and properties
- Include `@param` and `@return` tags
- Document exceptions with `@throws`
- Provide code examples in `@sample`

### Example

```kotlin
/**
 * A secure storage implementation using Google Tink encryption.
 *
 * This class provides encrypted storage for strings, objects, and binary data.
 * All data is encrypted using configurable AEAD algorithms with keys protected 
 * by Android Keystore.
 *
 * ## Thread Safety
 * All operations are thread-safe and can be called from multiple threads.
 *
 * ## Configuration
 * ```kotlin
 * val config = SecureStoreConfig.Builder()
 *     .encryption(EncryptionAlgorithm.AES_256_GCM)
 *     .keyProtection(KeyProtection.HARDWARE_PREFERRED)
 *     .build()
 * val storage = SecureStorageImpl(context, config)
 * ```
 *
 * ## Usage
 * ```kotlin
 * val storage = SecureStorageImpl(context)
 * storage.putString("key", "value")
 * val value = storage.getString("key")
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
    config: SecureStoreConfig = SecureStoreConfig.DEFAULT
) : SecureStorage
```

## Updating Documentation

When making changes:

1. Update relevant markdown files
2. Update KDoc comments in code
3. Regenerate API docs: `./gradlew dokkaHtml`
4. Update CHANGELOG.md with documentation changes
5. Commit all changes together

## Need Help?

- [Dokka Documentation](https://kotlinlang.org/docs/dokka-introduction.html)
- [KDoc Syntax](https://kotlinlang.org/docs/kotlin-doc.html)
- [Markdown Guide](https://www.markdownguide.org/)

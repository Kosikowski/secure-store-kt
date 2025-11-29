# Quick Start Guide

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.kosikowski:securestore:1.0.0")
}
```

## Basic Usage

```kotlin
import com.kosikowski.securestore.SecureStorageImpl

// Initialize with default configuration
val storage = SecureStorageImpl(context)

// Store a string
storage.putString("api_key", "secret-123")

// Retrieve a string
val apiKey = storage.getString("api_key")

// Check if key exists
if (storage.contains("api_key")) {
    // Key exists
}

// Remove a string
storage.removeString("api_key")
```

## Configuration

```kotlin
import com.kosikowski.securestore.*

// Custom configuration
val config = SecureStoreConfig.Builder()
    .encryption(EncryptionAlgorithm.AES_256_GCM)
    .keyProtection(KeyProtection.HARDWARE_PREFERRED)
    .namespace("my_app")
    .build()

val storage = SecureStorageImpl(context, config)

// Or use presets
val highSecurityStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
val performanceStorage = SecureStorageImpl(context, SecureStoreConfig.PERFORMANCE)
```

## Available Gradle Tasks

### Build Tasks
```bash
./gradlew clean                    # Clean build directory
./gradlew build                    # Build the library
./gradlew assembleRelease          # Build release AAR
```

### Test Tasks
```bash
./gradlew test                     # Run unit tests
./gradlew connectedAndroidTest     # Run instrumented tests
```

### Publishing Tasks
```bash
./gradlew publishToMavenLocal              # Publish to local Maven
./gradlew publishReleasePublicationToSonatypeRepository  # Publish to Maven Central
```

## Documentation

- **README.md** - Main library documentation
- **docs/CHANGELOG.md** - Version history
- **docs/EXAMPLES.md** - Code examples
- **docs/SECURITY.md** - Security policy
- **docs/CONTRIBUTING.md** - How to contribute
- **docs/PUBLISHING.md** - How to publish to Maven Central

## Quick Test

To verify the library works locally:

```bash
# Publish to local Maven
./gradlew publishToMavenLocal

# In another project, add to build.gradle.kts:
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.github.kosikowski:securestore:1.0.0")
}
```

## Configuration Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `encryption` | AES_256_GCM, AES_128_GCM, CHACHA20_POLY1305, AES_256_EAX | AES_256_GCM | Encryption algorithm |
| `keyProtection` | SOFTWARE, HARDWARE_PREFERRED, HARDWARE_REQUIRED | SOFTWARE | Key protection level |
| `storageMode` | CREDENTIAL_PROTECTED, DEVICE_PROTECTED | DEVICE_PROTECTED | Storage mode |
| `encryptKeys` | true/false | false | Encrypt SharedPreferences keys |
| `encryptFileNames` | true/false | false | Encrypt blob filenames |
| `useAssociatedData` | true/false | true | Use associated data for AEAD |
| `namespace` | String | "default" | Namespace for isolation |
| `secureMemory` | true/false | false | Wipe sensitive data from memory |
| `decryptionFailurePolicy` | RETURN_NULL, THROW_EXCEPTION, DELETE_AND_RETURN_NULL | RETURN_NULL | Decryption failure behavior |

## API Quick Reference

```kotlin
interface SecureStorage {
    // Strings
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun removeString(key: String)
    suspend fun contains(key: String): Boolean
    
    // Objects
    suspend fun <T> putObject(key: String, value: T, serializer: KSerializer<T>)
    suspend fun <T> getObject(key: String, serializer: KSerializer<T>): T?
    suspend fun removeObject(key: String)
    
    // Blobs
    suspend fun saveBlob(fileName: String, payload: ByteArray)
    suspend fun readBlob(fileName: String): ByteArray?
    suspend fun deleteBlob(fileName: String): Boolean
    suspend fun blobExists(fileName: String): Boolean
    
    // Bulk
    suspend fun clearAll()
    suspend fun getAllKeys(): Set<String>
    suspend fun getAllBlobNames(): Set<String>
    
    // Metadata
    fun getStoreInfo(): SecureStoreInfo
}
```

## Error Handling

```kotlin
import com.kosikowski.securestore.SecureStoreException

try {
    storage.putString("key", "value")
} catch (e: SecureStoreException.EncryptionException) {
    // Handle encryption failure
} catch (e: SecureStoreException.StorageException) {
    // Handle storage failure
} catch (e: SecureStoreException.HardwareRequiredException) {
    // Hardware-backed keys not available
}
```

## Support

For questions or issues:
- See documentation in the `docs/` directory
- Check the Troubleshooting section in README.md
- Review EXAMPLES.md for usage patterns
- Open a GitHub issue for bugs

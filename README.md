# SecureStore

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kosikowski/securestore.svg)](https://search.maven.org/artifact/io.github.kosikowski/securestore)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

A production-ready Android library for secure storage using Google Tink encryption with Android Keystore backing.

## Features

- 🔒 **Hardware-Backed Encryption**: Leverages Android Keystore for secure key management
- 🛡️ **Configurable Encryption**: AES-256-GCM, AES-128-GCM, ChaCha20-Poly1305, or AES-256-EAX
- 🔐 **Google Tink**: Built on Google's cryptography library for best practices
- 🧵 **Thread-Safe**: All operations are safe for concurrent access
- 📦 **Simple API**: Clean, intuitive interface for storing strings, objects, and binary data
- ⚡ **Coroutines**: Async operations using Kotlin coroutines
- 🎯 **Type-Safe**: kotlinx.serialization integration for objects
- 🔄 **Defense in Depth**: Separate encryption keys for different data types
- ⚙️ **Highly Configurable**: Customize encryption, key protection, storage mode, and more
- 🔑 **Metadata Encryption**: Optional encryption of keys and filenames

## Security Guarantees

- **Confidentiality**: All data is encrypted with authenticated encryption (AEAD)
- **Integrity**: Authenticated encryption prevents tampering
- **Key Protection**: Encryption keys never leave secure hardware (when available)
- **Device Protection**: Works before user unlocks device (API 24+)
- **Associated Data**: Prevents ciphertext relocation attacks

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.kosikowski:securestore:1.1.0")
}
```

### Requirements

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34+
- Kotlin 1.9+
- kotlinx.serialization (for object storage)
- kotlinx.coroutines

## Quick Start

### Basic Usage

```kotlin
// Initialize with default configuration
val secureStorage = SecureStorageImpl(context)

// Store and retrieve strings
secureStorage.putString("api_token", "secret-token-123")
val token = secureStorage.getString("api_token")

// Check if key exists
if (secureStorage.contains("api_token")) {
    // Key exists
}

// Remove data
secureStorage.removeString("api_token")
```

### Custom Configuration

```kotlin
// Create a custom configuration
val config = SecureStoreConfig.Builder()
    .encryption(EncryptionAlgorithm.AES_256_GCM)
    .keyProtection(KeyProtection.HARDWARE_PREFERRED)
    .storageMode(StorageMode.DEVICE_PROTECTED)
    .namespace("my_app")
    .encryptKeys(true)
    .encryptFileNames(true)
    .useAssociatedData(true)
    .decryptionFailurePolicy(DecryptionFailurePolicy.DELETE_AND_RETURN_NULL)
    .build()

val secureStorage = SecureStorageImpl(context, config)

// Or use preset configurations
val highSecurityStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
val performanceStorage = SecureStorageImpl(context, SecureStoreConfig.PERFORMANCE)
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `encryption` | Encryption algorithm (AES_256_GCM, AES_128_GCM, CHACHA20_POLY1305, AES_256_EAX) | AES_256_GCM |
| `keyProtection` | Key protection level (SOFTWARE, HARDWARE_PREFERRED, HARDWARE_REQUIRED) | SOFTWARE |
| `storageMode` | Storage mode (CREDENTIAL_PROTECTED, DEVICE_PROTECTED) | DEVICE_PROTECTED |
| `encryptKeys` | Encrypt SharedPreferences keys | false |
| `encryptFileNames` | Encrypt blob file names | false |
| `useAssociatedData` | Use key/filename as associated data | true |
| `decryptionFailurePolicy` | What to do on decryption failure | RETURN_NULL |
| `namespace` | Isolate multiple storage instances | "default" |
| `secureMemory` | Wipe sensitive data from memory after use | false |

### Storing Objects

```kotlin
@Serializable
data class UserCredentials(
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

// Store
val credentials = UserCredentials("john", "access-xyz", "refresh-abc")
secureStorage.putObject("user_creds", credentials, UserCredentials.serializer())

// Retrieve
val stored = secureStorage.getObject("user_creds", UserCredentials.serializer())

// Remove
secureStorage.removeObject("user_creds")
```

### Storing Binary Data

```kotlin
// Save a certificate or other binary data
val certificateBytes = loadCertificate()
secureStorage.saveBlob("client_cert", certificateBytes)

// Check if blob exists
if (secureStorage.blobExists("client_cert")) {
    // Read it back
    val cert = secureStorage.readBlob("client_cert")
}

// Delete when no longer needed
secureStorage.deleteBlob("client_cert")
```

### Listing Stored Data

```kotlin
// List all stored keys
val allKeys = secureStorage.getAllKeys()

// List all stored blob filenames
val allBlobs = secureStorage.getAllBlobNames()
```

### Get Store Information

```kotlin
val info = secureStorage.getStoreInfo()
println("Encryption: ${info.encryptionAlgorithm}")
println("Hardware-backed: ${info.isHardwareBacked}")
println("Namespace: ${info.namespace}")
println("Keys encrypted: ${info.keyEncryptionEnabled}")
println("Filenames encrypted: ${info.fileNameEncryptionEnabled}")
```

### Clear All Data

```kotlin
// Remove all stored data (useful for logout)
secureStorage.clearAll()
```

## Configuration Presets

### `SecureStoreConfig.DEFAULT`
Standard configuration for most use cases:
- AES-256-GCM encryption
- Software key protection
- Device-protected storage
- Associated data enabled

### `SecureStoreConfig.HIGH_SECURITY`
Maximum security for sensitive applications:
- AES-256-GCM encryption
- Hardware-required key protection
- Encrypted keys and filenames
- Secure memory wiping
- Auto-delete corrupted entries

### `SecureStoreConfig.PERFORMANCE`
Optimized for performance:
- ChaCha20-Poly1305 (faster on devices without AES-NI)
- Software key protection
- No metadata encryption

## Error Handling

SecureStore provides a custom exception hierarchy:

```kotlin
import com.kosikowski.securestore.SecureStoreException

try {
    secureStorage.putString("key", "value")
} catch (e: SecureStoreException.EncryptionException) {
    // Handle encryption failure
} catch (e: SecureStoreException.StorageException) {
    // Handle storage failure
} catch (e: SecureStoreException) {
    // Handle any other SecureStore error
}
```

### Exception Types

| Exception | Description |
|-----------|-------------|
| `InitializationException` | Tink or Keystore initialization failed |
| `EncryptionException` | Encryption operation failed |
| `DecryptionException` | Decryption operation failed |
| `KeystoreException` | Android Keystore operation failed |
| `StorageException` | File or SharedPreferences I/O failed |
| `HardwareRequiredException` | Hardware-backed keys required but unavailable |
| `SerializationException` | Object serialization/deserialization failed |

## Advanced Usage

### Namespace Isolation

Use namespaces to create isolated storage instances:

```kotlin
val userStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("user_data")
    .build())

val cacheStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("cache")
    .build())

// Data stored in one namespace is invisible to other namespaces
userStorage.putString("key", "value1")
cacheStorage.getString("key") // Returns null
```

### Dependency Injection

#### Hilt Example

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(context)
    
    @Provides
    @Singleton
    @Named("high_security")
    fun provideHighSecurityStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
}

// Usage
@HiltViewModel
class MyViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    @Named("high_security") private val highSecurityStorage: SecureStorage
) : ViewModel() {
    // Use secureStorage
}
```

#### Koin Example

```kotlin
val storageModule = module {
    single<SecureStorage> { SecureStorageImpl(androidContext()) }
    
    single<SecureStorage>(named("high_security")) {
        SecureStorageImpl(androidContext(), SecureStoreConfig.HIGH_SECURITY)
    }
}
```

### Custom Coroutine Dispatcher

```kotlin
val config = SecureStoreConfig.Builder()
    .ioDispatcher(Dispatchers.IO.limitedParallelism(4))
    .build()

val secureStorage = SecureStorageImpl(context, config)
```

## How It Works

### Architecture

1. **Master Key**: Protected by Android Keystore (hardware-backed when available)
2. **Encryption Keys**: Generated using Tink, encrypted by master key
3. **Data Encryption**: All data encrypted with configurable AEAD algorithm
4. **Storage**:
   - Key-Value pairs → Encrypted SharedPreferences
   - Blobs → Encrypted files in app's private directory

### Security Model

```
┌─────────────────────────────────────┐
│        Android Keystore             │
│    (Hardware-backed, StrongBox)     │
└────────────────┬────────────────────┘
                 │ Protects
                 ▼
┌─────────────────────────────────────┐
│         Tink Master Key             │
└────────────────┬────────────────────┘
                 │ Encrypts
                 ▼
┌─────────────────────────────────────┐
│    Data Encryption Keys (DEK)       │
│  - Preferences DEK                  │
│  - File DEK                         │
│  - Metadata DEK (optional)          │
└────────────────┬────────────────────┘
                 │ Encrypts
                 ▼
┌─────────────────────────────────────┐
│         Your Sensitive Data         │
│  - Strings, Objects, Blobs          │
│  - Keys & Filenames (optional)      │
└─────────────────────────────────────┘
```

### Thread Safety

All operations are thread-safe:
- **File operations**: Protected by per-file locks
- **Preferences**: Thread-safe by design
- **clearAll()**: Uses instance-level lock
- **Concurrent access**: Multiple threads can safely access different keys/files

## API Reference

### SecureStorage Interface

```kotlin
interface SecureStorage {
    // Strings
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun removeString(key: String)
    suspend fun contains(key: String): Boolean
    
    // Objects (requires kotlinx.serialization)
    suspend fun <T> putObject(key: String, value: T, serializer: KSerializer<T>)
    suspend fun <T> getObject(key: String, serializer: KSerializer<T>): T?
    suspend fun removeObject(key: String)
    
    // Binary data
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

## Performance Considerations

- First access initializes Tink (one-time cost ~100ms)
- Encryption/decryption is fast (~1ms for small payloads)
- Large blobs (>1MB) may take longer - consider chunking
- All operations are async-friendly (suspend functions)
- ChaCha20-Poly1305 is faster on devices without AES hardware acceleration

## Best Practices

1. **Initialization**: Create a single instance and reuse it (singleton)
2. **Error Handling**: Handle `SecureStoreException` subclasses appropriately
3. **Sensitive Data**: Never log decrypted values
4. **Key Names**: Use descriptive, unique keys to avoid collisions
5. **Logout**: Call `clearAll()` when user logs out
6. **Testing**: Use instrumented tests on real devices/emulators
7. **High Security**: Use `SecureStoreConfig.HIGH_SECURITY` for sensitive apps
8. **Namespaces**: Use separate namespaces for different data categories

## Comparison with Alternatives

| Feature | SecureStore | EncryptedSharedPreferences | DataStore | Plain SharedPreferences |
|---------|-------------|----------------------------|-----------|------------------------|
| Encryption | ✅ Configurable AEAD | ✅ AES-256-GCM | ❌ | ❌ |
| Keystore-backed | ✅ | ✅ | ❌ | ❌ |
| Binary storage | ✅ | ❌ | ❌ | ❌ |
| Object serialization | ✅ | ❌ | ✅ | ❌ |
| Thread-safe | ✅ | ✅ | ✅ | ⚠️ |
| Coroutines | ✅ | ❌ | ✅ | ❌ |
| Type-safe | ✅ | ❌ | ✅ | ❌ |
| Key encryption | ✅ | ✅ | ❌ | ❌ |
| Configurable | ✅ | ❌ | ⚠️ | ❌ |

## Troubleshooting

### Issue: `SecureStoreException.InitializationException`

**Cause**: Tink initialization failed  
**Solution**: Ensure app has proper permissions and Android Keystore is available

### Issue: `SecureStoreException.HardwareRequiredException`

**Cause**: Hardware-backed keys required but device doesn't support them  
**Solution**: Use `KeyProtection.HARDWARE_PREFERRED` or `KeyProtection.SOFTWARE`

### Issue: Data lost after app reinstall

**Cause**: Android Keystore keys are deleted on app uninstall  
**Solution**: This is intentional for security. Use server-side storage for persistence

### Issue: Performance degradation

**Cause**: Encrypting large files synchronously  
**Solution**: Use chunking or background processing for large blobs

## ProGuard / R8

No special configuration needed. The library is R8-friendly.

## Testing

### Running Tests

```bash
# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Writing Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class MyTest {
    private lateinit var storage: SecureStorage
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SecureStoreConfig.Builder()
            .namespace("test")
            .build()
        storage = SecureStorageImpl(context, config)
        runBlocking { storage.clearAll() }
    }
    
    @Test
    fun testStorage() = runBlocking {
        storage.putString("test", "value")
        assertEquals("value", storage.getString("test"))
        assertTrue(storage.contains("test"))
    }
}
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](docs/CONTRIBUTING.md) for guidelines.

## Security

If you discover a security vulnerability, please email security@example.com instead of using the issue tracker.

## License

```
Copyright 2025 Mateusz Kosikowski

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```


## Changelog

See [CHANGELOG.md](docs/CHANGELOG.md) for version history.

---

Made with ❤️ by [Mateusz Kosikowski](https://github.com/Kosikowski)

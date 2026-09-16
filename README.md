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
- ♻️ **Lost-Key Recovery**: Recovers when Android deletes the Keystore key, e.g. after the app's data is cleared
- 🔁 **Key Rotation**: Add a new encryption key while existing data stays readable

## Security Guarantees

- **Confidentiality**: All data is encrypted with authenticated encryption (AEAD)
- **Integrity**: Authenticated encryption prevents tampering
- **Key Protection**: The master key lives in Android Keystore and never leaves secure hardware when the device has it; the data keys are stored encrypted by it
- **Device Protection**: `DEVICE_PROTECTED` stores (the default) work before the user unlocks the device, in direct-boot-aware apps
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
| `masterKeyAlias` | Android Keystore alias of the master key (the namespace is appended) | "secure_store_master_key" |
| `ioDispatcher` | Coroutine dispatcher for storage operations | `Dispatchers.IO` |
| `secureMemory` | Wipe sensitive data from memory after use | false |
| `keysetLossPolicy` | What to do when the keysets can no longer be opened (RESET, THROW) | RESET |
| `onKeysetReset` | Called after RESET discarded the stored data | no-op |

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

### Lost Keys

Android deletes an app's Keystore keys when its data is cleared, and, for apps that share an
`android:sharedUserId`, when the data of **any** of those apps is cleared. A keyset can also be
corrupted. Either way the stored data can never be decrypted again.

By default (`KeysetLossPolicy.RESET`) the store deletes the unreadable keysets and data, creates new
keysets and carries on empty. `onKeysetReset` tells you it happened, so you can report it and restore
what the app needs:

```kotlin
val config = SecureStoreConfig.Builder()
    .onKeysetReset { cause ->
        crashReporter.recordNonFatal(cause)
        sessionManager.requireSignIn()
    }
    .build()
```

With `KeysetLossPolicy.THROW` every operation except `getStoreInfo()` throws `KeysetLostException` until you call `reset()`:

```kotlin
try {
    secureStorage.getString("token")
} catch (e: SecureStoreException.KeysetLostException) {
    secureStorage.reset()
}
```

### Key Rotation

```kotlin
secureStorage.rotateKeys()
```

Adds a new key to the keysets that encrypt values and blobs and uses it for all later writes. Existing
data stays readable with the earlier keys, which remain in the keysets; a value or blob moves to the new
key the next time it is written. The new key uses the configured `encryption`, so rotating also switches a
store to a newly configured algorithm. Name encryption keeps its key.

## Configuration Presets

### `SecureStoreConfig.DEFAULT`
Standard configuration for most use cases:
- AES-256-GCM encryption
- No hardware requirement (Android Keystore still uses secure hardware when the device has it)
- Device-protected storage
- Associated data enabled

### `SecureStoreConfig.HIGH_SECURITY`
Maximum security for sensitive applications:
- Its own namespace, `high_security`
- AES-256-GCM encryption
- Requires the master key to be in secure hardware (operations throw `HardwareRequiredException` otherwise, e.g. on emulators)
- Encrypted keys and filenames
- Secure memory wiping
- Auto-delete corrupted entries

### `SecureStoreConfig.PERFORMANCE`
Optimized for performance:
- ChaCha20-Poly1305 (faster on devices without AES-NI)
- No hardware requirement
- No metadata encryption
- No associated data

`PERFORMANCE` uses the `default` namespace, like `DEFAULT`, and stores values without associated data,
so neither can read what the other wrote. To use both, give one its own namespace:

```kotlin
val cache = SecureStorageImpl(context, SecureStoreConfig.PERFORMANCE.toBuilder().namespace("cache").build())
```

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
| `KeysetLostException` | The keysets can no longer be opened (only with `KeysetLossPolicy.THROW`) |
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

Stores that share a namespace and storage mode share their data and keysets, so they must use the same
`masterKeyAlias`; creating one with a different alias throws `IllegalArgumentException`. Settings that
change how data is stored (`encryptKeys`, `encryptFileNames`, `useAssociatedData`) should also match,
or each store only reads what it wrote itself.

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

1. **Master Key**: An AES key in Android Keystore, in secure hardware when the device has it
2. **Keysets**: Tink keysets for values, blobs and (optionally) names, encrypted by the master key
3. **Data Encryption**: Values and blobs are encrypted with the configured AEAD algorithm; names deterministically with AES-SIV
4. **Storage**:
   - Key-Value pairs → Encrypted SharedPreferences
   - Blobs → Encrypted files in app's private directory
   - Keysets → SharedPreferences next to the data, so `DEVICE_PROTECTED` keeps them in device-protected storage

### Security Model

```
┌─────────────────────────────────────┐
│  Master key in Android Keystore     │
│   (secure hardware when available)  │
└────────────────┬────────────────────┘
                 │ Encrypts
                 ▼
┌─────────────────────────────────────┐
│          Tink keysets               │
│  - Values keyset                    │
│  - Blobs keyset                     │
│  - Names keyset (optional, AES-SIV) │
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
- **File operations**: Protected by locks shared by all instances of the store; `saveBlob` writes to a separate file and renames it, so a crash never leaves a truncated blob
- **Preferences**: Thread-safe by design
- **clearAll()**: Uses a lock shared by the instances of the store
- **reset()**: Waits for running operations on the store to finish, and later operations wait for the reset
- **Several instances**: Instances with the same storage mode and namespace share their state within the process, so a reset through one applies to all of them
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
    suspend fun reset()
    suspend fun rotateKeys()
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
9. **Lost Keys**: Use `onKeysetReset` to learn when stored data was discarded, e.g. to ask the user to sign in again

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

**Cause**: Tink initialization failed, or, for a `DEVICE_PROTECTED` store that already holds data, the device has not been unlocked since upgrading from 1.0.0 (its keysets are copied to device-protected storage on first unlock)  
**Solution**: Ensure Android Keystore is available; for the upgrade case, retry after the first unlock

### Issue: `SecureStoreException.HardwareRequiredException`

**Cause**: `KeyProtection.HARDWARE_REQUIRED` is set (as in `HIGH_SECURITY`) but the device keeps the master key in software, for example on an emulator  
**Solution**: Use `KeyProtection.SOFTWARE` where software keys are acceptable

### Issue: Data lost after app reinstall

**Cause**: Android Keystore keys are deleted on app uninstall  
**Solution**: This is intentional for security. Use server-side storage for persistence

### Issue: Stored data disappeared without a reinstall

**Cause**: The keysets could no longer be opened, for example because the app's data, or the data of an app sharing its user ID, was cleared. `KeysetLossPolicy.RESET` discarded the data  
**Solution**: Listen with `onKeysetReset` to detect it and restore what the app needs; see [Lost Keys](#lost-keys)

### Issue: Performance degradation

**Cause**: Encrypting large files synchronously  
**Solution**: Use chunking or background processing for large blobs

## ProGuard / R8

No special configuration needed. The library is R8-friendly.

## Testing

### Running Tests

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

See [TESTING.md](docs/TESTING.md) for details.

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

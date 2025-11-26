# SecureStore

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kosikowski/securestore.svg)](https://search.maven.org/artifact/io.github.kosikowski/securestore)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

A production-ready Android library for secure storage using Google Tink encryption with Android Keystore backing.

## Features

- 🔒 **Hardware-Backed Encryption**: Leverages Android Keystore for secure key management
- 🛡️ **AES-256-GCM**: Industry-standard authenticated encryption
- 🔐 **Google Tink**: Built on Google's cryptography library for best practices
- 🧵 **Thread-Safe**: All operations are safe for concurrent access
- 📦 **Simple API**: Clean, intuitive interface for storing strings, objects, and binary data
- ⚡ **Coroutines**: Async operations using Kotlin coroutines
- 🎯 **Type-Safe**: kotlinx.serialization integration for objects
- 🔄 **Defense in Depth**: Separate encryption keys for different data types

## Security Guarantees

- **Confidentiality**: All data is encrypted with AES-256-GCM
- **Integrity**: Authenticated encryption prevents tampering
- **Key Protection**: Encryption keys never leave secure hardware (when available)
- **Device Protection**: Works before user unlocks device (API 24+)

## Installation

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.kosikowski:securestore:1.0.0")
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
// Initialize (typically in Application.onCreate or using dependency injection)
val secureStorage = SecureStorageImpl(context)

// Store and retrieve strings
secureStorage.putString("api_token", "secret-token-123")
val token = secureStorage.getString("api_token")

// Remove data
secureStorage.removeString("api_token")
```

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
```

### Storing Binary Data

```kotlin
// Save a certificate or other binary data
val certificateBytes = loadCertificate()
secureStorage.saveBlob("client_cert", certificateBytes)

// Read it back
val cert = secureStorage.readBlob("client_cert")

// Delete when no longer needed
secureStorage.deleteBlob("client_cert")
```

### Clear All Data

```kotlin
// Remove all stored data (useful for logout)
secureStorage.clearAll()
```

## Advanced Usage

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
}

// Usage
@HiltViewModel
class MyViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {
    // Use secureStorage
}
```

#### Koin Example

```kotlin
val storageModule = module {
    single<SecureStorage> { SecureStorageImpl(androidContext()) }
}
```

### Custom Coroutine Dispatcher

```kotlin
// Use a custom dispatcher for IO operations
val secureStorage = SecureStorageImpl(
    context = context,
    ioDispatcher = Dispatchers.IO.limitedParallelism(4)
)
```

### Error Handling

```kotlin
try {
    secureStorage.putString("key", "value")
} catch (e: IOException) {
    // Handle storage failure
    Log.e(TAG, "Failed to save data", e)
}
```

## How It Works

### Architecture

1. **Master Key**: Protected by Android Keystore (hardware-backed when available)
2. **Encryption Keys**: Generated using Tink, encrypted by master key
3. **Data Encryption**: All data encrypted with AES-256-GCM
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
└────────────────┬────────────────────┘
                 │ Encrypts
                 ▼
┌─────────────────────────────────────┐
│         Your Sensitive Data         │
│  - Strings, Objects, Blobs          │
└─────────────────────────────────────┘
```

### Thread Safety

All operations are thread-safe:
- **File operations**: Protected by per-file locks
- **Preferences**: Thread-safe by design
- **clearAll()**: Uses instance-level lock
- **Concurrent access**: Multiple threads can safely access different keys/files

## Performance Considerations

- First access initializes Tink (one-time cost ~100ms)
- Encryption/decryption is fast (~1ms for small payloads)
- Large blobs (>1MB) may take longer - consider chunking
- All operations are async-friendly (suspend functions)

## API Reference

### SecureStorage Interface

```kotlin
interface SecureStorage {
    // Strings
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun removeString(key: String)
    
    // Objects (requires kotlinx.serialization)
    suspend fun <T> putObject(key: String, value: T, serializer: KSerializer<T>)
    suspend fun <T> getObject(key: String, serializer: KSerializer<T>): T?
    
    // Binary data
    suspend fun saveBlob(fileName: String, payload: ByteArray)
    suspend fun readBlob(fileName: String): ByteArray?
    suspend fun deleteBlob(fileName: String): Boolean
    
    // Clear all
    suspend fun clearAll()
}
```

## Best Practices

1. **Initialization**: Create a single instance and reuse it (singleton)
2. **Error Handling**: Always wrap operations in try-catch for IOException
3. **Sensitive Data**: Never log decrypted values
4. **Key Names**: Use descriptive, unique keys to avoid collisions
5. **Logout**: Call `clearAll()` when user logs out
6. **Testing**: Use instrumented tests on real devices/emulators

## Comparison with Alternatives

| Feature | SecureStore | EncryptedSharedPreferences | DataStore | Plain SharedPreferences |
|---------|-------------|----------------------------|-----------|------------------------|
| Encryption | ✅ AES-256-GCM | ✅ AES-256-GCM | ❌ | ❌ |
| Keystore-backed | ✅ | ✅ | ❌ | ❌ |
| Binary storage | ✅ | ❌ | ❌ | ❌ |
| Object serialization | ✅ | ❌ | ✅ | ❌ |
| Thread-safe | ✅ | ✅ | ✅ | ⚠️ |
| Coroutines | ✅ | ❌ | ✅ | ❌ |
| Type-safe | ✅ | ❌ | ✅ | ❌ |

## Troubleshooting

### Issue: `IllegalStateException: Failed to initialize Tink AEAD`

**Cause**: Tink initialization failed  
**Solution**: Ensure app has proper permissions and Android Keystore is available

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
        storage = SecureStorageImpl(ApplicationProvider.getApplicationContext())
        runBlocking { storage.clearAll() }
    }
    
    @Test
    fun testStorage() = runBlocking {
        storage.putString("test", "value")
        assertEquals("value", storage.getString("test"))
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


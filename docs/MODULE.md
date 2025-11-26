# Module SecureStore

SecureStore is a production-ready Android library that provides secure storage using Google Tink encryption with Android Keystore backing.

## Overview

This library offers a simple and secure way to store sensitive data on Android devices. It leverages:

- **Android Keystore** for hardware-backed key protection
- **Google Tink** for cryptographic operations
- **AES-256-GCM** for authenticated encryption
- **Kotlin Coroutines** for async operations

## Key Features

- 🔒 Hardware-backed encryption when available
- 🛡️ AES-256-GCM authenticated encryption
- 🧵 Thread-safe concurrent access
- 📦 Support for strings, objects (via kotlinx.serialization), and binary data
- ⚡ Async operations with Kotlin coroutines
- 🔄 Defense in depth with separate encryption keys

## Security Model

The library implements a multi-layered security architecture:

1. **Master Key** - Stored in Android Keystore (hardware-backed when available)
2. **Data Encryption Keys (DEKs)** - Encrypted by the master key
3. **Your Data** - Encrypted by DEKs using AES-256-GCM

## Quick Example

```kotlin
// Initialize
val secureStorage = SecureStorageImpl(context)

// Store encrypted data
secureStorage.putString("api_token", "secret-token-123")

// Retrieve decrypted data
val token = secureStorage.getString("api_token")
```

## Packages

| Package | Description |
|---------|-------------|
| `com.kosikowski.securestore` | Core interfaces and implementation for secure storage |

## See Also

- [GitHub Repository](https://github.com/Kosikowski/secure-store-kt)
- [Quick Start Guide](QUICKSTART.md)
- [Examples](EXAMPLES.md)
- [Security Information](SECURITY.md)


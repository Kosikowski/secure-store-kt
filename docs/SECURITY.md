# Security Policy

## Supported Versions

Currently supported versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :white_check_mark: |

## Security Features

SecureStore implements multiple layers of security:

### Encryption Algorithms

SecureStore supports multiple authenticated encryption algorithms:

| Algorithm | Key Size | Description |
|-----------|----------|-------------|
| AES-256-GCM | 256 bits | Default. Industry standard, hardware-accelerated on most devices |
| AES-128-GCM | 128 bits | Faster, slightly smaller ciphertext, still secure |
| ChaCha20-Poly1305 | 256 bits | Better performance on devices without AES-NI |
| AES-256-EAX | 256 bits | Alternative authenticated encryption mode |

All algorithms provide:
- **Authenticated Encryption**: Built-in message authentication (AEAD)
- **Nonce Management**: Automatic, secure nonce generation
- **Tamper Detection**: Any modification to ciphertext is detected

### Key Protection Levels

| Level | Description | Use Case |
|-------|-------------|----------|
| SOFTWARE | Software-backed keys (default) | General purpose, works on all devices |
| HARDWARE_PREFERRED | Hardware-backed when available | Recommended for sensitive data |
| HARDWARE_REQUIRED | Hardware-backed required | Maximum security, may fail on some devices |

### Key Management
- **Storage**: Android Keystore (hardware-backed when available)
- **Protection**: Keys never leave secure hardware
- **Generation**: Cryptographically secure random generation
- **Isolation**: Separate keys for preferences, files, and metadata

### Metadata Protection

Optional encryption of metadata to hide what data is stored:

| Option | Description |
|--------|-------------|
| `encryptKeys` | Encrypts SharedPreferences keys |
| `encryptFileNames` | Encrypts blob file names |
| `useAssociatedData` | Binds ciphertext to key/filename, preventing relocation attacks |

### Android Integration
- **Keystore**: Uses Android Keystore system
- **StrongBox**: Utilizes StrongBox when available (Pixel 3+, Samsung S9+)
- **TEE**: Trusted Execution Environment support
- **Attestation**: Key attestation on supported devices

### Secure Memory

When `secureMemory` is enabled:
- Sensitive byte arrays are zeroed after use
- Reduces exposure window for memory attacks

## Configuration Presets

### SecureStoreConfig.DEFAULT
Standard security for most applications:
```kotlin
- AES-256-GCM encryption
- Software key protection
- Device-protected storage
- Associated data enabled
- No metadata encryption
```

### SecureStoreConfig.HIGH_SECURITY
Maximum security for sensitive applications:
```kotlin
- AES-256-GCM encryption
- Hardware-required key protection
- Device-protected storage
- Associated data enabled
- Key encryption enabled
- Filename encryption enabled
- Secure memory wiping enabled
- Corrupted entries auto-deleted
```

### SecureStoreConfig.PERFORMANCE
Optimized for speed:
```kotlin
- ChaCha20-Poly1305 encryption
- Software key protection
- No metadata encryption
- No secure memory wiping
```

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:
- **Email**: security@example.com
- **Subject**: "SecureStore Security Vulnerability"

### What to Include

1. **Description**: Detailed description of the vulnerability
2. **Impact**: Potential impact and attack scenarios
3. **Reproduction**: Steps to reproduce the issue
4. **Environment**: 
   - Library version
   - Android version
   - Device model
   - Configuration used
4. **Suggested Fix**: If you have one

### Response Timeline

- **Initial Response**: Within 48 hours
- **Status Update**: Within 7 days
- **Fix Timeline**: Depends on severity
  - Critical: 1-2 weeks
  - High: 2-4 weeks
  - Medium: 4-8 weeks
  - Low: Next release cycle

### Disclosure Policy

We follow coordinated vulnerability disclosure:

1. **Private Disclosure**: Report sent to maintainers
2. **Acknowledgment**: We confirm receipt and validate
3. **Fix Development**: We develop and test a fix
4. **Fix Release**: We release patched version
5. **Public Disclosure**: After fix is available (typically 90 days)

We will credit you in the security advisory unless you prefer to remain anonymous.

## Security Best Practices for Users

### 1. Use Appropriate Configuration

```kotlin
// For sensitive data (auth tokens, credentials)
val storage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)

// For general data
val storage = SecureStorageImpl(context, SecureStoreConfig.DEFAULT)
```

### 2. Keep Updated
```kotlin
dependencies {
    // Always use the latest version
    implementation("io.github.kosikowski:securestore:1.1.0")
}
```

### 3. Never Log Decrypted Data
```kotlin
// ❌ DON'T
val token = storage.getString("token")
Log.d(TAG, "Token: $token")  // Logs sensitive data!

// ✅ DO
val token = storage.getString("token")
Log.d(TAG, "Token retrieved successfully")
```

### 4. Clear Data on Logout
```kotlin
// Always clear sensitive data when user logs out
suspend fun logout() {
    secureStorage.clearAll()
    // Other logout logic
}
```

### 5. Handle Errors Properly
```kotlin
// Use proper exception handling
try {
    storage.putString("key", "value")
} catch (e: SecureStoreException.EncryptionException) {
    // Log to crash reporting, not logcat
    crashReporter.log(e)
    // Show generic error to user
    showError("Failed to save data")
} catch (e: SecureStoreException) {
    crashReporter.log(e)
    showError("Storage error")
}
```

### 6. Use HTTPS Only
```kotlin
// Never transmit decrypted data over HTTP
val token = storage.getString("api_token")
// Use only with HTTPS endpoints
apiClient.authenticate(token)
```

### 7. Validate Data
```kotlin
// Validate data after retrieval
val token = storage.getString("token")
if (token?.matches("[A-Za-z0-9]+".toRegex()) == true) {
    // Use token
} else {
    // Handle invalid/tampered data
}
```

### 8. Use Namespaces for Isolation
```kotlin
// Isolate different types of data
val authStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("auth")
    .keyProtection(KeyProtection.HARDWARE_PREFERRED)
    .build())

val cacheStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("cache")
    .build())
```

### 9. ProGuard/R8
```kotlin
// Ensure ProGuard rules are applied
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

## Known Limitations

### 1. Root/Jailbreak
- **Risk**: Rooted devices may compromise key security
- **Mitigation**: Consider root detection for sensitive apps
- **Note**: Even on rooted devices, keys remain in Keystore

### 2. Backup
- **Risk**: Android backup may include encrypted data
- **Mitigation**: Keys are NOT backed up (data unrecoverable after restore)
- **Note**: This is intentional for security

### 3. Key Loss Scenarios
Keys are lost when:
- App is uninstalled
- User changes lock screen security (on some devices)
- User clears app data
- Device is factory reset

### 4. Screen Lock Requirement
- **Requirement**: Device must have a screen lock for hardware-backed keys
- **Reason**: Required for Android Keystore security
- **Fallback**: SOFTWARE key protection works without screen lock

### 5. Hardware Availability
- **HARDWARE_REQUIRED**: May fail on older devices
- **Solution**: Use HARDWARE_PREFERRED for broader compatibility

## Security Audits

| Date | Auditor | Scope | Findings | Status |
|------|---------|-------|----------|--------|
| TBD  | TBD     | TBD   | TBD      | Planned |

## Security Updates

Subscribe to security updates:
- GitHub: Watch releases for this repository
- Email: security-announce@example.com (coming soon)

## Compliance

SecureStore is designed to help meet:
- **PCI DSS**: Payment card data encryption requirements
- **HIPAA**: Protected health information security
- **GDPR**: Data protection requirements
- **SOC 2**: Security controls

**Note**: Compliance is a shared responsibility. Proper implementation and usage are required.

## Exception Handling for Security

SecureStore provides specific exceptions for security-related failures:

| Exception | Security Implication |
|-----------|---------------------|
| `HardwareRequiredException` | Device doesn't meet security requirements |
| `DecryptionException` | Possible data tampering or key issues |
| `KeystoreException` | Android Keystore compromise or corruption |
| `InitializationException` | Cryptographic system failure |

```kotlin
try {
    val data = storage.getString("sensitive_key")
} catch (e: SecureStoreException.DecryptionException) {
    // Possible tampering - take appropriate action
    securityAudit.log("Decryption failure: possible tampering")
    storage.clearAll()
} catch (e: SecureStoreException.KeystoreException) {
    // Keystore issue - may need user intervention
    promptUserToReauthenticate()
}
```

## References

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [Google Tink](https://github.com/google/tink)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [AEAD Encryption](https://en.wikipedia.org/wiki/Authenticated_encryption)

## Questions?

For security questions (non-vulnerabilities):
- **Email**: security@example.com
- **Discussions**: GitHub Discussions

---

Last Updated: 2025-11-28

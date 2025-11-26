# Security Policy

## Supported Versions

Currently supported versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Security Features

SecureStore implements multiple layers of security:

### Encryption
- **Algorithm**: AES-256-GCM (Authenticated Encryption with Associated Data)
- **Key Size**: 256 bits
- **Mode**: GCM (Galois/Counter Mode)
- **Authentication**: Built-in message authentication

### Key Management
- **Storage**: Android Keystore (hardware-backed when available)
- **Protection**: Keys never leave secure hardware
- **Generation**: Cryptographically secure random generation
- **Isolation**: Separate keys for preferences and files

### Android Integration
- **Keystore**: Uses Android Keystore system
- **StrongBox**: Utilizes StrongBox when available (Pixel 3+, Samsung S9+)
- **TEE**: Trusted Execution Environment support
- **Attestation**: Key attestation on supported devices

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

### 1. Keep Updated
```kotlin
dependencies {
    // Always use the latest version
    implementation("io.github.kosikowski:securestore:1.0.0")
}
```

### 2. Never Log Decrypted Data
```kotlin
// ❌ DON'T
val token = storage.getString("token")
Log.d(TAG, "Token: $token")  // Logs sensitive data!

// ✅ DO
val token = storage.getString("token")
Log.d(TAG, "Token retrieved successfully")
```

### 3. Clear Data on Logout
```kotlin
// Always clear sensitive data when user logs out
suspend fun logout() {
    secureStorage.clearAll()
    // Other logout logic
}
```

### 4. Handle Errors Properly
```kotlin
// Don't expose error details in production
try {
    storage.putString("key", "value")
} catch (e: IOException) {
    // Log to crash reporting, not logcat
    Timber.e(e, "Storage operation failed")
    // Show generic error to user
}
```

### 5. Use HTTPS Only
```kotlin
// Never transmit decrypted data over HTTP
val token = storage.getString("api_token")
// Use only with HTTPS endpoints
apiClient.authenticate(token)
```

### 6. Validate Data
```kotlin
// Validate data after retrieval
val token = storage.getString("token")
if (token?.matches("[A-Za-z0-9]+".toRegex()) == true) {
    // Use token
} else {
    // Handle invalid/tampered data
}
```

### 7. ProGuard/R8
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
- User changes lock screen security
- User clears app data
- Device is factory reset

### 4. Screen Lock Requirement
- **Requirement**: Device must have a screen lock
- **Reason**: Required for Android Keystore security
- **Fallback**: Consider app-level PIN for devices without lock

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

## References

- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [Google Tink](https://github.com/google/tink)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)

## Questions?

For security questions (non-vulnerabilities):
- **Email**: security@example.com
- **Discussions**: GitHub Discussions

---

Last Updated: 2025-11-25


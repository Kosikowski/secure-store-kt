# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2025-11-25

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
[1.0.0]: https://github.com/Kosikowski/secure-store-kt/releases/tag/v1.0.0


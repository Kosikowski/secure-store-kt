# Contributing to SecureStore

First off, thank you for considering contributing to SecureStore! It's people like you that make this library better for everyone.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct:
- Be respectful and inclusive
- Accept constructive criticism gracefully
- Focus on what's best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce** the issue
- **Expected behavior**
- **Actual behavior**
- **Device/emulator information** (Android version, device model)
- **Library version**
- **Code sample** that demonstrates the issue
- **Logs** if applicable

**Example bug report:**

```markdown
### Bug: clearAll() doesn't remove blob files

**Environment:**
- Library version: 1.0.0
- Android version: 11
- Device: Pixel 5 emulator

**Steps to reproduce:**
1. Save a blob: `storage.saveBlob("test", byteArrayOf(1,2,3))`
2. Call `storage.clearAll()`
3. Read the blob: `storage.readBlob("test")`

**Expected:** Returns null
**Actual:** Returns the original data

**Code sample:**
...
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear, descriptive title**
- **Provide a detailed description** of the proposed feature
- **Explain why this enhancement would be useful**
- **Provide code examples** of how it would be used

### Security Vulnerabilities

**DO NOT** open a public issue for security vulnerabilities. Instead:
- Contact me privately via GitHub ([@Kosikowski](https://github.com/Kosikowski))
- Mention "SecureStore Security" when you get in touch
- Provide detailed description and reproduction steps
- Allow time for a fix before public disclosure

## Development Setup

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17+
- Android SDK with API 24+
- Git

### Setting Up Your Development Environment

1. **Fork and clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/secure-store-kt.git
   cd secure-store-kt
   ```

2. **Open in Android Studio:**
   - File → Open → Select the secure-store-kt directory

3. **Sync Gradle:**
   - Let Android Studio sync the project

4. **Run tests:**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

### Project Structure

```
secure-store-kt/
├── src/
│   ├── main/kotlin/com/kosikowski/securestore/
│   │   ├── SecureStorage.kt          # Interface
│   │   ├── SecureStorageImpl.kt      # Implementation
│   │   ├── SecureStoreConfig.kt      # Configuration, presets and policies
│   │   ├── SecureStoreException.kt   # Exception hierarchy
│   │   ├── KeysetLoss.kt             # Lost-keyset detection
│   │   ├── KeysetStorageContext.kt   # Keeps keysets next to device-protected data
│   │   └── NameCipher.kt             # Deterministic key and file name encryption
│   ├── test/kotlin/com/kosikowski/securestore/          # Unit tests
│   └── androidTest/kotlin/com/kosikowski/securestore/   # Instrumented tests
├── build.gradle.kts                   # Build configuration
├── README.md                          # Documentation
└── docs/                              # Guides, including this file
```

## Coding Standards

### Kotlin Style Guide

Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use trailing commas in multiline declarations
- Order modifiers: `public/protected/private`, `abstract`, `override`, `suspend`

### Code Quality

- **No warnings**: Code must compile without warnings
- **Ktlint**: Run `./gradlew ktlintFormat` before committing; `./gradlew installGitHooks` adds a pre-push `ktlintCheck`
- **Documentation**: All public APIs must have KDoc comments
- **Tests**: All new features must have tests

### Documentation Standards

```kotlin
/**
 * Brief one-line description.
 *
 * More detailed explanation if needed. Can span multiple paragraphs.
 *
 * @param key Description of parameter
 * @return Description of return value
 * @throws IOException Description of when this is thrown
 * @since 1.0.0 (for new APIs)
 */
suspend fun example(key: String): String?
```

## Testing

### Test Requirements

- **Unit tests** for logic without Android dependencies
- **Instrumented tests** for Android-specific functionality
- **Thread-safety tests** for concurrent operations
- **Edge case tests** for boundary conditions

### Writing Tests

```kotlin
@Test
fun descriptiveTestName_condition_expectedBehavior() = runBlocking {
    // Given
    val input = "test-data"
    
    // When
    storage.putString("key", input)
    
    // Then
    assertEquals(input, storage.getString("key"))
}
```

### Running Tests

```bash
# Run all tests
./gradlew test connectedAndroidTest

# Run specific instrumented test class
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kosikowski.securestore.SecureStorageInstrumentedTest

# Run specific instrumented test method
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kosikowski.securestore.SecureStorageInstrumentedTest#putAndGetString_roundTrip

# Run specific unit test class
./gradlew testDebugUnitTest --tests "com.kosikowski.securestore.KeysetLossTest"
```

## Pull Request Process

### Before Submitting

1. ✅ **Tests pass**: All existing and new tests pass
2. ✅ **Code formatted**: Run `./gradlew ktlintFormat`
3. ✅ **No warnings**: Code compiles cleanly
4. ✅ **Documentation**: Public APIs are documented
5. ✅ **Changelog**: Update CHANGELOG.md if applicable

### PR Guidelines

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/my-feature
   # or
   git checkout -b fix/bug-description
   ```

2. **Make your changes:**
   - Write clean, documented code
   - Add/update tests
   - Update documentation

3. **Commit with clear messages:**
   ```bash
   git commit -m "Add support for encrypted blob streaming"
   ```
   
   Good commit messages:
   - Use present tense ("Add feature" not "Added feature")
   - Be concise but descriptive
   - Reference issues: "Fix #123: Handle null values in getString"

4. **Push and create PR:**
   ```bash
   git push origin feature/my-feature
   ```
   Then open a PR on GitHub

5. **PR Description Template:**
   ```markdown
   ## Description
   Brief description of changes
   
   ## Motivation
   Why is this change needed?
   
   ## Changes
   - Change 1
   - Change 2
   
   ## Testing
   - [ ] Unit tests added/updated
   - [ ] Instrumented tests added/updated
   - [ ] Manually tested on device
   
   ## Checklist
   - [ ] Code follows style guidelines
   - [ ] Documentation updated
   - [ ] Tests pass
   - [ ] No new warnings
   
   Closes #<issue-number>
   ```

### PR Review Process

1. **Automated checks** must pass (tests, linting)
2. **Code review** by at least one maintainer
3. **Address feedback** promptly
4. **Squash commits** if requested
5. **Merge** once approved

## Releasing (Maintainers Only)

### Version Numbering

Follow [Semantic Versioning](https://semver.org/):
- **MAJOR**: Incompatible API changes
- **MINOR**: New backward-compatible functionality
- **PATCH**: Backward-compatible bug fixes

### Release Checklist

1. Update `version` in `build.gradle.kts` (the published version comes from here, not from the tag)
2. Move the `## [Unreleased]` entries in `docs/CHANGELOG.md` under the new version and update the compare links
3. Update the dependency snippets in `README.md` and `docs/*.md`
4. Merge to `main`
5. Publish a GitHub Release tagged `vX.Y.Z` with the changelog entries; the `Publish to Maven Central` workflow
   builds, signs and releases it (see [PUBLISHING.md](PUBLISHING.md))

## Questions?

- 💬 **Contact**: Reach me via GitHub ([@Kosikowski](https://github.com/Kosikowski))
- 🐛 **Issues**: Use [GitHub Issues](https://github.com/Kosikowski/secure-store-kt/issues)


Thank you for contributing! 🎉


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
    ├── PROJECT_SUMMARY.md      # Project summary
    └── api/                    # Generated API docs (gitignored)
        ├── html/               # HTML documentation
        └── javadoc/            # Javadoc format
```

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
 * @throws IOException if encryption or storage fails
 */
suspend fun putString(key: String, value: String)
```

### 2. **Code Examples**

See [EXAMPLES.md](EXAMPLES.md) for comprehensive usage examples.

### 3. **Source Links**

Generated documentation includes links to source code on GitHub.

### 4. **Search Functionality**

HTML documentation includes full-text search.

## Documentation Options

### Alternative Tools

1. **Dokka** (Current) ✅
   - Official Kotlin documentation tool
   - Multiple output formats (HTML, Javadoc, Markdown)
   - GitHub integration
   - Maven Central compatible

2. **Orchid**
   - Beautiful static site generator
   - More customizable
   - Requires more setup

3. **GitHub Pages**
   - Host documentation online
   - Automated deployment via GitHub Actions

4. **Read the Docs**
   - Professional documentation hosting
   - Version management
   - Free for open source

### Recommended Setup (GitHub Pages)

1. Generate docs: `./gradlew dokkaHtml`
2. Copy to `docs/api/html/`
3. Enable GitHub Pages in repository settings
4. Set source to `/docs` folder
5. Access at: `https://kosikowski.github.io/secure-store-kt/`

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
 * All data is encrypted using AES-256-GCM with keys protected by Android Keystore.
 *
 * ## Thread Safety
 * All operations are thread-safe and can be called from multiple threads.
 *
 * ## Usage
 * ```kotlin
 * val storage = SecureStorageImpl(context)
 * storage.putString("key", "value")
 * val value = storage.getString("key")
 * ```
 *
 * @param context Android application context
 * @param ioDispatcher Coroutine dispatcher for IO operations (defaults to Dispatchers.IO)
 * @throws IllegalStateException if Tink initialization fails
 *
 * @see SecureStorage
 */
class SecureStorageImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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


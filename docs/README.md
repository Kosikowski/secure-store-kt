# 📚 Documentation Overview

This project has been reorganized with comprehensive documentation.

## 📁 Structure

```
SecureStore/
├── README.md              # Main project documentation
├── LICENSE                # Apache 2.0 License
└── docs/                  # All other documentation
    ├── DOCUMENTATION.md   # 📖 How to generate API docs
    ├── MODULE.md          # Dokka module documentation
    ├── CHANGELOG.md       # Version history
    ├── QUICKSTART.md      # Getting started guide
    ├── EXAMPLES.md        # Usage examples
    ├── CONTRIBUTING.md    # Contribution guidelines
    ├── SECURITY.md        # Security information
    ├── TESTING.md         # Testing guide
    ├── PUBLISHING.md      # Publishing to Maven Central
    └── README.md          # This overview
```

## 🚀 Quick Links

- **[README.md](../README.md)** - Start here
- **[Quick Start](QUICKSTART.md)** - Get up and running
- **[Examples](EXAMPLES.md)** - See code examples
- **[API Documentation](DOCUMENTATION.md)** - Learn how to generate docs

## 📖 Generating API Documentation

SecureStore uses **Dokka** - the official Kotlin documentation engine.

### Generate HTML Documentation

```bash
./gradlew dokkaHtml
```

Opens: `build/dokka/html/index.html`

### Generate Javadoc

```bash
./gradlew dokkaJavadoc
```

Output: `build/dokka/javadoc/`

### View Documentation

```bash
# macOS
./gradlew dokkaHtml && open build/dokka/html/index.html

# Linux
./gradlew dokkaHtml && xdg-open build/dokka/html/index.html

# Windows
./gradlew dokkaHtml && start build/dokka/html/index.html
```

## 📦 Documentation Features

### ✅ What's Included

- **KDoc Comments** - All public APIs are documented
- **Code Examples** - Real-world usage patterns
- **Source Links** - Link from docs to GitHub source
- **Search** - Full-text search in HTML docs
- **Multiple Formats** - HTML and Javadoc
- **Maven Central Ready** - Javadoc JAR for publishing

### 📚 Available Guides

| Guide | Description |
|-------|-------------|
| [QUICKSTART.md](QUICKSTART.md) | Get started in 5 minutes |
| [EXAMPLES.md](EXAMPLES.md) | Comprehensive code examples |
| [TESTING.md](TESTING.md) | How to write and run tests |
| [SECURITY.md](SECURITY.md) | Security model and guarantees |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [PUBLISHING.md](PUBLISHING.md) | Publish to Maven Central |
| [DOCUMENTATION.md](DOCUMENTATION.md) | Generate and manage docs |
| [CHANGELOG.md](CHANGELOG.md) | Version history |

## 🔧 Documentation Options

### Current Setup: Dokka ✅

- Official Kotlin documentation tool
- Multiple output formats (HTML, Javadoc, Markdown)
- GitHub integration
- Maven Central compatible
- Search functionality

### Alternative Options

1. **GitHub Pages** - Host docs online at `https://kosikowski.github.io/secure-store-kt/`
2. **Read the Docs** - Professional hosting with versioning
3. **Orchid** - Beautiful static sites with more customization
4. **GitBook** - Interactive documentation platform

### Setting Up GitHub Pages (Recommended)

1. Generate docs:
   ```bash
   ./gradlew dokkaHtml
   ```

2. Copy to docs folder:
   ```bash
   mkdir -p docs/api
   cp -r build/dokka/html docs/api/
   ```

3. Enable GitHub Pages:
   - Go to repository Settings → Pages
   - Set source to `/docs` folder
   - Save

4. Access at: `https://kosikowski.github.io/secure-store-kt/`

## 📝 Writing Documentation

### For Code (KDoc)

```kotlin
/**
 * Brief description.
 *
 * Detailed explanation with examples.
 *
 * @param name Parameter description
 * @return What the function returns
 * @throws Exception When this happens
 */
fun myFunction(name: String): Result
```

### For Guides (Markdown)

- Use clear headings
- Include code examples
- Add links to related docs
- Keep it concise


## 📖 Learn More

- [Dokka Documentation](https://kotlinlang.org/docs/dokka-introduction.html)
- [KDoc Syntax](https://kotlinlang.org/docs/kotlin-doc.html)
- [Markdown Guide](https://www.markdownguide.org/)

---

Made with ❤️ by [Mateusz Kosikowski](https://github.com/Kosikowski)


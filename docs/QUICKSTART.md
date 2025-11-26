# Quick Start Guide

## Available Gradle Tasks

### Build Tasks
```bash
./gradlew clean                    # Clean build directory
./gradlew build                    # Build the library
./gradlew assembleRelease          # Build release AAR
```

### Test Tasks
```bash
./gradlew test                     # Run unit tests
./gradlew connectedAndroidTest     # Run instrumented tests
```

### Publishing Tasks
```bash
./gradlew publishToMavenLocal              # Publish to local Maven
./gradlew publishReleasePublicationToSonatypeRepository  # Publish to Maven Central
```

## Documentation

- **README.md** - Main library documentation
- **PROJECT_SUMMARY.md** - Complete project overview
- **PUBLISHING.md** - How to publish to Maven Central
- **CONTRIBUTING.md** - How to contribute
- **EXAMPLES.md** - Code examples
- **SECURITY.md** - Security policy


## Quick Test

To verify the library works locally:

```bash
# Publish to local Maven
./gradlew publishToMavenLocal

# In another project, add to build.gradle.kts:
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.github.kosikowski:securestore:1.0.0")
}
```

## Support

For questions or issues:
- See documentation in the root directory
- Check TROUBLESHOOTING section in README.md
- Review EXAMPLES.md for usage patterns


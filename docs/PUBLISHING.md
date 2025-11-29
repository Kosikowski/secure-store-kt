# Publishing to Maven Central

This guide explains how to publish **SecureStore** to Maven Central using the **Vanniktech Maven Publish Plugin** with automatic release to the Central Portal.

## Overview

The project uses the [Vanniktech Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) which provides:
- ✅ Native Maven Central Portal support
- ✅ Automatic release (no manual "Publish" button click)
- ✅ In-memory GPG signing (no keyring file needed)
- ✅ Single command publishing

## Prerequisites

### 1. Maven Central Portal Account

1. Create an account at <https://central.sonatype.com/>
2. Verify the `io.github.kosikowski` namespace appears in your account

### 2. Generate Portal User Token

1. Log in to <https://central.sonatype.com/>
2. Click your username → **View Account**
3. Click **Generate User Token**
4. Save the generated **username** and **password**
   - These are NOT your account credentials
   - They are a token pair for API access

### 3. GPG Key Setup

#### Generate a GPG Key (if needed)

```bash
# Generate a new key
gpg --gen-key

# List keys to find your KEY_ID
gpg --list-keys --keyid-format SHORT

# Example output:
# pub   rsa3072/ABCD1234 2024-11-29 [SC]
#       1234567890ABCDEF1234567890ABCDEF12345678
# uid         [ultimate] Your Name <email@example.com>
#
# KEY_ID = ABCD1234 (last 8 characters)
```

#### Export Key for CI

```bash
# Export private key as base64 (for SIGNING_KEY secret)
gpg --export-secret-keys ABCD1234 | base64 | tr -d '\n' > signing-key.txt

# The content of signing-key.txt goes into SIGNING_KEY secret
```

#### Publish Public Key

```bash
# Required for Maven Central to verify signatures
gpg --keyserver keys.openpgp.org --send-keys ABCD1234
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234
```

### 4. GitHub Secrets

Add these secrets to your repository (`Settings → Secrets → Actions`):

| Secret | Description | Example |
|--------|-------------|---------|
| `CENTRAL_USERNAME` | Portal token username | `abc123` |
| `CENTRAL_TOKEN` | Portal token password | `xyz789...` |
| `SIGNING_KEY` | Base64-encoded GPG private key | `LS0tLS1CRUdJTi...` |
| `SIGNING_KEY_ID` | Last 8 chars of GPG key ID | `ABCD1234` |
| `SIGNING_PASSWORD` | GPG key passphrase | `YourPassphrase` |

---

## Publishing

### Automatic Publishing (Recommended)

When you create a GitHub Release, the CI workflow automatically:
1. Builds the library
2. Signs all artifacts
3. Uploads to Maven Central Portal
4. Automatically releases (no manual click needed!)

#### Steps:

1. **Update version** in `build.gradle.kts`:
   ```kotlin
   version = "1.0.0"
   ```

2. **Commit and push**:
   ```bash
   git add build.gradle.kts docs/CHANGELOG.md
   git commit -m "Release 1.0.0"
   git push
   ```

3. **Create a tag**:
   ```bash
   git tag -a v1.0.0 -m "Release 1.0.0"
   git push origin v1.0.0
   ```

4. **Create GitHub Release**:
   - Go to <https://github.com/Kosikowski/secure-store-kt/releases/new>
   - Select tag `v1.0.0`
   - Add release notes from CHANGELOG.md
   - Click **Publish release**

5. **Wait ~20-45 minutes** for Maven Central sync

6. **Verify** at:
   - <https://central.sonatype.com/artifact/io.github.kosikowski/securestore>
   - <https://repo1.maven.org/maven2/io/github/kosikowski/securestore/>

### Local Testing (No Signing)

For quick local testing without signing:

```bash
./gradlew publishToMavenLocal
```

This publishes to `~/.m2/repository/io/github/kosikowski/securestore/` **without signing**.

You can then test the library in another project:

```kotlin
// In the test project's settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Add this
        mavenCentral()
    }
}

// In build.gradle.kts
dependencies {
    implementation("io.github.kosikowski:securestore:1.0.0")
}
```

### Manual Publishing (Full)

For full local publishing to Maven Central (with signing), configure `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=YOUR_PORTAL_TOKEN_USERNAME
mavenCentralPassword=YOUR_PORTAL_TOKEN_PASSWORD
signingInMemoryKey=BASE64_ENCODED_GPG_KEY
signingInMemoryKeyId=ABCD1234
signingInMemoryKeyPassword=YOUR_GPG_PASSPHRASE
```

Then run:

```bash
./gradlew publishAndReleaseToMavenCentral
```

---

## Available Gradle Tasks

| Task | Description |
|------|-------------|
| `publishToMavenLocal` | Publish to local Maven (~/.m2) **without signing** |
| `publishAllPublicationsToMavenCentralRepository` | Upload to Central Portal (staging, requires signing) |
| `publishAndReleaseToMavenCentral` | Upload AND auto-release to Maven Central (requires signing) |

---

## Timeline

| Step | Time | Status |
|------|------|--------|
| Create GitHub Release | 0 min | Manual |
| CI builds & signs | ~3-5 min | Automatic |
| CI publishes to Central | ~1-2 min | Automatic |
| Central Portal validates | ~2-5 min | Automatic |
| Central Portal releases | ~1 min | **Automatic** ✨ |
| Maven Central sync | ~15-30 min | Automatic |
| **Total** | **~20-45 min** | Fully automatic! |

---

## Troubleshooting

### "signMavenPublication FAILED" locally

This happens when running tasks that require signing without credentials configured.

**Solution**: Use `publishToMavenLocal` for local testing (no signing needed):
```bash
./gradlew publishToMavenLocal
```

Or configure signing in `~/.gradle/gradle.properties` (see Manual Publishing section above).

### "401 Unauthorized"

- Verify `CENTRAL_USERNAME` and `CENTRAL_TOKEN` secrets
- Ensure you're using the Portal token (not account credentials)
- Check namespace ownership at <https://central.sonatype.com/>

### "Signing failed" in CI

- Verify `SIGNING_KEY` is base64-encoded correctly
- Check `SIGNING_KEY_ID` matches your key (last 8 chars)
- Ensure `SIGNING_PASSWORD` is correct

### "Validation failed"

- Check POM has all required fields (name, description, url, licenses, developers, scm)
- Ensure public GPG key is published to keyservers
- Review error details in GitHub Actions log

### "Artifact not appearing"

- Maven Central sync can take up to 2 hours
- Verify publication succeeded in GitHub Actions
- Check <https://status.maven.org/> for outages

---

## Useful Links

- [Maven Central Portal](https://central.sonatype.com/)
- [Vanniktech Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/)
- [Maven Central Search](https://search.maven.org/)
- [GPG Quick Start](https://central.sonatype.org/publish/requirements/gpg/)

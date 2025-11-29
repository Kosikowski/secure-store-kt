# Publishing to Maven Central

This guide explains how to publish **SecureStore** to Maven Central using the **Central Portal** (OSSRH was retired on June 30, 2025).

## Prerequisites

### 1. Maven Central Portal Account

1. Create an account at <https://central.sonatype.com/>.
2. Verify the `io.github.kosikowski` namespace appears in your account.
   - If you had an old OSSRH account, log in with the same credentials.
   - If the namespace is missing, contact [Central Support](https://central.sonatype.org/support/).

### 2. Generate Portal User Token

1. Log in to <https://central.sonatype.com/>.
2. Click your username in the top right → **View Account**.
3. Click **Generate User Token**.
4. Save the generated **username** and **password** securely.
   - Note: This generates a new username/password pair (not your account password)

### 3. GPG Key

You need a GPG key to sign artifacts.

```bash
# Generate a key pair
gpg --gen-key

# List keys to find your KEY_ID
gpg --list-keys

# Publish your public key to a keyserver
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 4. Gradle Properties

Configure signing and Central Portal credentials in `~/.gradle/gradle.properties`:

```properties
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_GPG_PASSWORD
signing.secretKeyRingFile=/Users/YOUR_USERNAME/.gnupg/secring.gpg

centralUsername=YOUR_PORTAL_USERNAME
centralToken=YOUR_PORTAL_TOKEN
```

> **Note:** The "Generate User Token" feature creates a **username/password pair** specifically for publishing. Use these credentials (not your account login). The property is named `centralToken` but it's actually the password from the token pair.

---

## Publishing a Release

### 1. Set the Version

In the library `build.gradle.kts` set a **non-SNAPSHOT** version:

```kotlin
version = "1.0.0"
```

### 2. Build and Test

Run a clean build and all tests:

```bash
./gradlew clean build connectedAndroidTest
```

All tasks should succeed before publishing.

### 3. Publish to Central Portal

```bash
./gradlew publishReleaseToCentralRepository
```

This uploads and **automatically publishes** the artifacts to Maven Central.

### 4. Verify Publication

After the publish task completes successfully, the artifacts will be automatically validated and published to Maven Central (usually within 15–30 minutes).

### 5. Tag the Release in Git

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

### 6. Create a GitHub Release

1. Go to **Releases** in your GitHub repository.
2. Click **Draft a new release**.
3. Select tag `v1.0.0`.
4. Copy release notes from `docs/CHANGELOG.md`.
5. Publish the release.

---


## Verification

After a release, verify that the artifact is available:

1. **Maven Central Search**  
   <https://search.maven.org/>

2. **Direct URL**  
   <https://repo1.maven.org/maven2/io/github/kosikowski/securestore/>

3. **Test in a sample project**

   ```kotlin
   dependencies {
       implementation("io.github.kosikowski:securestore:1.0.0")
   }
   ```

---

## Troubleshooting

### "401 Unauthorized"

- Check `centralUsername` / `centralToken` in `~/.gradle/gradle.properties`.
- Ensure you generated a Portal user token (username/password pair from Portal, not your account credentials).
- Verify your account has permissions for `io.github.kosikowski` namespace.

### "No valid signing key"

- Verify `signing.keyId`, `signing.password`, and `signing.secretKeyRingFile`.
- Confirm the key ID matches `gpg --list-keys` output.

### "Could not find artifact" on Maven Central

- Maven Central sync can take up to 2 hours.
- Ensure the deployment was **Published** in the Central Portal.

### "POM validation failed"

Ensure the `pom` block in `build.gradle.kts` contains at least:

- `name`
- `description`
- `url`
- `licenses`
- `developers`
- `scm`

---

## Useful Links

- [Maven Central Portal](https://central.sonatype.com/)
- [Central Portal Guide](https://central.sonatype.org/publish/publish-portal-guide/)
- [Maven Central Search](https://search.maven.org/)
- [GPG Quick Start](https://central.sonatype.org/publish/requirements/gpg/)

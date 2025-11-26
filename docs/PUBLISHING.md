# Publishing to Maven Central

This guide explains how to publish **SecureStore** to Maven Central.

## Prerequisites

### 1. Sonatype OSSRH Account

1. Create an account at <https://s01.oss.sonatype.org/>.
2. Request access to the `io.github.kosikowski` group ID according to the
   [Sonatype OSSRH guide](https://central.sonatype.org/publish/publish-guide/).

### 2. GPG Key

You need a GPG key to sign artifacts.

```bash
# Generate a key pair
gpg --gen-key

# List keys to find your KEY_ID
gpg --list-keys

# Publish your public key to a keyserver
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 3. Gradle Properties

Configure signing and Sonatype credentials in `~/.gradle/gradle.properties`:

```properties
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_GPG_PASSWORD
signing.secretKeyRingFile=/Users/YOUR_USERNAME/.gnupg/secring.gpg

ossrhUsername=YOUR_SONATYPE_USERNAME
ossrhPassword=YOUR_SONATYPE_PASSWORD
```

> Note: The `signing.secretKeyRingFile` path must point to your local GPG keyring.

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

### 3. Publish to Sonatype Staging

```bash
./gradlew publishReleasePublicationToSonatypeRepository
```

This uploads the artifacts to Sonatype's **staging** repository on `s01.oss.sonatype.org`.

### 4. Close and Release Staging Repository

1. Go to <https://s01.oss.sonatype.org/> and log in.
2. In the left menu, select **Staging Repositories**.
3. Find the repository starting with `io.github.kosikowski-...`.
4. Click **Close** and wait for validation to finish.
5. If validation passes, click **Release**.

After release, Maven Central will sync the artifacts (usually within 30–120 minutes).

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

## Snapshot Releases

For development versions you can publish **snapshots**.

### 1. Set a Snapshot Version

```kotlin
version = "1.1.0-SNAPSHOT"
```

### 2. Publish Snapshot

```bash
./gradlew publishReleasePublicationToSonatypeRepository
```

Snapshots are published to the Sonatype snapshots repository:

- <https://s01.oss.sonatype.org/content/repositories/snapshots/>

### 3. Consuming Snapshots

In a client project:

```kotlin
repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencies {
    implementation("io.github.kosikowski:securestore:1.1.0-SNAPSHOT")
}
```

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

- Check `ossrhUsername` / `ossrhPassword` in `~/.gradle/gradle.properties`.
- Ensure your Sonatype account has permissions for `io.github.kosikowski`.

### "No valid signing key"

- Verify `signing.keyId`, `signing.password`, and `signing.secretKeyRingFile`.
- Confirm the key ID matches `gpg --list-keys` output.

### "Could not find artifact" on Maven Central

- Maven Central sync can take up to 2 hours.
- Ensure the staging repository was **Released**, not only **Closed**.

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

- [Maven Central](https://search.maven.org/)
- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [GPG Quick Start](https://central.sonatype.org/publish/requirements/gpg/)

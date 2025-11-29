# Automatic Publishing to Maven Central

## Overview

This project uses **fully automatic publishing** to Maven Central. When you create a GitHub Release:

1. ✅ CI builds the library
2. ✅ CI signs all artifacts with GPG
3. ✅ CI uploads to Maven Central Portal
4. ✅ **Portal automatically releases** (no manual click!)
5. ✅ Library is live on Maven Central in ~20-45 minutes

**No manual steps required after creating the GitHub Release!**

## How It Works

The project uses the [Vanniktech Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) configured with:

```kotlin
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    // ...
}
```

The `automaticRelease = true` flag tells the plugin to automatically publish after validation passes.

## Release Process

### 1. Update Version

Edit `build.gradle.kts`:

```kotlin
version = "1.1.0"  // New version
```

### 2. Update Changelog

Add entry to `docs/CHANGELOG.md`.

### 3. Commit and Tag

```bash
git add build.gradle.kts docs/CHANGELOG.md
git commit -m "Release 1.1.0"
git push

git tag -a v1.1.0 -m "Release 1.1.0"
git push origin v1.1.0
```

### 4. Create GitHub Release

1. Go to <https://github.com/Kosikowski/secure-store-kt/releases/new>
2. Select tag: `v1.1.0`
3. Title: `v1.1.0`
4. Description: Copy from CHANGELOG.md
5. Click **Publish release**

### 5. Done! 🎉

That's it! Wait ~20-45 minutes and your library will be live on Maven Central.

## Monitoring

### GitHub Actions

Watch the workflow progress:
- <https://github.com/Kosikowski/secure-store-kt/actions>

### Central Portal (Optional)

You can optionally monitor at <https://central.sonatype.com/deployments>:
- **Validating** → Checking artifacts
- **Publishing** → Auto-releasing
- **Published** → Done!

### Maven Central

After ~15-30 minutes:
- <https://search.maven.org/artifact/io.github.kosikowski/securestore>

## Comparison

### Before (Manual)

```
1. Create GitHub Release
2. Wait for CI
3. Log in to Central Portal
4. Find deployment
5. Wait for validation
6. Click "Publish" button ❌ Manual step
7. Wait for sync
```

### Now (Automatic)

```
1. Create GitHub Release
2. Wait ~20-45 minutes ✅ All automatic
3. Library is live! 🎉
```

## Required Secrets

These must be configured in GitHub repository settings:

| Secret | Description |
|--------|-------------|
| `CENTRAL_USERNAME` | Portal token username |
| `CENTRAL_TOKEN` | Portal token password |
| `SIGNING_KEY` | Base64-encoded GPG private key |
| `SIGNING_KEY_ID` | Last 8 chars of GPG key ID |
| `SIGNING_PASSWORD` | GPG key passphrase |

## Troubleshooting

### Build fails

Check GitHub Actions log for specific errors.

### Publishing fails

Common causes:
- Invalid credentials (check secrets)
- GPG signing issues
- Duplicate version (can't republish same version)

### Artifact not appearing

- Wait up to 2 hours for sync
- Check <https://status.maven.org/>

## Disabling Automatic Release

If you ever need manual control:

```kotlin
// build.gradle.kts
publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
```

Then you'll need to click "Publish" in the Portal UI after validation.

---

See also:
- `docs/PUBLISHING.md` - Full publishing guide
- `docs/RELEASE_CHECKLIST.md` - Release checklist

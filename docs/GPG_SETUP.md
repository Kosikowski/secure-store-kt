# GPG Signing Setup Guide

This guide explains how to set up GPG signing for publishing to Maven Central.

## Why GPG Signing is Required

Maven Central requires all published artifacts to be cryptographically signed with GPG (GNU Privacy Guard). This ensures the integrity and authenticity of your library.

## Step-by-Step Setup

### 1. Install GPG

**macOS:**
```bash
brew install gnupg
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get install gnupg
```

**Verify installation:**
```bash
gpg --version
```

### 2. Generate a GPG Key Pair

```bash
gpg --gen-key
```

**You'll be prompted for:**

1. **Type of key**: Press Enter (default: RSA and RSA)
2. **Key size**: Enter `3072` or `4096` (recommended)
3. **Expiration**: 
   - `0` = key does not expire (easier but less secure)
   - `1y` = expires in 1 year (more secure, need to renew)
4. **Real name**: Your full name (e.g., "Mateusz Kosikowski")
5. **Email address**: Your email (e.g., "mateusz.kosikowski@gmail.com")
6. **Comment**: Optional (can leave empty)
7. **Passphrase**: **IMPORTANT** - Choose a strong password and remember it!
   - This becomes your `SIGNING_PASSWORD`
   - You'll need it every time you sign artifacts

**Example output:**
```
gpg: key ABCD1234ABCD1234 marked as ultimately trusted
gpg: revocation certificate stored as '/Users/you/.gnupg/openpgp-revocs.d/...'
public and secret key created and signed.

pub   rsa3072 2024-11-29 [SC]
      ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234
uid                      Your Name <your.email@example.com>
sub   rsa3072 2024-11-29 [E]
```

### 3. List Your Keys

```bash
gpg --list-keys
```

**Output:**
```
/Users/you/.gnupg/pubring.kbx
--------------------------------
pub   rsa3072 2024-11-29 [SC]
      ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234
uid           [ultimate] Your Name <your.email@example.com>
sub   rsa3072 2024-11-29 [E]
```

**Important values:**
- **Full Key ID**: `ABCD1234ABCD1234ABCD1234ABCD1234ABCD1234` (40 characters)
- **Short Key ID**: `ABCD1234` (last 8 characters) ← This is your `SIGNING_KEY_ID`

### 4. Export Your Private Key (for GitHub Actions)

```bash
# Replace ABCD1234 with YOUR key ID (last 8 chars)
gpg --export-secret-keys ABCD1234 | base64 > signing-key.txt
```

**On macOS** (to avoid line breaks):
```bash
gpg --export-secret-keys ABCD1234 | base64 | tr -d '\n' > signing-key.txt
```

**The file `signing-key.txt` now contains your `SIGNING_KEY`**

You can verify it was created:
```bash
ls -lh signing-key.txt
# Should be around 4-6 KB
```

### 5. Publish Your Public Key

Maven Central requires your public key to be available on a keyserver.

```bash
# Publish to keys.openpgp.org (recommended)
gpg --keyserver keys.openpgp.org --send-keys ABCD1234

# Optionally, publish to other keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234
gpg --keyserver pgp.mit.edu --send-keys ABCD1234
```

**Verify it was published:**
```bash
gpg --keyserver keys.openpgp.org --recv-keys ABCD1234
```

### 6. Collect Your Credentials

You now have all 3 signing credentials:

| Credential | How to Get It | Example Value |
|------------|---------------|---------------|
| `SIGNING_KEY_ID` | Last 8 chars from `gpg --list-keys` | `ABCD1234` |
| `SIGNING_KEY` | Content of `signing-key.txt` | `LS0tLS1CRUdJTi...` (very long) |
| `SIGNING_PASSWORD` | Passphrase from step 2 | `YourSecretPassword123` |

## Add to GitHub Secrets

1. Go to your repository: <https://github.com/Kosikowski/secure-store-kt>
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** for each:

**Add `SIGNING_KEY_ID`:**
- Name: `SIGNING_KEY_ID`
- Secret: `ABCD1234` (your actual key ID)

**Add `SIGNING_KEY`:**
- Name: `SIGNING_KEY`
- Secret: Copy the **entire content** of `signing-key.txt`
  ```bash
  cat signing-key.txt | pbcopy  # macOS - copies to clipboard
  cat signing-key.txt           # Linux - copy the output manually
  ```

**Add `SIGNING_PASSWORD`:**
- Name: `SIGNING_PASSWORD`
- Secret: Your GPG passphrase from step 2

## Add to Local Gradle (for local publishing)

Edit `~/.gradle/gradle.properties`:

```properties
signing.keyId=ABCD1234
signing.password=YourSecretPassword123
signing.secretKeyRingFile=/Users/YOUR_USERNAME/.gnupg/secring.gpg
```

**Note:** On newer GPG versions, you may need to export the keyring:

```bash
gpg --export-secret-keys > ~/.gnupg/secring.gpg
```

## Test Your Setup

### Test Locally

```bash
# This will use your local GPG setup
./gradlew signReleasePublication

# If successful, you'll see:
# BUILD SUCCESSFUL
```

### Test on GitHub Actions

Create a test tag and release to trigger the workflow:

```bash
git tag -a v0.0.1-test -m "Test signing"
git push origin v0.0.1-test
```

Then create a release from this tag on GitHub and watch the Actions log.

## Troubleshooting

### "gpg: signing failed: Inappropriate ioctl for device"

```bash
export GPG_TTY=$(tty)
```

Add to `~/.zshrc` or `~/.bashrc`:
```bash
echo 'export GPG_TTY=$(tty)' >> ~/.zshrc
```

### "No secret key"

Make sure you're using the correct key ID:
```bash
gpg --list-secret-keys
```

### "Invalid passphrase"

The `SIGNING_PASSWORD` must match the passphrase you set in step 2. Try signing locally to verify:
```bash
echo "test" | gpg --clearsign --local-user ABCD1234
# Enter your passphrase - if it works, that's the correct password
```

### "Cannot find secring.gpg"

Export your keyring:
```bash
gpg --export-secret-keys > ~/.gnupg/secring.gpg
```

### GitHub Actions: "gpg: decryption failed: No secret key"

- Verify `SIGNING_KEY` secret contains the full base64-encoded key
- Ensure no line breaks (use `tr -d '\n'` when creating)
- Check the secret wasn't truncated when pasting

## Security Best Practices

1. **Never commit your private key to Git**
   ```bash
   # Delete the exported key file after adding to GitHub
   rm signing-key.txt
   ```

2. **Use a strong passphrase**
   - At least 16 characters
   - Mix of letters, numbers, symbols
   - Store in a password manager

3. **Back up your key**
   ```bash
   # Export both public and private keys to a secure location
   gpg --export-secret-keys ABCD1234 > gpg-private-backup.key
   gpg --export ABCD1234 > gpg-public-backup.key
   
   # Store these files in a secure location (encrypted USB, password manager)
   ```

4. **Create a revocation certificate** (in case key is compromised)
   ```bash
   gpg --gen-revoke ABCD1234 > revoke-ABCD1234.asc
   # Store this securely - you'll need it to revoke the key if compromised
   ```

## Key Management

### List all keys
```bash
gpg --list-keys           # Public keys
gpg --list-secret-keys    # Private keys
```

### Delete a key
```bash
gpg --delete-secret-keys ABCD1234  # Delete private key first
gpg --delete-keys ABCD1234         # Then delete public key
```

### Extend expiration
```bash
gpg --edit-key ABCD1234
gpg> expire
gpg> save
```

### Export public key
```bash
gpg --armor --export ABCD1234
```

## Summary

**What you need to remember:**
1. Your **key ID** (last 8 chars) → `SIGNING_KEY_ID`
2. Your **passphrase** → `SIGNING_PASSWORD`
3. Your **exported private key** (base64) → `SIGNING_KEY`

**Setup checklist:**
- [x] GPG installed
- [x] Key pair generated
- [x] Private key exported to base64
- [x] Public key published to keyserver
- [x] All 3 secrets added to GitHub
- [x] Local `~/.gradle/gradle.properties` configured
- [x] Private key file (`signing-key.txt`) deleted
- [x] Keys backed up securely

---

For more information:
- [Maven Central Requirements](https://central.sonatype.org/publish/requirements/gpg/)
- [GPG Documentation](https://gnupg.org/documentation/)


# Git Hooks Setup

This project includes git hooks to ensure code quality before pushing.

## What's Included

- **pre-push**: Runs `ktlintCheck` before allowing a push

## Installation

### Option 1: Using Gradle (Recommended)

```bash
./gradlew installGitHooks
```

### Option 2: Using Shell Script (Unix/Mac/Linux)

```bash
chmod +x scripts/install-hooks.sh
./scripts/install-hooks.sh
```

### Option 3: Using PowerShell (Windows)

```powershell
.\scripts\install-hooks.ps1
```

## What Happens

When you run `git push`, the pre-push hook will:

1. Run `./gradlew ktlintCheck`
2. If ktlint finds issues, the push will be blocked
3. You'll see error messages indicating what needs to be fixed

## Bypassing Hooks

If you need to bypass the hook (not recommended), use:

```bash
git push --no-verify
```

## Fixing ktlint Issues

If ktlint check fails, you can auto-fix most issues:

```bash
./gradlew ktlintFormat
```

Then review the changes and commit them.

## Manual Hook Installation

If you prefer to install hooks manually:

```bash
# Copy the hook
cp .githooks/pre-push .git/hooks/pre-push

# Make it executable
chmod +x .git/hooks/pre-push
```

## Troubleshooting

### Hook not running

1. Verify the hook is installed:
   ```bash
   ls -la .git/hooks/pre-push
   ```

2. Check if it's executable:
   ```bash
   chmod +x .git/hooks/pre-push
   ```

### Hook fails but code is fine

- Make sure you're in the project root when pushing
- Ensure Gradle wrapper is executable: `chmod +x gradlew`
- Try running ktlint manually: `./gradlew ktlintCheck`

### Want to disable hooks temporarily

```bash
# Skip hooks for one push
git push --no-verify

# Or uninstall hooks
rm .git/hooks/pre-push
```

## CI/CD

These hooks are for local development. CI/CD pipelines should also run:

```yaml
# GitHub Actions example
- name: Check code style
  run: ./gradlew ktlintCheck
```


# ktlint Configuration

This project uses [ktlint](https://ktlint.github.io/) for Kotlin code formatting and linting.

## Configuration

- **Plugin**: `org.jlleitschuh.gradle.ktlint` version `11.6.1`
- **ktlint Version**: `1.0.1`
- **Android Mode**: Enabled
- **Experimental Rules**: Enabled

## Available Tasks

### Check Code Style

```bash
# Check all Kotlin files for style violations
./gradlew ktlintCheck

# Check only main source files
./gradlew ktlintMainSourceSetCheck

# Check only test source files
./gradlew ktlintTestSourceSetCheck
```

### Auto-format Code

```bash
# Auto-format all Kotlin files
./gradlew ktlintFormat

# Format only main source files
./gradlew ktlintMainSourceSetFormat

# Format only test source files
./gradlew ktlintTestSourceSetFormat
```

### Generate Reports

```bash
# Generate HTML report
./gradlew ktlintCheck
# Report location: build/reports/ktlint/
```

## IDE Integration

### IntelliJ IDEA / Android Studio

1. Install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint)
2. Enable "Run ktlint on Save" in Settings → Tools → ktlint
3. The plugin will automatically format files on save

### VS Code

Install the [ktlint extension](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) and configure it to use ktlint.

## Pre-commit Hook (Optional)

To automatically format code before committing, add this to `.git/hooks/pre-commit`:

```bash
#!/bin/sh
./gradlew ktlintFormat
git add -u
```

Or use a tool like [pre-commit](https://pre-commit.com/):

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/pinterest/ktlint
    rev: 1.0.1
    hooks:
      - id: ktlint
```

## Configuration Files

- **`.editorconfig`**: Editor configuration for consistent formatting
- **`.ktlintignore`**: Files and directories to exclude from linting

## Disabled Rules

The following ktlint rules are disabled:
- `no-wildcard-imports` - Allows wildcard imports (useful for test files)

## CI Integration

Add ktlint check to your CI pipeline:

```yaml
# GitHub Actions example
- name: Check code style
  run: ./gradlew ktlintCheck
```

## Common Issues

### Issue: "Unused import" warnings

**Solution**: Run `./gradlew ktlintFormat` to auto-remove unused imports.

### Issue: "File must end with a newline"

**Solution**: ktlint requires files to end with a newline. Run `ktlintFormat` to fix.

### Issue: "Max line length exceeded"

**Solution**: The max line length is set to 120 characters in `.editorconfig`. Either:
- Break the line
- Or disable the rule for that specific line: `// ktlint-disable max-line-length`

## Resources

- [ktlint Documentation](https://ktlint.github.io/)
- [ktlint Rules](https://ktlint.github.io/#rules)
- [Gradle ktlint Plugin](https://github.com/JLLeitschuh/ktlint-gradle)


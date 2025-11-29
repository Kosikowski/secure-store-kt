# PowerShell script to install git hooks for SecureStore project
# This script copies hooks from .githooks/ to .git/hooks/

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$GitHooksDir = Join-Path $ProjectRoot ".git\hooks"
$GitHooksSourceDir = Join-Path $ProjectRoot ".githooks"

Write-Host "Installing git hooks for SecureStore..." -ForegroundColor Cyan
Write-Host ""

# Check if .git directory exists
if (-not (Test-Path (Join-Path $ProjectRoot ".git"))) {
    Write-Host "Error: .git directory not found. Are you in a git repository?" -ForegroundColor Red
    exit 1
}

# Check if .githooks directory exists
if (-not (Test-Path $GitHooksSourceDir)) {
    Write-Host "Error: .githooks directory not found." -ForegroundColor Red
    exit 1
}

# Create .git/hooks directory if it doesn't exist
if (-not (Test-Path $GitHooksDir)) {
    New-Item -ItemType Directory -Path $GitHooksDir -Force | Out-Null
}

# Copy hooks from .githooks/ to .git/hooks/
$HooksInstalled = 0
Get-ChildItem -Path $GitHooksSourceDir -File | ForEach-Object {
    $HookName = $_.Name
    $TargetHook = Join-Path $GitHooksDir $HookName
    
    # Copy the hook
    Copy-Item $_.FullName -Destination $TargetHook -Force
    
    Write-Host "✓ Installed hook: $HookName" -ForegroundColor Green
    $HooksInstalled++
}

if ($HooksInstalled -eq 0) {
    Write-Host "No hooks found in .githooks/ directory." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "✅ Successfully installed $HooksInstalled git hook(s)!" -ForegroundColor Green
Write-Host ""
Write-Host "Hooks will now run automatically on git operations." -ForegroundColor Cyan
Write-Host "To bypass a hook, use: git push --no-verify" -ForegroundColor Yellow


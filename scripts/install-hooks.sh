#!/bin/bash
#
# Script to install git hooks for SecureStore project
# This script copies hooks from .githooks/ to .git/hooks/

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GIT_HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
GITHOOKS_DIR="$PROJECT_ROOT/.githooks"

echo "${BLUE}Installing git hooks for SecureStore...${NC}"
echo ""

# Check if .git directory exists
if [ ! -d "$PROJECT_ROOT/.git" ]; then
    echo "${RED}Error: .git directory not found. Are you in a git repository?${NC}"
    exit 1
fi

# Check if .githooks directory exists
if [ ! -d "$GITHOOKS_DIR" ]; then
    echo "${RED}Error: .githooks directory not found.${NC}"
    exit 1
fi

# Create .git/hooks directory if it doesn't exist
mkdir -p "$GIT_HOOKS_DIR"

# Copy hooks from .githooks/ to .git/hooks/
HOOKS_INSTALLED=0
for hook in "$GITHOOKS_DIR"/*; do
    if [ -f "$hook" ]; then
        HOOK_NAME=$(basename "$hook")
        TARGET_HOOK="$GIT_HOOKS_DIR/$HOOK_NAME"
        
        # Copy the hook
        cp "$hook" "$TARGET_HOOK"
        
        # Make it executable
        chmod +x "$TARGET_HOOK"
        
        echo "${GREEN}✓ Installed hook: $HOOK_NAME${NC}"
        HOOKS_INSTALLED=$((HOOKS_INSTALLED + 1))
    fi
done

if [ $HOOKS_INSTALLED -eq 0 ]; then
    echo "${YELLOW}No hooks found in .githooks/ directory.${NC}"
    exit 1
fi

echo ""
echo "${GREEN}✅ Successfully installed $HOOKS_INSTALLED git hook(s)!${NC}"
echo ""
echo "${BLUE}Hooks will now run automatically on git operations.${NC}"
echo "${YELLOW}To bypass a hook, use: git push --no-verify${NC}"


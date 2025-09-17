#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[build] Building lsp module..."
"$ROOT/gradlew" :lsp:build

echo "[npm] Installing / building extension..."
pushd "$ROOT/vscode-extension" > /dev/null
npm install
npm run build
popd > /dev/null

echo "[launch] Opening VS Code (extension development)..."
# ensure 'code' CLI is available on PATH
if ! command -v code >/dev/null 2>&1; then
  echo "Error: 'code' CLI not found. Install it from VS Code: Command Palette -> 'Shell Command: Install 'code' command in PATH'"
  exit 2
fi

# Launch VS Code with the extension development path pointed at vscode-extension
code --extensionDevelopmentPath="$ROOT/vscode-extension" "$ROOT"
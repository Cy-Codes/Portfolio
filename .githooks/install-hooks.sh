#!/usr/bin/env bash
# ============================================================================
# Installs Portfolio Git pre-commit hooks for this repository.
# ============================================================================
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly HOOK_SOURCE="${ROOT_DIR}/.githooks/pre-commit"
readonly FORMATTER_SCRIPT="${ROOT_DIR}/.githooks/clean_and_format.py"

chmod +x "${HOOK_SOURCE}" "${FORMATTER_SCRIPT}"
git -C "${ROOT_DIR}" config core.hooksPath .githooks

echo "Installed Portfolio Git hooks via core.hooksPath=.githooks"

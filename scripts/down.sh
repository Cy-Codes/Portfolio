#!/usr/bin/env bash
# ============================================================================
# Usage: ./scripts/down.sh [--volumes]
# Stops and removes the Portfolio Docker Compose containers idempotently.
# ============================================================================
set -euo pipefail

# shellcheck source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

readonly ROOT_DIR="$(repo_root)"

main() {
    local remove_volumes="false"

    for arg in "$@"; do
        case "${arg}" in
            --volumes | -v)
                remove_volumes="true"
                ;;
            *)
                die "Unknown argument '${arg}'. Usage: ./scripts/down.sh [--volumes]"
                ;;
        esac
    done

    load_env "${ROOT_DIR}"

    local down_args=(--profile deps --profile local --profile test down --remove-orphans)
    if [ "${remove_volumes}" = "true" ]; then
        down_args+=(--volumes)
    fi

    echo "==> Stopping Portfolio Compose stack..."
    compose_cmd "${ROOT_DIR}" "${down_args[@]}"
    echo "==> Stack stopped."
}

main "$@"

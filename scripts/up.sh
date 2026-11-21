#!/usr/bin/env bash
# ============================================================================
# Usage: ./scripts/up.sh [deps|local|test] [--seed]
# Starts the Portfolio Docker Compose stack and optionally seeds dev data.
# ============================================================================
set -euo pipefail

# shellcheck source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

readonly ROOT_DIR="$(repo_root)"
readonly SEED_SCRIPT_REL="server/server-db/db/scripts/01_seed_dev_data.sql"

main() {
    local profile="deps"
    local seed_enabled="false"

    for arg in "$@"; do
        case "${arg}" in
            deps | local | test)
                profile="${arg}"
                ;;
            --seed)
                seed_enabled="true"
                ;;
            *)
                die "Unknown argument '${arg}'. Usage: ./scripts/up.sh [deps|local|test] [--seed]"
                ;;
        esac
    done

    validate_profile "${profile}"
    load_env "${ROOT_DIR}"

    echo "==> Starting Portfolio Compose stack (profile=${profile})..."
    compose_cmd "${ROOT_DIR}" --profile "${profile}" up -d --wait postgres minio
    compose_cmd "${ROOT_DIR}" --profile "${profile}" up -d --no-deps db-migrator minio-init

    echo "==> Verifying one-shot Flyway migration and MinIO bucket initialization..."
    wait_for_service_exit_zero "${ROOT_DIR}" "${profile}" "db-migrator"
    wait_for_service_exit_zero "${ROOT_DIR}" "${profile}" "minio-init"

    if [ "${seed_enabled}" = "true" ]; then
        local migrator_user="${PORTFOLIO_MIGRATOR_USER:-portfolio_migrator}"
        local db_name="${POSTGRES_DB:-${DEFAULT_POSTGRES_DB}}"
        echo "==> Seeding development dataset as '${migrator_user}'..."
        run_psql_stdin "${ROOT_DIR}" "${migrator_user}" "${db_name}" "${ROOT_DIR}/${SEED_SCRIPT_REL}"
    fi

    if [ "${profile}" = "local" ]; then
        echo "==> Building and starting Ktor server-public and server-admin containers..."
        compose_cmd "${ROOT_DIR}" --profile "local" up -d --build --wait server-public server-admin
    fi

    echo "==> Stack is healthy and ready."
}

main "$@"

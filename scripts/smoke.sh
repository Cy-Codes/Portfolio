#!/usr/bin/env bash
# ============================================================================
# Usage: ./scripts/smoke.sh [deps|local] [--bruno]
# Verifies PostgreSQL schema/roles, MinIO bucket readiness, HTTP health probes,
# and optional Bruno API contract suites.
# ============================================================================
set -euo pipefail

# shellcheck source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

readonly ROOT_DIR="$(repo_root)"
readonly SCHEMA_TESTS_REL="server/server-db/db/tests/01_schema_structure_tests.sql"

# Side effect: Verifies Postgres Flyway history, schema structure tests, and MinIO bucket.
verify_deps_smoke() {
    local db_name="${POSTGRES_DB:-${DEFAULT_POSTGRES_DB}}"
    local superuser="${POSTGRES_SUPERUSER:-${DEFAULT_POSTGRES_SUPERUSER}}"
    local bucket_name="${BLOB_STORAGE_BUCKET:-${DEFAULT_MINIO_BUCKET}}"
    local minio_user="${MINIO_ROOT_USER:-${DEFAULT_MINIO_ROOT_USER}}"
    local minio_password="${MINIO_ROOT_PASSWORD:-${DEFAULT_MINIO_ROOT_PASSWORD}}"

    echo "==> Checking Flyway migration history in portfolio.flyway_schema_history..."
    local migration_count
    migration_count="$(run_psql_query "${ROOT_DIR}" "${superuser}" "${db_name}" \
        "SELECT count(*) FROM portfolio.flyway_schema_history WHERE success = true;")"
    if [ "${migration_count}" -lt 1 ]; then
        die "Expected at least one successful Flyway migration, found ${migration_count}."
    fi

    echo "==> Running PostgreSQL structural and least-privilege role assertions..."
    run_psql_stdin "${ROOT_DIR}" "${superuser}" "${db_name}" "${ROOT_DIR}/${SCHEMA_TESTS_REL}"

    echo "==> Verifying MinIO bucket '${bucket_name}' exists..."
    compose_cmd "${ROOT_DIR}" exec -T minio /bin/sh -c \
        "mc alias set smoke http://127.0.0.1:9000 '${minio_user}' '${minio_password}' >/dev/null && mc ls 'smoke/${bucket_name}' >/dev/null"

    echo "==> Deps smoke verification passed (${migration_count} successful Flyway record(s))."
}

# Side effect: Probes Ktor public/admin readiness endpoints and runs optional Bruno tests.
verify_local_smoke() {
    local run_bruno="$1"
    local host="${HOST_BIND_ADDRESS:-${DEFAULT_HOST_BIND_ADDRESS}}"
    local public_port="${PUBLIC_SERVER_PORT:-${DEFAULT_PUBLIC_PORT}}"
    local admin_port="${ADMIN_SERVER_PORT:-${DEFAULT_ADMIN_PORT}}"

    verify_deps_smoke

    echo "==> Probing public and admin health/readiness endpoints..."
    wait_for_url "http://${host}:${public_port}/health"
    wait_for_url "http://${host}:${public_port}/health/ready"
    wait_for_url "http://${host}:${admin_port}/health"
    wait_for_url "http://${host}:${admin_port}/health/ready"

    if [ "${run_bruno}" = "true" ]; then
        local bruno_dir="${ROOT_DIR}/bruno"
        if [ ! -d "${bruno_dir}" ]; then
            die "Bruno directory not found at ${bruno_dir}."
        fi
        require_command "bru" "Install @usebruno/cli (or bruno-cli via Homebrew) to execute --bruno."
        local admin_token
        admin_token="$("${ROOT_DIR}/scripts/dev-token.sh" owner)"
        local viewer_token
        viewer_token="$("${ROOT_DIR}/scripts/dev-token.sh" viewer)"
        echo "==> Running Bruno API suite..."
        (
            cd "${bruno_dir}" \
                && bru run -r --env Local \
                    --env-var "adminToken=${admin_token}" \
                    --env-var "viewerToken=${viewer_token}"
        )
    fi

    echo "==> Local smoke verification passed."
}

main() {
    local mode="deps"
    local run_bruno="false"

    for arg in "$@"; do
        case "${arg}" in
            deps | local)
                mode="${arg}"
                ;;
            --bruno)
                run_bruno="true"
                ;;
            *)
                die "Unknown argument '${arg}'. Usage: ./scripts/smoke.sh [deps|local] [--bruno]"
                ;;
        esac
    done

    load_env "${ROOT_DIR}"

    case "${mode}" in
        deps)
            verify_deps_smoke
            ;;
        local)
            verify_local_smoke "${run_bruno}"
            ;;
    esac
}

main "$@"

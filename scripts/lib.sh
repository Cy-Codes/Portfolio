#!/usr/bin/env bash
# ============================================================================
# Shared Shell Helpers for Portfolio Lifecycle & Verification Scripts
# Sourced by scripts/up.sh, scripts/down.sh, scripts/dev-token.sh, scripts/smoke.sh.
# Never executed directly.
# ============================================================================

readonly DEFAULT_HOST_BIND_ADDRESS="127.0.0.1"
readonly DEFAULT_POSTGRES_DB="portfolio"
readonly DEFAULT_POSTGRES_SUPERUSER="postgres"
readonly DEFAULT_PORTFOLIO_ADMIN_USER="portfolio_admin"
readonly DEFAULT_PUBLIC_PORT="8080"
readonly DEFAULT_ADMIN_PORT="8081"
readonly DEFAULT_MINIO_BUCKET="portfolio-assets"
readonly DEFAULT_MINIO_ROOT_USER="minioadmin"
readonly DEFAULT_MINIO_ROOT_PASSWORD="dev_minio_change_me"
readonly MAX_WAIT_ATTEMPTS=30
readonly RETRY_SLEEP_SECONDS=2

# Side effect: Prints an actionable error message and terminates with exit code 1.
die() {
    local message="$1"
    printf 'ERROR: %s\n' "${message}" >&2
    exit 1
}

# Pure query: Verifies an external CLI tool is available on PATH.
require_command() {
    local cmd_name="$1"
    local install_hint="$2"
    if ! command -v "${cmd_name}" >/dev/null 2>&1; then
        die "Required command '${cmd_name}' was not found. ${install_hint}"
    fi
}

# Pure query: Resolves the absolute repository root directory.
repo_root() {
    cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

# Side effect: Loads environment overrides from git-ignored .env if present.
load_env() {
    local root_dir="$1"
    local env_file="${root_dir}/.env"
    if [ -f "${env_file}" ]; then
        set -a
        # shellcheck disable=SC1090
        source "${env_file}"
        set +a
    fi
}

# Pure query: Validates a supported Docker Compose profile name.
validate_profile() {
    local profile="$1"
    case "${profile}" in
        deps | local | test)
            return 0
            ;;
        *)
            die "Unsupported profile '${profile}'. Allowed values: deps, local, test."
            ;;
    esac
}

# Pure query: Locates a working Docker CLI supporting `compose` subcommand.
resolve_docker_bin() {
    if docker compose version >/dev/null 2>&1; then
        echo "docker"
        return 0
    fi
    if [ -x "/Applications/Docker.app/Contents/Resources/bin/docker" ] \
        && /Applications/Docker.app/Contents/Resources/bin/docker compose version >/dev/null 2>&1; then
        echo "/Applications/Docker.app/Contents/Resources/bin/docker"
        return 0
    fi
    die "Docker Compose v2 is required. Start Docker Desktop or install the compose CLI plugin."
}

# Side effect: Runs docker compose scoped to the repository compose.yaml.
compose_cmd() {
    local root_dir="$1"
    shift
    local docker_bin
    docker_bin="$(resolve_docker_bin)"
    "${docker_bin}" compose --project-directory "${root_dir}" -f "${root_dir}/compose.yaml" "$@"
}

# Side effect: Polls a one-shot container until it exits or exceeds MAX_WAIT_ATTEMPTS.
wait_for_service_exit_zero() {
    local root_dir="$1"
    local profile="$2"
    local service_name="$3"
    local attempt=0

    # Bounded polling loop waiting for one-shot container exit status
    while [ "${attempt}" -lt "${MAX_WAIT_ATTEMPTS}" ]; do
        local container_id
        container_id="$(compose_cmd "${root_dir}" --profile "${profile}" ps -a -q "${service_name}" || true)"
        if [ -n "${container_id}" ]; then
            local docker_bin
            docker_bin="$(resolve_docker_bin)"
            local status
            status="$("${docker_bin}" inspect -f '{{.State.Status}}' "${container_id}")"
            if [ "${status}" = "exited" ]; then
                local exit_code
                exit_code="$("${docker_bin}" inspect -f '{{.State.ExitCode}}' "${container_id}")"
                if [ "${exit_code}" = "0" ]; then
                    return 0
                fi
                compose_cmd "${root_dir}" --profile "${profile}" logs "${service_name}" >&2 || true
                die "One-shot service '${service_name}' exited with status ${exit_code}."
            fi
        fi
        attempt=$((attempt + 1))
        sleep "${RETRY_SLEEP_SECONDS}"
    done

    die "Timed out waiting for one-shot service '${service_name}' to complete."
}

# Side effect: Executes a SQL file against the running Postgres container via stdin.
run_psql_stdin() {
    local root_dir="$1"
    local db_user="$2"
    local db_name="$3"
    local sql_file="$4"
    shift 4

    if [ ! -f "${sql_file}" ]; then
        die "SQL file not found: ${sql_file}"
    fi

    compose_cmd "${root_dir}" exec -T postgres \
        psql -v ON_ERROR_STOP=1 -U "${db_user}" -d "${db_name}" "$@" <"${sql_file}"
}

# Side effect: Executes a single inline SQL check inside the Postgres container.
run_psql_query() {
    local root_dir="$1"
    local db_user="$2"
    local db_name="$3"
    local query="$4"

    compose_cmd "${root_dir}" exec -T postgres \
        psql -v ON_ERROR_STOP=1 -tAc "${query}" -U "${db_user}" -d "${db_name}"
}

# Side effect: Polls an HTTP URL until it returns 2xx/3xx or times out.
wait_for_url() {
    local url="$1"
    local attempt=0
    require_command "curl" "Install curl to probe HTTP endpoints."

    # Bounded polling loop checking HTTP readiness endpoints
    while [ "${attempt}" -lt "${MAX_WAIT_ATTEMPTS}" ]; do
        if curl -fsS "${url}" >/dev/null 2>&1; then
            return 0
        fi
        attempt=$((attempt + 1))
        sleep "${RETRY_SLEEP_SECONDS}"
    done

    die "Timed out waiting for endpoint ${url} to become ready."
}

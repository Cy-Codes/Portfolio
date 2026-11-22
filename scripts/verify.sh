#!/usr/bin/env bash
# ============================================================================
# Usage: ./scripts/verify.sh [--fast | --full] [--module <name>]
# Single verification entrypoint for Portfolio scripts, hooks, builds,
# unit tests, Konsist architecture assertions, and integration suites.
# ============================================================================
set -euo pipefail

# shellcheck source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

readonly ROOT_DIR="$(repo_root)"
readonly HOOKS_JSON_REL=".agents/hooks.json"
readonly HOOKS_DIR_REL=".agents/hooks"
readonly ARCHITECTURE_TEST_FILTER="*ArchitectureFitnessTest*"
readonly INTEGRATION_TEST_FILTER="*PostgresAndGcsIntegrationTest*"
readonly -a SUPPORTED_MODULES=(
    "core"
    "presentation"
    "composeUi"
    "adminShared"
    "server-public"
    "server-admin"
    "server-db"
)

# Pure query: Prints CLI usage instructions.
print_usage() {
    echo "Usage: ./scripts/verify.sh [--fast | --full] [--module <name>]"
    echo "Supported --module values: ${SUPPORTED_MODULES[*]}"
}

# Pure query: Validates a supported module name for --module.
validate_module() {
    local candidate="$1"
    local mod
    for mod in "${SUPPORTED_MODULES[@]}"; do
        if [ "${candidate}" = "${mod}" ]; then
            return 0
        fi
    done
    die "Unsupported module '${candidate}'. Supported values: ${SUPPORTED_MODULES[*]}"
}

# Side effect: Verifies shell scripts, hook JSON, and hook Python syntax without mutating files.
verify_scripts_and_hooks() {
    require_command "python3" "Install Python 3 to validate hook JSON and Python scripts."

    local -a shell_files=()
    local script_path
    for script_path in "${ROOT_DIR}"/scripts/*.sh; do
        if [ -f "${script_path}" ]; then
            shell_files+=("${script_path}")
        fi
    done

    if [ "${#shell_files[@]}" -gt 0 ]; then
        echo "==> Checking shell script syntax (bash -n)..."
        for script_path in "${shell_files[@]}"; do
            bash -n "${script_path}"
        done

        if command -v shellcheck >/dev/null 2>&1; then
            echo "==> Running shellcheck on scripts/*.sh..."
            shellcheck -x "${shell_files[@]}"
        else
            echo "[skip] shellcheck not installed; skipped static shell lint."
        fi
    fi

    local hooks_json="${ROOT_DIR}/${HOOKS_JSON_REL}"
    if [ -f "${hooks_json}" ]; then
        echo "==> Validating ${HOOKS_JSON_REL}..."
        python3 -m json.tool "${hooks_json}" >/dev/null
    fi

    local hooks_dir="${ROOT_DIR}/${HOOKS_DIR_REL}"
    if [ -d "${hooks_dir}" ]; then
        local -a hook_py_files=()
        local py_path
        for py_path in "${hooks_dir}"/*.py; do
            if [ -f "${py_path}" ]; then
                hook_py_files+=("${py_path}")
            fi
        done
        if [ "${#hook_py_files[@]}" -gt 0 ]; then
            echo "==> Compiling Python hook scripts (${#hook_py_files[@]} file(s))..."
            python3 -m py_compile "${hook_py_files[@]}"
        fi
    fi
}

# Side effect: Runs JVM unit tests and Konsist architecture fitness assertions.
verify_jvm_tests() {
    local target_module="$1"
    local kotlin_bin="${ROOT_DIR}/kotlin"

    if [ ! -x "${kotlin_bin}" ]; then
        die "Executable Kotlin Toolchain wrapper not found at ${kotlin_bin}."
    fi

    if [ -n "${target_module}" ]; then
        echo "==> Running JVM unit tests for module '${target_module}'..."
        if [ "${target_module}" = "server-db" ]; then
            "${kotlin_bin}" test -m "server-db" -p jvm \
                --exclude-classes="${INTEGRATION_TEST_FILTER}"
        else
            "${kotlin_bin}" test -m "${target_module}" -p jvm
            echo "==> Running Konsist architecture fitness tests..."
            "${kotlin_bin}" test -m "server-db" -p jvm \
                --include-classes="${ARCHITECTURE_TEST_FILTER}"
        fi
        return 0
    fi

    local mod
    for mod in "${SUPPORTED_MODULES[@]}"; do
        echo "==> Running JVM unit tests for module '${mod}'..."
        if [ "${mod}" = "server-db" ]; then
            "${kotlin_bin}" test -m "server-db" -p jvm \
                --exclude-classes="${INTEGRATION_TEST_FILTER}"
        else
            "${kotlin_bin}" test -m "${mod}" -p jvm
        fi
    done
}

# Side effect: Runs full multiplatform build, iOS tests, and Docker Testcontainers integration suite.
verify_full_suite() {
    local kotlin_bin="${ROOT_DIR}/kotlin"
    local docker_bin
    docker_bin="$(resolve_docker_bin)"

    if ! "${docker_bin}" info >/dev/null 2>&1; then
        die "Docker daemon is not reachable. Start Docker Desktop before running ./scripts/verify.sh --full."
    fi

    echo "==> Running full multiplatform build (./kotlin build)..."
    "${kotlin_bin}" build

    verify_jvm_tests ""

    echo "==> Running iOS simulator unit tests (./kotlin test -m iosApp)..."
    "${kotlin_bin}" test -m iosApp

    echo "==> Running PostgreSQL & MinIO Testcontainers integration suite..."
    "${kotlin_bin}" test -m "server-db" -p jvm \
        --include-classes="${INTEGRATION_TEST_FILTER}"
}

main() {
    local mode="fast"
    local target_module=""

    while [ "$#" -gt 0 ]; do
        case "$1" in
            --fast)
                mode="fast"
                shift
                ;;
            --full)
                mode="full"
                shift
                ;;
            --module)
                if [ "$#" -lt 2 ]; then
                    die "Missing value for --module."
                fi
                validate_module "$2"
                target_module="$2"
                shift 2
                ;;
            --help | -h)
                print_usage
                return 0
                ;;
            *)
                die "Unknown argument '$1'. Usage: ./scripts/verify.sh [--fast | --full] [--module <name>]"
                ;;
        esac
    done

    if [ "${mode}" = "full" ] && [ -n "${target_module}" ]; then
        die "--module cannot be combined with --full; use --fast --module <name> instead."
    fi

    verify_scripts_and_hooks

    case "${mode}" in
        fast)
            verify_jvm_tests "${target_module}"
            echo "==> Fast verification passed."
            ;;
        full)
            verify_full_suite
            echo "==> Full verification passed."
            ;;
    esac
}

main "$@"

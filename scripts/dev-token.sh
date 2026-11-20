#!/usr/bin/env bash
# ============================================================================
# Usage: ./scripts/dev-token.sh [owner|viewer]
# Mints a local HS256 JWT for testing admin endpoints in dev/test profiles.
# Refuses to run when APP_ENV is set to prod or production.
# ============================================================================
set -euo pipefail

# shellcheck source=lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

readonly ROOT_DIR="$(repo_root)"
readonly DEFAULT_DEV_JWT_SECRET="dev-only-portfolio-hs256-secret-minimum-32-bytes"
readonly TOKEN_TTL_SECONDS=3600

# Pure query: Encodes stdin to unpadded URL-safe Base64.
base64url_encode() {
    openssl base64 -A | tr '+/' '-_' | tr -d '='
}

main() {
    local role="${1:-owner}"
    load_env "${ROOT_DIR}"

    local app_env="${APP_ENV:-dev}"
    if [ "${app_env}" = "prod" ] || [ "${app_env}" = "production" ]; then
        die "dev-token.sh is strictly prohibited in '${app_env}' environments."
    fi

    require_command "openssl" "Install OpenSSL to mint local HS256 dev tokens."

    local subject
    case "${role}" in
        owner)
            subject="dev-owner"
            ;;
        viewer)
            subject="dev-viewer"
            ;;
        *)
            die "Unsupported role '${role}'. Allowed values: owner, viewer."
            ;;
    esac

    local secret="${DEV_JWT_SECRET:-${DEFAULT_DEV_JWT_SECRET}}"
    local now_epoch
    now_epoch="$(date +%s)"
    local exp_epoch=$((now_epoch + TOKEN_TTL_SECONDS))

    local header_json='{"alg":"HS256","typ":"JWT"}'
    local payload_json
    payload_json="$(printf '{"sub":"%s","role":"%s","iss":"portfolio-dev","aud":"portfolio-admin","iat":%d,"exp":%d}' \
        "${subject}" "${role}" "${now_epoch}" "${exp_epoch}")"

    local header_b64
    header_b64="$(printf '%s' "${header_json}" | base64url_encode)"
    local payload_b64
    payload_b64="$(printf '%s' "${payload_json}" | base64url_encode)"
    local signing_input="${header_b64}.${payload_b64}"

    local signature_b64
    signature_b64="$(printf '%s' "${signing_input}" | openssl dgst -sha256 -hmac "${secret}" -binary | base64url_encode)"

    printf '%s.%s\n' "${signing_input}" "${signature_b64}"
}

main "$@"

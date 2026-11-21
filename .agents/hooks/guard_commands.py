#!/usr/bin/env python3
"""
PreToolUse guard for run_command.
Hard-denies force pushes, --no-verify hook bypasses, and prod dev-token minting;
requires explicit confirmation (force_ask) for destructive git/Docker volume operations.
"""
from __future__ import annotations

import json
import re
import sys

GIT_PUSH_FORCE_PATTERN: re.Pattern[str] = re.compile(
    r"\bgit\b[^\n;&|]*\bpush\b[^\n;&|]*(?:--force(?!-with-lease)(?:\s|$)|(?:^|\s)-f(?:\s|$))"
)
GIT_NO_VERIFY_PATTERN: re.Pattern[str] = re.compile(
    r"\bgit\b[^\n;&|]*\b(?:commit|push)\b[^\n;&|]*--no-verify(?:\s|$)"
)
PROD_DEV_TOKEN_PATTERN: re.Pattern[str] = re.compile(
    r"\bAPP_ENV\s*=\s*['\"]?prod(?:uction)?['\"]?\b[^\n]*dev-token\.sh"
)
GIT_FORCE_WITH_LEASE_PATTERN: re.Pattern[str] = re.compile(
    r"\bgit\b[^\n;&|]*\bpush\b[^\n;&|]*--force-with-lease(?:\s|$)"
)
GIT_RESET_HARD_PATTERN: re.Pattern[str] = re.compile(
    r"\bgit\b[^\n;&|]*\breset\b[^\n;&|]*--hard(?:\s|$)"
)
GIT_CLEAN_FORCE_PATTERN: re.Pattern[str] = re.compile(
    r"\bgit\b[^\n;&|]*\bclean\b[^\n;&|]*(?:--force(?:\s|$)|(?:^|\s)-[a-zA-Z]*f[a-zA-Z]*(?:\s|$))"
)
DOCKER_COMPOSE_DOWN_VOLUMES_PATTERN: re.Pattern[str] = re.compile(
    r"\bdocker\b[^\n;&|]*\bcompose\b[^\n;&|]*\bdown\b[^\n;&|]*(?:--volumes(?:\s|$)|(?:^|\s)-v(?:\s|$))"
)
DOCKER_VOLUME_RM_PATTERN: re.Pattern[str] = re.compile(
    r"\bdocker\b[^\n;&|]*\bvolume\s+(?:rm|prune)\b"
)


def respond(decision: str, reason: str | None = None) -> int:
    payload: dict[str, str] = {"decision": decision}
    if reason:
        payload["reason"] = reason
    sys.stdout.write(json.dumps(payload) + "\n")
    return 0


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return respond("allow")

    tool_call = payload.get("toolCall") or {}
    args = tool_call.get("args") or {}
    cmd = args.get("CommandLine") or args.get("commandLine") or ""
    if not isinstance(cmd, str) or not cmd.strip():
        return respond("allow")

    if GIT_PUSH_FORCE_PATTERN.search(cmd):
        return respond("deny", "Force pushing (--force / -f) is blocked. Use --force-with-lease with explicit confirmation if needed.")

    if GIT_NO_VERIFY_PATTERN.search(cmd):
        return respond("deny", "Bypassing git hooks via --no-verify is blocked.")

    if PROD_DEV_TOKEN_PATTERN.search(cmd):
        return respond("deny", "Running dev-token.sh in prod/production environments is strictly prohibited.")

    if GIT_RESET_HARD_PATTERN.search(cmd):
        return respond("force_ask", "git reset --hard discards uncommitted local work. Confirm before running.")

    if GIT_CLEAN_FORCE_PATTERN.search(cmd):
        return respond("force_ask", "git clean -f permanently removes untracked files. Confirm before running.")

    if GIT_FORCE_WITH_LEASE_PATTERN.search(cmd):
        return respond("force_ask", "git push --force-with-lease rewrites remote branch history. Confirm before running.")

    if DOCKER_COMPOSE_DOWN_VOLUMES_PATTERN.search(cmd) or DOCKER_VOLUME_RM_PATTERN.search(cmd):
        return respond("force_ask", "Destroying Docker volumes wipes local Postgres/MinIO data. Confirm before running.")

    return respond("allow")


if __name__ == "__main__":
    raise SystemExit(main())

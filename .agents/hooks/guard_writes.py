#!/usr/bin/env python3
"""
PreToolUse guard for edit_file and write_file.
Blocks edits to committed Flyway V*.sql migrations, real secret/.env files, and .git internals.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path

MIGRATION_V_PATTERN: re.Pattern[str] = re.compile(r"(?:^|/)migrations/V[^/]*\.sql$")
SECRET_SUFFIXES: tuple[str, ...] = (".pem", ".key", ".p12", ".jks", ".keystore", ".secret.bru")


def respond(decision: str, reason: str | None = None) -> int:
    payload: dict[str, str] = {"decision": decision}
    if reason:
        payload["reason"] = reason
    sys.stdout.write(json.dumps(payload) + "\n")
    return 0


def resolve_repo_root(base_dir: Path) -> Path:
    try:
        res = subprocess.run(
            ["git", "-C", str(base_dir), "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
        if res.returncode == 0 and res.stdout.strip():
            return Path(res.stdout.strip()).resolve()
    except OSError:
        pass
    return base_dir.resolve()


def is_git_tracked(repo_root: Path, rel_path: str) -> bool:
    try:
        res = subprocess.run(
            ["git", "-C", str(repo_root), "ls-files", "--error-unmatch", rel_path],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
        return res.returncode == 0
    except OSError:
        return False


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        return respond("allow")

    tool_call = payload.get("toolCall") or {}
    args = tool_call.get("args") or {}
    raw_path = args.get("Path") or args.get("path") or ""
    if not isinstance(raw_path, str) or not raw_path.strip():
        return respond("allow")

    workspace_paths = payload.get("workspacePaths") or []
    cwd = payload.get("cwd") or (workspace_paths[0] if workspace_paths else os.getcwd())
    base_dir = Path(cwd)
    repo_root = resolve_repo_root(base_dir)

    raw_p = Path(raw_path.strip())
    resolved = raw_p.resolve() if raw_p.is_absolute() else (base_dir / raw_p).resolve()

    try:
        rel_path = resolved.relative_to(repo_root).as_posix()
    except ValueError:
        rel_path = resolved.as_posix()

    normalized_posix = rel_path.lstrip("/")
    basename = resolved.name

    # Guard 1: .git internals
    if normalized_posix == ".git" or normalized_posix.startswith(".git/") or "/.git/" in f"/{normalized_posix}":
        return respond("deny", f"Direct edits inside .git are forbidden ({rel_path}).")

    # Guard 2: Real .env files & secret material (allow .env.example)
    is_env_secret = (basename == ".env" or basename.startswith(".env.")) and basename != ".env.example"
    is_key_secret = any(basename.endswith(suffix) for suffix in SECRET_SUFFIXES)
    if is_env_secret or is_key_secret:
        return respond(
            "deny",
            f"Refusing to edit secret/environment file '{basename}'. Document variables in .env.example instead.",
        )

    # Guard 3: Committed Flyway versioned V*.sql migrations are immutable
    if MIGRATION_V_PATTERN.search(normalized_posix) and is_git_tracked(repo_root, rel_path):
        return respond(
            "deny",
            f"Committed Flyway migration '{rel_path}' is strictly immutable. Append a new V<n+1>__<description>.sql file instead.",
        )

    return respond("allow")


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
PostToolUse hook for edit_file, write_file, and run_command.
Tracks source/config edits and successful verification commands per conversation.
"""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

CODE_CONFIG_SUFFIXES: frozenset[str] = frozenset(
    {".kt", ".kts", ".swift", ".sql", ".sh", ".yaml", ".yml", ".toml"}
)
EXCLUDED_SEGMENTS: tuple[str, ...] = ("/.agents/", "/docs/", "/.gemini/")
MAX_DIRTY_FILES: int = 20
VERIFY_CMD_PATTERN: re.Pattern[str] = re.compile(
    r"(?:^|&&|\|\||;|\()\s*(?:bash\s+)?(?:\./|[^\s]*?/)?(?:scripts/)?verify\.sh(?:\s|$|;|&&|\|\|)"
    r"|(?:^|&&|\|\||;|\()\s*(?:\./|[^\s]*?/)?kotlin\s+(?:build|test)\b"
)


def resolve_state_file(payload: dict) -> Path:
    artifact_dir = (payload.get("artifactDirectoryPath") or "").strip()
    if artifact_dir:
        state_dir = Path(artifact_dir) / ".portfolio_hook_state"
    else:
        conv_id = re.sub(r"[^A-Za-z0-9._-]", "_", (payload.get("conversationId") or "default").strip()) or "default"
        state_dir = Path("/tmp/portfolio-hook-state") / conv_id
    state_dir.mkdir(parents=True, exist_ok=True)
    return state_dir / "state.json"


def load_state(state_file: Path) -> dict:
    if not state_file.is_file():
        return {"dirty_files": [], "last_dirty_ts": 0.0, "last_verified_ts": 0.0, "last_verify_cmd": ""}
    try:
        data = json.loads(state_file.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except (OSError, json.JSONDecodeError):
        pass
    return {"dirty_files": [], "last_dirty_ts": 0.0, "last_verified_ts": 0.0, "last_verify_cmd": ""}


def save_state(state_file: Path, state: dict) -> None:
    try:
        state_file.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    except OSError:
        pass


def is_tracked_code_or_config(raw_path: str) -> bool:
    normalized = "/" + raw_path.replace("\\", "/").lstrip("/")
    if any(segment in normalized for segment in EXCLUDED_SEGMENTS):
        return False
    return Path(normalized).suffix.lower() in CODE_CONFIG_SUFFIXES


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError:
        sys.stdout.write("{}\n")
        return 0

    if payload.get("error"):
        sys.stdout.write("{}\n")
        return 0

    tool_call = payload.get("toolCall") or {}
    tool_name = str(tool_call.get("name") or "").strip().lower()
    args = tool_call.get("args") or {}
    state_file = resolve_state_file(payload)

    if tool_name in {"edit_file", "write_file"}:
        raw_path = args.get("Path") or args.get("path") or ""
        if isinstance(raw_path, str) and raw_path.strip() and is_tracked_code_or_config(raw_path.strip()):
            state = load_state(state_file)
            dirty = [p for p in state.get("dirty_files", []) if p != raw_path.strip()]
            dirty.append(raw_path.strip())
            state["dirty_files"] = dirty[-MAX_DIRTY_FILES:]
            state["last_dirty_ts"] = time.time()
            save_state(state_file, state)

    elif tool_name == "run_command":
        cmd = args.get("CommandLine") or args.get("commandLine") or ""
        if isinstance(cmd, str) and VERIFY_CMD_PATTERN.search(cmd):
            state = load_state(state_file)
            now = time.time()
            state["last_verified_ts"] = max(now, float(state.get("last_dirty_ts", 0.0)))
            state["last_verify_cmd"] = cmd.strip()
            state["dirty_files"] = []
            save_state(state_file, state)

    sys.stdout.write("{}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
Stop hook requiring verification (./scripts/verify.sh --fast or ./kotlin build/test)
whenever code/config files were edited since the last verification pass.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


def resolve_state_file(payload: dict) -> Path:
    artifact_dir = (payload.get("artifactDirectoryPath") or "").strip()
    if artifact_dir:
        return Path(artifact_dir) / ".portfolio_hook_state" / "state.json"
    conv_id = re.sub(r"[^A-Za-z0-9._-]", "_", (payload.get("conversationId") or "default").strip()) or "default"
    return Path("/tmp/portfolio-hook-state") / conv_id / "state.json"


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

    if payload.get("terminationReason") != "NO_TOOL_CALL":
        return respond("allow")

    if payload.get("continuationActive"):
        return respond("allow")

    if payload.get("fullyIdle") is False:
        return respond("allow")

    state_file = resolve_state_file(payload)
    if not state_file.is_file():
        return respond("allow")

    try:
        state = json.loads(state_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return respond("allow")

    dirty_files = state.get("dirty_files") or []
    last_dirty_ts = float(state.get("last_dirty_ts") or 0.0)
    last_verified_ts = float(state.get("last_verified_ts") or 0.0)

    if not dirty_files or last_verified_ts >= last_dirty_ts:
        return respond("allow")

    sample = ", ".join(Path(p).name for p in dirty_files[:5])
    suffix = f" (+{len(dirty_files) - 5} more)" if len(dirty_files) > 5 else ""
    return respond(
        "continue",
        f"Modified code/config files since last verification ({sample}{suffix}). "
        "Run `./scripts/verify.sh --fast` (or targeted `./kotlin test` / `./kotlin build`) before finishing.",
    )


if __name__ == "__main__":
    raise SystemExit(main())

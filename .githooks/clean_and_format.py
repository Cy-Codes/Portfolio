#!/usr/bin/env python3
"""
Pre-commit Auto-Formatter & Unused Import Cleaner for Portfolio.
Normalizes line endings, trailing whitespace, and EOF newlines across staged files,
cleans/sorts Kotlin and Python imports, and respects immutable Flyway migrations.
"""
from __future__ import annotations

import ast
import os
import re
import shutil
import subprocess
import sys

KOTLIN_IMPORT_PATTERN: re.Pattern[str] = re.compile(
    r"^\s*import\s+(?P<fqn>[A-Za-z0-9_.]+(?:\.\*)?)(?:\s+as\s+(?P<alias>[A-Za-z_][A-Za-z0-9_]*))?\s*$"
)
KOTLIN_IDENTIFIER_PATTERN: re.Pattern[str] = re.compile(
    r"`[^`]+`|[A-Za-z_][A-Za-z0-9_]*"
)
KOTLIN_OPERATOR_SYMBOLS: frozenset[str] = frozenset(
    {"getValue", "setValue", "provideDelegate", "get", "set", "invoke"}
)
TEXT_EXTENSIONS: frozenset[str] = frozenset(
    {".kt", ".kts", ".py", ".sql", ".sh", ".md", ".json", ".yml", ".yaml", ".toml", ".bru", ".xml"}
)
EXCLUDED_PATH_SEGMENTS: tuple[str, ...] = (
    "/build/",
    "/.kotlin/",
    "/.git/",
    "/.idea/",
    "/migrations/V",
)


def is_format_eligible(filepath: str) -> bool:
    """Pure check ensuring build outputs, IDE state, and immutable Flyway migrations are untouched."""
    normalized = filepath.replace("\\", "/")
    ext = os.path.splitext(normalized)[1].lower()
    return (
        ext in TEXT_EXTENSIONS
        and not any(segment in normalized for segment in EXCLUDED_PATH_SEGMENTS)
        and os.path.isfile(filepath)
    )


def normalize_whitespace(content: str, preserve_markdown_breaks: bool = False) -> str:
    """Pure transformation normalizing CRLF to LF, stripping trailing spaces, and ensuring single EOF newline."""
    normalized_lines = tuple(
        line if (preserve_markdown_breaks and line.endswith("  ")) else line.rstrip(" \t")
        for line in content.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    )
    body = "\n".join(normalized_lines).rstrip("\n")
    return f"{body}\n" if body else ""


def collect_used_python_names(tree: ast.AST) -> frozenset[str]:
    """Pure AST traversal collecting all referenced identifier names outside import statements."""
    import_nodes = {
        node
        for node in ast.walk(tree)
        if isinstance(node, (ast.Import, ast.ImportFrom))
    }
    referenced: set[str] = set()
    for node in ast.walk(tree):
        if node in import_nodes:
            continue
        if isinstance(node, ast.Name):
            referenced.add(node.id)
        elif isinstance(node, ast.Attribute):
            curr: ast.AST = node
            while isinstance(curr, ast.Attribute):
                curr = curr.value
            if isinstance(curr, ast.Name):
                referenced.add(curr.id)
    return frozenset(referenced)


def format_import_node(node: ast.Import | ast.ImportFrom, kept_aliases: tuple[ast.alias, ...]) -> str:
    """Pure formatter reconstructing a single Python import statement from kept aliases."""
    sorted_aliases = sorted(kept_aliases, key=lambda alias: (alias.name, alias.asname or ""))
    rendered_names = ", ".join(
        f"{alias.name} as {alias.asname}" if alias.asname else alias.name
        for alias in sorted_aliases
    )
    if isinstance(node, ast.Import):
        return f"import {rendered_names}"
    module_prefix = "." * (node.level or 0) + (node.module or "")
    return f"from {module_prefix} import {rendered_names}"


def remove_unused_python_imports(source: str) -> str:
    """Pure AST transformation removing unused top-level Python imports."""
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return source

    used_names = collect_used_python_names(tree)
    lines = source.splitlines()
    lines_to_remove: set[int] = set()
    line_replacements: dict[int, str] = {}

    for node in tree.body:
        if not isinstance(node, (ast.Import, ast.ImportFrom)):
            continue
        if isinstance(node, ast.ImportFrom) and node.module == "__future__":
            continue
        if any(alias.name == "*" for alias in node.names):
            continue

        kept_aliases = tuple(
            alias
            for alias in node.names
            if (alias.asname or alias.name.split(".")[0]) in used_names
        )

        if len(kept_aliases) == len(node.names):
            continue

        start_line = node.lineno - 1
        end_line = (node.end_lineno or node.lineno) - 1
        for line_idx in range(start_line, end_line + 1):
            lines_to_remove.add(line_idx)

        if kept_aliases:
            line_replacements[start_line] = format_import_node(node, kept_aliases)

    updated_lines = [
        line_replacements[idx]
        if idx in line_replacements
        else line
        for idx, line in enumerate(lines)
        if idx not in lines_to_remove or idx in line_replacements
    ]
    return "\n".join(updated_lines) + "\n"


def clean_and_sort_kotlin_imports(content: str) -> str:
    """Pure transformation removing unused Kotlin imports and sorting remaining imports alphabetically."""
    lines = content.splitlines()
    import_indices = [
        idx for idx, line in enumerate(lines) if KOTLIN_IMPORT_PATTERN.match(line)
    ]
    if not import_indices:
        return content

    first_idx = import_indices[0]
    last_idx = import_indices[-1]
    body_text = "\n".join(lines[:first_idx] + lines[last_idx + 1:])
    used_tokens = {
        tok.strip("`")
        for tok in KOTLIN_IDENTIFIER_PATTERN.findall(body_text)
    }

    def resolve_kept_import(raw_line: str) -> str | None:
        match = KOTLIN_IMPORT_PATTERN.match(raw_line.strip())
        if not match:
            return None
        fqn = match.group("fqn")
        alias = match.group("alias")
        if fqn.endswith(".*"):
            return f"import {fqn}"
        symbol = alias or fqn.split(".")[-1]
        if symbol in used_tokens or symbol in KOTLIN_OPERATOR_SYMBOLS:
            return f"import {fqn} as {alias}" if alias else f"import {fqn}"
        return None

    kept_imports = sorted(
        {
            rendered
            for idx in range(first_idx, last_idx + 1)
            if (rendered := resolve_kept_import(lines[idx])) is not None
        }
    )
    prefix = lines[:first_idx]
    suffix = lines[last_idx + 1:]
    return "\n".join(prefix + kept_imports + suffix) + "\n"


def format_kotlin_file(filepath: str) -> bool:
    """Side-effecting formatter applying ktlint if installed plus deterministic import cleanup/sorting."""
    if shutil.which("ktlint"):
        subprocess.run(["ktlint", "--format", "--quiet", filepath], check=False)

    try:
        with open(filepath, "r", encoding="utf-8") as fh:
            original = fh.read()
    except OSError:
        return False

    cleaned = normalize_whitespace(
        clean_and_sort_kotlin_imports(original) if filepath.endswith(".kt") else original
    )
    if cleaned != original:
        with open(filepath, "w", encoding="utf-8") as fh:
            fh.write(cleaned)
        return True
    return False


def format_python_file(filepath: str) -> bool:
    """Side-effecting formatter applying ruff if installed and deterministic AST unused-import cleanup."""
    if shutil.which("ruff"):
        subprocess.run(["ruff", "check", "--select", "F401,I", "--fix", "--quiet", filepath], check=False)
        subprocess.run(["ruff", "format", "--quiet", filepath], check=False)

    try:
        with open(filepath, "r", encoding="utf-8") as fh:
            original = fh.read()
    except OSError:
        return False

    cleaned = normalize_whitespace(remove_unused_python_imports(original))
    if cleaned != original:
        with open(filepath, "w", encoding="utf-8") as fh:
            fh.write(cleaned)
        return True
    return False


def format_shell_file(filepath: str) -> bool:
    """Side-effecting formatter running shfmt -i 4 -w when installed and normalizing whitespace."""
    if shutil.which("shfmt"):
        subprocess.run(["shfmt", "-i", "4", "-w", filepath], check=False)

    return format_plain_text_file(filepath, preserve_markdown_breaks=False)


def format_plain_text_file(filepath: str, preserve_markdown_breaks: bool) -> bool:
    """Side-effecting formatter normalizing line endings and whitespace."""
    try:
        with open(filepath, "r", encoding="utf-8") as fh:
            original = fh.read()
    except OSError:
        return False

    cleaned = normalize_whitespace(original, preserve_markdown_breaks=preserve_markdown_breaks)
    if cleaned != original:
        with open(filepath, "w", encoding="utf-8") as fh:
            fh.write(cleaned)
        return True
    return False


def format_file(filepath: str) -> bool:
    """Side-effecting dispatcher routing a file path to its language-specific formatter."""
    if not is_format_eligible(filepath):
        return False
    ext = os.path.splitext(filepath)[1].lower()
    if ext in (".kt", ".kts"):
        return format_kotlin_file(filepath)
    if ext == ".py":
        return format_python_file(filepath)
    if ext == ".sh":
        return format_shell_file(filepath)
    return format_plain_text_file(filepath, preserve_markdown_breaks=(ext == ".md"))


def collect_repo_files(root: str) -> tuple[str, ...]:
    """Pure directory traversal collecting format-eligible repository files."""
    collected = tuple(
        os.path.join(dirpath, filename)
        for dirpath, dirnames, filenames in os.walk(root)
        for filename in filenames
        if is_format_eligible(os.path.join(dirpath, filename))
    )
    return tuple(sorted(collected))


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        return 0
    targets = collect_repo_files(os.getcwd()) if argv[1] == "--all" else tuple(argv[1:])
    changed = sum(1 for path in targets if format_file(path))
    if changed > 0:
        print(f"Formatted {changed} file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

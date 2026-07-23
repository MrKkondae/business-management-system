#!/usr/bin/env python3
"""Validate feature-package acceptance scenario structure and references."""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DESIGN_ROOT = (
    ROOT
    / "docs"
    / "03.application-development"
    / "02.design"
)
PACKAGE_ROOT = DESIGN_ROOT / "03.feature-packages"
CATALOG_ROOT = DESIGN_ROOT / "02.catalog"

SCENARIO_PATTERN = re.compile(r"^\s*시나리오(?: 개요)?:\s*(.+?)\s*$")
API_ID_PATTERN = re.compile(r"\bAPI-[A-Z0-9]+(?:-[A-Z0-9]+)+\b")
EXTERNAL_ERROR_PATTERN = re.compile(
    r"\b(?:AUTH|COMMON|SYS|RES)_[A-Z0-9]+(?:_[A-Z0-9]+)+\b"
)


def read_ids(name: str, column: str) -> set[str]:
    with (CATALOG_ROOT / name).open(
        encoding="utf-8-sig", newline=""
    ) as handle:
        return {row[column] for row in csv.DictReader(handle)}


def manifest_acceptance_paths(errors: list[str]) -> set[Path]:
    declared: set[Path] = set()
    for manifest in PACKAGE_ROOT.rglob("manifest.yaml"):
        lines = manifest.read_text(encoding="utf-8").splitlines()
        in_acceptance = False
        acceptance_indent = 0
        for line in lines:
            match = re.match(r"^(\s*)acceptance_tests:\s*$", line)
            if match:
                in_acceptance = True
                acceptance_indent = len(match.group(1))
                continue
            if not in_acceptance:
                continue
            item_match = re.match(r"^(\s*)-\s+(.+?)\s*$", line)
            if item_match and len(item_match.group(1)) > acceptance_indent:
                path = manifest.parent / item_match.group(2)
                declared.add(path)
                if not path.exists():
                    errors.append(
                        f"{manifest.relative_to(ROOT)}: missing acceptance test "
                        f"{item_match.group(2)}"
                    )
                continue
            if line.strip() and len(line) - len(line.lstrip()) <= acceptance_indent:
                in_acceptance = False
    return declared


def validate_feature(
    path: Path,
    api_ids: set[str],
    error_codes: set[str],
    global_names: dict[str, Path],
    errors: list[str],
) -> int:
    content = path.read_text(encoding="utf-8")
    lines = content.splitlines()
    relative = path.relative_to(ROOT)

    if not lines or lines[0].strip() != "# language: ko":
        errors.append(f"{relative}: first line must be '# language: ko'")
    if not any(line.startswith("기능:") for line in lines):
        errors.append(f"{relative}: missing 기능 declaration")

    scenario_indexes: list[tuple[int, str, bool]] = []
    for index, line in enumerate(lines):
        match = SCENARIO_PATTERN.match(line)
        if match:
            name = match.group(1)
            is_outline = line.lstrip().startswith("시나리오 개요:")
            scenario_indexes.append((index, name, is_outline))
            previous = global_names.get(name)
            if previous is not None:
                errors.append(
                    f"{relative}:{index + 1}: duplicate scenario name also in "
                    f"{previous.relative_to(ROOT)}"
                )
            else:
                global_names[name] = path

    if not scenario_indexes:
        errors.append(f"{relative}: no scenarios")

    for position, (start, name, is_outline) in enumerate(scenario_indexes):
        end = (
            scenario_indexes[position + 1][0]
            if position + 1 < len(scenario_indexes)
            else len(lines)
        )
        block = lines[start + 1 : end]
        if not any(re.match(r"^\s*만일\s+", line) for line in block):
            errors.append(
                f"{relative}:{start + 1}: scenario '{name}' has no 만일 step"
            )
        if not any(re.match(r"^\s*그러면\s+", line) for line in block):
            errors.append(
                f"{relative}:{start + 1}: scenario '{name}' has no 그러면 step"
            )
        if is_outline and not any(
            re.match(r"^\s*예:\s*$", line) for line in block
        ):
            errors.append(
                f"{relative}:{start + 1}: outline '{name}' has no examples"
            )

    for api_id in set(API_ID_PATTERN.findall(content)):
        if api_id not in api_ids:
            errors.append(f"{relative}: unknown api_id={api_id}")

    for error_code in set(EXTERNAL_ERROR_PATTERN.findall(content)):
        if error_code not in error_codes:
            errors.append(f"{relative}: unknown external error_code={error_code}")

    return len(scenario_indexes)


def validate() -> tuple[list[str], int, int]:
    errors: list[str] = []
    api_ids = read_ids("apis.csv", "api_id")
    error_codes = read_ids("error-codes.csv", "error_code")
    declared_paths = manifest_acceptance_paths(errors)
    feature_paths = set(PACKAGE_ROOT.rglob("*.feature"))

    undeclared = sorted(feature_paths - declared_paths)
    for path in undeclared:
        errors.append(
            f"{path.relative_to(ROOT)}: feature is not declared in manifest"
        )

    global_names: dict[str, Path] = {}
    scenario_count = 0
    for path in sorted(feature_paths):
        scenario_count += validate_feature(
            path, api_ids, error_codes, global_names, errors
        )

    return errors, len(feature_paths), scenario_count


def main() -> int:
    errors, feature_count, scenario_count = validate()
    for error in errors:
        print(f"ERROR: {error}")
    print(
        "Test-scenario validation: "
        f"features={feature_count} scenarios={scenario_count} errors={len(errors)}"
    )
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

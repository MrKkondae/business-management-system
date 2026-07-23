#!/usr/bin/env python3
"""Validate authentication prerequisite seed-data CSV files."""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SEED_DIR = (
    ROOT
    / "docs"
    / "03.application-development"
    / "02.design"
    / "02.catalog"
    / "seed-data"
)
SYSTEM_ADMIN_ROLE_ID = "01KY3HYG000000000000000001"
ULID_PATTERN = re.compile(r"^[0-9A-HJKMNP-TV-Z]{26}$")
CODE_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*$")

SCHEMAS = {
    "roles.csv": (
        "role_id",
        "role_name",
        "role_description",
        "protected_yn",
    ),
    "menus.csv": (
        "menu_id",
        "parent_menu_id",
        "menu_name",
        "menu_url",
        "sort_order",
        "screen_id",
    ),
    "role-menu-permissions.csv": ("role_id", "menu_id"),
    "code-groups.csv": (
        "code_group_id",
        "code_group_name",
        "description",
    ),
    "codes.csv": (
        "code_group_id",
        "code",
        "code_name",
        "sort_order",
    ),
}


def read_csv(name: str, errors: list[str]) -> list[dict[str, str]]:
    path = SEED_DIR / name
    if not path.exists():
        errors.append(f"{name}: file is missing")
        return []

    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        expected = list(SCHEMAS[name])
        if reader.fieldnames != expected:
            errors.append(
                f"{name}: invalid header; expected {','.join(expected)}"
            )
            return []
        rows = list(reader)

    for line_no, row in enumerate(rows, start=2):
        for column, value in row.items():
            if value != value.strip():
                errors.append(
                    f"{name}:{line_no}: {column} has surrounding whitespace"
                )
    return rows


def unique_values(
    name: str,
    rows: list[dict[str, str]],
    columns: tuple[str, ...],
    errors: list[str],
) -> None:
    seen: set[tuple[str, ...]] = set()
    for line_no, row in enumerate(rows, start=2):
        key = tuple(row[column] for column in columns)
        if key in seen:
            errors.append(
                f"{name}:{line_no}: duplicate {','.join(columns)}={key}"
            )
        seen.add(key)


def require_ulid(
    name: str,
    rows: list[dict[str, str]],
    column: str,
    errors: list[str],
) -> None:
    for line_no, row in enumerate(rows, start=2):
        value = row[column]
        if not ULID_PATTERN.fullmatch(value):
            errors.append(f"{name}:{line_no}: invalid ULID in {column}: {value}")


def validate() -> list[str]:
    errors: list[str] = []
    data = {name: read_csv(name, errors) for name in SCHEMAS}
    if errors:
        return errors

    roles = data["roles.csv"]
    menus = data["menus.csv"]
    permissions = data["role-menu-permissions.csv"]
    code_groups = data["code-groups.csv"]
    codes = data["codes.csv"]

    unique_values("roles.csv", roles, ("role_id",), errors)
    unique_values("roles.csv", roles, ("role_name",), errors)
    unique_values("menus.csv", menus, ("menu_id",), errors)
    unique_values("role-menu-permissions.csv", permissions, ("role_id", "menu_id"), errors)
    unique_values("code-groups.csv", code_groups, ("code_group_id",), errors)
    unique_values("codes.csv", codes, ("code_group_id", "code"), errors)

    for name, rows, column in (
        ("roles.csv", roles, "role_id"),
        ("menus.csv", menus, "menu_id"),
        ("role-menu-permissions.csv", permissions, "role_id"),
        ("role-menu-permissions.csv", permissions, "menu_id"),
        ("code-groups.csv", code_groups, "code_group_id"),
        ("codes.csv", codes, "code_group_id"),
    ):
        require_ulid(name, rows, column, errors)

    role_ids = {row["role_id"] for row in roles}
    menu_ids = {row["menu_id"] for row in menus}
    group_ids = {row["code_group_id"] for row in code_groups}

    admin_roles = [
        row
        for row in roles
        if row["role_id"] == SYSTEM_ADMIN_ROLE_ID
        and row["protected_yn"] == "Y"
    ]
    if len(admin_roles) != 1:
        errors.append("roles.csv: protected system administrator role is required")

    for line_no, row in enumerate(menus, start=2):
        parent_id = row["parent_menu_id"]
        if parent_id and parent_id not in menu_ids:
            errors.append(
                f"menus.csv:{line_no}: unknown parent_menu_id={parent_id}"
            )
        if parent_id == row["menu_id"]:
            errors.append(f"menus.csv:{line_no}: menu cannot be its own parent")
        try:
            if int(row["sort_order"]) < 0:
                raise ValueError
        except ValueError:
            errors.append(f"menus.csv:{line_no}: sort_order must be a nonnegative integer")

    admin_menu_ids = {
        row["menu_id"]
        for row in permissions
        if row["role_id"] == SYSTEM_ADMIN_ROLE_ID
    }
    missing_admin_permissions = sorted(menu_ids - admin_menu_ids)
    if missing_admin_permissions:
        errors.append(
            "role-menu-permissions.csv: system administrator is missing menus: "
            + ", ".join(missing_admin_permissions)
        )

    for line_no, row in enumerate(permissions, start=2):
        if row["role_id"] not in role_ids:
            errors.append(
                f"role-menu-permissions.csv:{line_no}: unknown role_id={row['role_id']}"
            )
        if row["menu_id"] not in menu_ids:
            errors.append(
                f"role-menu-permissions.csv:{line_no}: unknown menu_id={row['menu_id']}"
            )

    for line_no, row in enumerate(codes, start=2):
        if row["code_group_id"] not in group_ids:
            errors.append(
                f"codes.csv:{line_no}: unknown code_group_id={row['code_group_id']}"
            )
        code = row["code"]
        if len(code) > 20 or not CODE_PATTERN.fullmatch(code):
            errors.append(
                f"codes.csv:{line_no}: code must be <=20 uppercase letters, digits, or underscores"
            )
        try:
            if int(row["sort_order"]) < 0:
                raise ValueError
        except ValueError:
            errors.append(f"codes.csv:{line_no}: sort_order must be a nonnegative integer")

    return errors


def main() -> int:
    errors = validate()
    for error in errors:
        print(f"ERROR: {error}")
    print(f"Seed-data validation: errors={len(errors)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

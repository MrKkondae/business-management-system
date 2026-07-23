#!/usr/bin/env python3
"""Generate reviewed Flyway seed SQL from the seed-data design CSV files."""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

from validate_seed_data import SEED_DIR, validate


def read_rows(name: str) -> list[dict[str, str]]:
    with (SEED_DIR / name).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def sql_text(value: str | None) -> str:
    if value is None or value == "":
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def values(rows: list[tuple[str, ...]]) -> str:
    return ",\n".join(
        "    (" + ", ".join(row) + ")" for row in rows
    )


def generate_sql() -> str:
    roles = read_rows("roles.csv")
    menus = read_rows("menus.csv")
    permissions = read_rows("role-menu-permissions.csv")
    groups = read_rows("code-groups.csv")
    codes = read_rows("codes.csv")

    sections = [
        "-- Generated from docs/03.application-development/02.design/02.catalog/seed-data.",
        "-- Review this output and save it as a new immutable Flyway migration.",
        "",
        """INSERT INTO tb_sys_role (
    role_id, role_nm, role_desc, reg_id, reg_dtm, mod_id, mod_dtm, del_yn
)
VALUES
"""
        + values(
            [
                (
                    sql_text(row["role_id"]),
                    sql_text(row["role_name"]),
                    sql_text(row["role_description"]),
                    "'SYSTEM'",
                    "CURRENT_TIMESTAMP",
                    "NULL",
                    "NULL",
                    "'N'",
                )
                for row in roles
            ]
        )
        + """
ON CONFLICT (role_id) DO UPDATE
SET role_nm = EXCLUDED.role_nm,
    role_desc = EXCLUDED.role_desc,
    mod_id = 'SYSTEM',
    mod_dtm = CURRENT_TIMESTAMP,
    del_yn = 'N';""",
        """INSERT INTO tb_com_code_grp (
    cd_grp_id, cd_grp_nm, cd_grp_desc_cont,
    reg_id, reg_dtm, mod_id, mod_dtm, del_yn
)
VALUES
"""
        + values(
            [
                (
                    sql_text(row["code_group_id"]),
                    sql_text(row["code_group_name"]),
                    sql_text(row["description"]),
                    "'SYSTEM'",
                    "CURRENT_TIMESTAMP",
                    "NULL",
                    "NULL",
                    "'N'",
                )
                for row in groups
            ]
        )
        + """
ON CONFLICT (cd_grp_id) DO UPDATE
SET cd_grp_nm = EXCLUDED.cd_grp_nm,
    cd_grp_desc_cont = EXCLUDED.cd_grp_desc_cont,
    mod_id = 'SYSTEM',
    mod_dtm = CURRENT_TIMESTAMP,
    del_yn = 'N';""",
        """INSERT INTO tb_com_code (
    cd_grp_id, cd, cd_nm, sort_seq,
    reg_id, reg_dtm, mod_id, mod_dtm, del_yn
)
VALUES
"""
        + values(
            [
                (
                    sql_text(row["code_group_id"]),
                    sql_text(row["code"]),
                    sql_text(row["code_name"]),
                    row["sort_order"],
                    "'SYSTEM'",
                    "CURRENT_TIMESTAMP",
                    "NULL",
                    "NULL",
                    "'N'",
                )
                for row in codes
            ]
        )
        + """
ON CONFLICT (cd_grp_id, cd) DO UPDATE
SET cd_nm = EXCLUDED.cd_nm,
    sort_seq = EXCLUDED.sort_seq,
    mod_id = 'SYSTEM',
    mod_dtm = CURRENT_TIMESTAMP,
    del_yn = 'N';""",
        """INSERT INTO tb_sys_menu (
    menu_id, up_menu_id, menu_nm, menu_url, sort_seq,
    reg_id, reg_dtm, mod_id, mod_dtm, del_yn
)
VALUES
"""
        + values(
            [
                (
                    sql_text(row["menu_id"]),
                    sql_text(row["parent_menu_id"]),
                    sql_text(row["menu_name"]),
                    sql_text(row["menu_url"]),
                    row["sort_order"],
                    "'SYSTEM'",
                    "CURRENT_TIMESTAMP",
                    "NULL",
                    "NULL",
                    "'N'",
                )
                for row in menus
            ]
        )
        + """
ON CONFLICT (menu_id) DO UPDATE
SET up_menu_id = EXCLUDED.up_menu_id,
    menu_nm = EXCLUDED.menu_nm,
    menu_url = EXCLUDED.menu_url,
    sort_seq = EXCLUDED.sort_seq,
    mod_id = 'SYSTEM',
    mod_dtm = CURRENT_TIMESTAMP,
    del_yn = 'N';""",
        """INSERT INTO tb_sys_role_menu_perm_rel (
    role_id, menu_id, reg_id, reg_dtm
)
VALUES
"""
        + values(
            [
                (
                    sql_text(row["role_id"]),
                    sql_text(row["menu_id"]),
                    "'SYSTEM'",
                    "CURRENT_TIMESTAMP",
                )
                for row in permissions
            ]
        )
        + "\nON CONFLICT (role_id, menu_id) DO NOTHING;",
    ]
    return "\n\n".join(sections) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        help="Output SQL path. If omitted, print SQL to stdout.",
    )
    args = parser.parse_args()

    errors = validate()
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print("Seed-data validation failed; SQL was not generated.", file=sys.stderr)
        return 1

    result = generate_sql()
    if args.output:
        args.output.write_text(result, encoding="utf-8", newline="\n")
        print(f"Generated {args.output}")
    else:
        sys.stdout.write(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())

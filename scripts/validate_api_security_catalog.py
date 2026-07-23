#!/usr/bin/env python3
"""Validate API, error-code and permission design catalogs."""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG_DIR = (
    ROOT
    / "docs"
    / "03.application-development"
    / "02.design"
    / "02.catalog"
)
OPENAPI_PATH = (
    ROOT
    / "docs"
    / "03.application-development"
    / "02.design"
    / "04.openapi"
    / "openapi.yaml"
)
SCREEN_LIST_PATH = (
    ROOT
    / "docs"
    / "03.application-development"
    / "01.analysis"
    / "06.screen-list"
    / "00.screen-list.md"
)
FEATURE_PACKAGE_DIR = (
    ROOT
    / "docs"
    / "03.application-development"
    / "02.design"
    / "03.feature-packages"
)
MENU_PATH = CATALOG_DIR / "seed-data" / "menus.csv"

SCHEMAS = {
    "apis.csv": (
        "api_id",
        "api_name",
        "domain",
        "method",
        "path",
        "operation_id",
        "function_ids",
        "screen_ids",
        "permission_code",
        "csrf_required_yn",
        "reauthentication_required_yn",
        "request_schema",
        "response_schema",
        "error_codes",
        "feature_package_id",
        "design_status",
        "implementation_status",
    ),
    "error-codes.csv": (
        "error_code",
        "domain",
        "http_status",
        "user_message",
        "exposure_policy",
        "retryable_yn",
        "log_level",
        "description",
    ),
    "permissions.csv": (
        "permission_code",
        "permission_name",
        "permission_type",
        "session_scope",
        "screen_id",
        "menu_id",
        "data_scope",
        "reauthentication_default_yn",
        "description",
    ),
    "functions.csv": (
        "function_id",
        "function_name",
        "domain",
        "requirement_id",
        "backlog_id",
        "function_type",
        "screen_ids",
        "api_ids",
        "primary_entities",
        "design_status",
        "implementation_status",
    ),
}

API_ID_PATTERN = re.compile(r"^API-[A-Z0-9]+(?:-[A-Z0-9]+)+$")
FUNCTION_ID_PATTERN = re.compile(r"^BFD-\d{2}(?:-\d{2}){3}$")
SCREEN_ID_PATTERN = re.compile(r"^[A-Z]{3}-\d{3}$")
ERROR_CODE_PATTERN = re.compile(r"^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$")
PERMISSION_CODE_PATTERN = re.compile(r"^[A-Z0-9]+(?:[-_][A-Z0-9]+)*$")

HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"}
DESIGN_STATUSES = {
    "DESIGN_PENDING",
    "DESIGN_IN_PROGRESS",
    "READY",
    "DEFERRED",
    "EXCLUDED",
}
IMPLEMENTATION_STATUSES = {
    "NOT_STARTED",
    "IN_PROGRESS",
    "IMPLEMENTED",
    "VERIFIED",
    "BLOCKED",
}
PERMISSION_TYPES = {"PUBLIC", "SESSION", "MENU"}
SESSION_SCOPES = {"NONE", "GENERAL", "LIMITED", "GENERAL_OR_LIMITED"}
EXPOSURE_POLICIES = {"SAFE", "GENERIC_AUTH", "GENERIC_INTERNAL"}
LOG_LEVELS = {"INFO", "WARN", "ERROR"}


def split_ids(value: str) -> list[str]:
    return [item for item in value.split(";") if item]


def read_csv(name: str, errors: list[str]) -> list[dict[str, str]]:
    path = CATALOG_DIR / name
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


def unique(
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


def parse_openapi() -> tuple[dict[str, dict[str, object]], set[str]]:
    operations: dict[str, dict[str, object]] = {}
    schema_names: set[str] = set()
    current_path = ""
    current_operation: dict[str, object] | None = None
    in_components = False

    for line in OPENAPI_PATH.read_text(encoding="utf-8").splitlines():
        if line == "components:":
            in_components = True
            current_operation = None
            continue

        path_match = re.match(r"^  (/[^:]+):$", line)
        if path_match and not in_components:
            current_path = path_match.group(1)
            current_operation = None
            continue

        method_match = re.match(
            r"^    (get|post|put|patch|delete):$", line
        )
        if method_match and current_path and not in_components:
            current_operation = {
                "path": current_path,
                "method": method_match.group(1).upper(),
                "operation_id": "",
                "function_ids": [],
                "screen_ids": [],
                "error_codes": [],
                "permission_code": "",
                "csrf_required_yn": "N",
                "http_statuses": set(),
            }
            continue

        if current_operation is not None:
            value_match = re.match(
                r"^      (operationId|x-bms-api-id|x-bms-permission): (.+)$",
                line,
            )
            if value_match:
                key, value = value_match.groups()
                key_map = {
                    "operationId": "operation_id",
                    "x-bms-api-id": "api_id",
                    "x-bms-permission": "permission_code",
                }
                current_operation[key_map[key]] = value
                if key == "x-bms-api-id":
                    operations[value] = current_operation
                continue

            list_match = re.match(
                r"^      x-bms-(function-ids|screen-ids|error-codes): \[(.*)]$",
                line,
            )
            if list_match:
                key, value = list_match.groups()
                parsed = [
                    item.strip() for item in value.split(",") if item.strip()
                ]
                key_map = {
                    "function-ids": "function_ids",
                    "screen-ids": "screen_ids",
                    "error-codes": "error_codes",
                }
                current_operation[key_map[key]] = parsed
                continue

            if "csrfToken:" in line:
                current_operation["csrf_required_yn"] = "Y"
                continue

            response_match = re.match(r"^        '(\d{3})':$", line)
            if response_match:
                statuses = current_operation["http_statuses"]
                assert isinstance(statuses, set)
                statuses.add(response_match.group(1))

        if in_components:
            schema_match = re.match(r"^    ([A-Za-z][A-Za-z0-9]*):$", line)
            if schema_match:
                schema_names.add(schema_match.group(1))

    return operations, schema_names


def discover_screen_ids() -> set[str]:
    content = SCREEN_LIST_PATH.read_text(encoding="utf-8")
    return set(re.findall(r"\b[A-Z]{3}-\d{3}\b", content))


def discover_package_ids() -> set[str]:
    package_ids: set[str] = set()
    for path in FEATURE_PACKAGE_DIR.rglob("manifest.yaml"):
        match = re.search(
            r"^package_id:\s*(\S+)\s*$",
            path.read_text(encoding="utf-8"),
            flags=re.MULTILINE,
        )
        if match:
            package_ids.add(match.group(1))
    return package_ids


def discover_menu_ids() -> set[str]:
    with MENU_PATH.open(encoding="utf-8-sig", newline="") as handle:
        return {
            row["menu_id"]
            for row in csv.DictReader(handle)
        }


def validate() -> list[str]:
    errors: list[str] = []
    data = {name: read_csv(name, errors) for name in SCHEMAS}
    if errors:
        return errors

    apis = data["apis.csv"]
    error_codes = data["error-codes.csv"]
    permissions = data["permissions.csv"]
    functions = data["functions.csv"]

    unique("apis.csv", apis, ("api_id",), errors)
    unique("apis.csv", apis, ("operation_id",), errors)
    unique("apis.csv", apis, ("method", "path"), errors)
    unique("error-codes.csv", error_codes, ("error_code",), errors)
    unique("permissions.csv", permissions, ("permission_code",), errors)

    api_ids = {row["api_id"] for row in apis}
    error_by_code = {row["error_code"]: row for row in error_codes}
    permission_codes = {row["permission_code"] for row in permissions}
    function_by_id = {row["function_id"]: row for row in functions}
    screen_ids = discover_screen_ids()
    package_ids = discover_package_ids()
    menu_ids = discover_menu_ids()
    openapi_operations, openapi_schemas = parse_openapi()

    for line_no, row in enumerate(error_codes, start=2):
        code = row["error_code"]
        if not ERROR_CODE_PATTERN.fullmatch(code):
            errors.append(f"error-codes.csv:{line_no}: invalid error_code={code}")
        try:
            status = int(row["http_status"])
            if status < 400 or status > 599:
                raise ValueError
        except ValueError:
            errors.append(
                f"error-codes.csv:{line_no}: http_status must be 400..599"
            )
        if row["exposure_policy"] not in EXPOSURE_POLICIES:
            errors.append(
                f"error-codes.csv:{line_no}: invalid exposure_policy"
            )
        if row["retryable_yn"] not in {"Y", "N"}:
            errors.append(f"error-codes.csv:{line_no}: invalid retryable_yn")
        if row["log_level"] not in LOG_LEVELS:
            errors.append(f"error-codes.csv:{line_no}: invalid log_level")

    for line_no, row in enumerate(permissions, start=2):
        code = row["permission_code"]
        if not PERMISSION_CODE_PATTERN.fullmatch(code):
            errors.append(
                f"permissions.csv:{line_no}: invalid permission_code={code}"
            )
        if row["permission_type"] not in PERMISSION_TYPES:
            errors.append(
                f"permissions.csv:{line_no}: invalid permission_type"
            )
        if row["session_scope"] not in SESSION_SCOPES:
            errors.append(
                f"permissions.csv:{line_no}: invalid session_scope"
            )
        if row["reauthentication_default_yn"] not in {"Y", "N"}:
            errors.append(
                f"permissions.csv:{line_no}: invalid reauthentication_default_yn"
            )
        if row["screen_id"] and row["screen_id"] not in screen_ids:
            errors.append(
                f"permissions.csv:{line_no}: unknown screen_id={row['screen_id']}"
            )
        if row["menu_id"] and row["menu_id"] not in menu_ids:
            errors.append(
                f"permissions.csv:{line_no}: unknown menu_id={row['menu_id']}"
            )
        if row["permission_type"] == "MENU" and (
            not row["screen_id"] or not row["menu_id"]
        ):
            errors.append(
                f"permissions.csv:{line_no}: MENU requires screen_id and menu_id"
            )
        if row["permission_type"] == "PUBLIC" and row["session_scope"] != "NONE":
            errors.append(
                f"permissions.csv:{line_no}: PUBLIC requires session_scope=NONE"
            )
        if row["permission_type"] != "PUBLIC" and row["session_scope"] == "NONE":
            errors.append(
                f"permissions.csv:{line_no}: non-PUBLIC permission requires a session"
            )

    for line_no, row in enumerate(apis, start=2):
        api_id = row["api_id"]
        if not API_ID_PATTERN.fullmatch(api_id):
            errors.append(f"apis.csv:{line_no}: invalid api_id={api_id}")
        if row["method"] not in HTTP_METHODS:
            errors.append(f"apis.csv:{line_no}: invalid method={row['method']}")
        if not row["path"].startswith("/"):
            errors.append(f"apis.csv:{line_no}: path must start with /")
        if row["csrf_required_yn"] not in {"Y", "N"}:
            errors.append(f"apis.csv:{line_no}: invalid csrf_required_yn")
        if row["reauthentication_required_yn"] not in {"Y", "N"}:
            errors.append(
                f"apis.csv:{line_no}: invalid reauthentication_required_yn"
            )
        if (
            row["reauthentication_required_yn"] == "Y"
            and row["permission_code"] == "PUBLIC"
        ):
            errors.append(
                f"apis.csv:{line_no}: PUBLIC API cannot require reauthentication"
            )
        if row["design_status"] not in DESIGN_STATUSES:
            errors.append(f"apis.csv:{line_no}: invalid design_status")
        if row["implementation_status"] not in IMPLEMENTATION_STATUSES:
            errors.append(f"apis.csv:{line_no}: invalid implementation_status")
        if row["permission_code"] not in permission_codes:
            errors.append(
                f"apis.csv:{line_no}: unknown permission_code={row['permission_code']}"
            )
        if row["feature_package_id"] not in package_ids:
            errors.append(
                f"apis.csv:{line_no}: unknown feature_package_id={row['feature_package_id']}"
            )

        for function_id in split_ids(row["function_ids"]):
            if not FUNCTION_ID_PATTERN.fullmatch(function_id):
                errors.append(
                    f"apis.csv:{line_no}: invalid function_id={function_id}"
                )
                continue
            function = function_by_id.get(function_id)
            if function is None:
                errors.append(
                    f"apis.csv:{line_no}: unknown function_id={function_id}"
                )
            elif api_id not in split_ids(function["api_ids"]):
                errors.append(
                    f"apis.csv:{line_no}: {function_id} does not reference {api_id}"
                )

        for screen_id in split_ids(row["screen_ids"]):
            if not SCREEN_ID_PATTERN.fullmatch(screen_id) or screen_id not in screen_ids:
                errors.append(
                    f"apis.csv:{line_no}: unknown screen_id={screen_id}"
                )

        for error_code in split_ids(row["error_codes"]):
            error = error_by_code.get(error_code)
            if error is None:
                errors.append(
                    f"apis.csv:{line_no}: unknown error_code={error_code}"
                )

        for schema_column in ("request_schema", "response_schema"):
            schema_name = row[schema_column]
            if (
                schema_name
                and schema_name != "NoContent"
                and schema_name not in openapi_schemas
            ):
                errors.append(
                    f"apis.csv:{line_no}: unknown OpenAPI schema {schema_name}"
                )

        operation = openapi_operations.get(api_id)
        if operation is None:
            errors.append(f"apis.csv:{line_no}: API missing from OpenAPI")
            continue
        comparisons = {
            "method": row["method"],
            "path": row["path"],
            "operation_id": row["operation_id"],
            "permission_code": row["permission_code"],
            "csrf_required_yn": row["csrf_required_yn"],
        }
        for key, expected in comparisons.items():
            if operation[key] != expected:
                errors.append(
                    f"apis.csv:{line_no}: OpenAPI {key}={operation[key]} != {expected}"
                )
        for key, column in (
            ("function_ids", "function_ids"),
            ("screen_ids", "screen_ids"),
            ("error_codes", "error_codes"),
        ):
            if set(operation[key]) != set(split_ids(row[column])):
                errors.append(
                    f"apis.csv:{line_no}: OpenAPI {key} differs"
                )

        statuses = operation["http_statuses"]
        assert isinstance(statuses, set)
        for error_code in split_ids(row["error_codes"]):
            error = error_by_code.get(error_code)
            if error and error["http_status"] not in statuses:
                errors.append(
                    f"apis.csv:{line_no}: OpenAPI lacks HTTP {error['http_status']} for {error_code}"
                )

    missing_openapi_apis = sorted(set(openapi_operations) - api_ids)
    if missing_openapi_apis:
        errors.append(
            "apis.csv: OpenAPI APIs missing from catalog: "
            + ", ".join(missing_openapi_apis)
        )

    for line_no, row in enumerate(functions, start=2):
        for api_id in split_ids(row["api_ids"]):
            if api_id not in api_ids:
                errors.append(
                    f"functions.csv:{line_no}: unknown api_id={api_id}"
                )

    for path in FEATURE_PACKAGE_DIR.rglob("*.md"):
        content = path.read_text(encoding="utf-8")
        for api_id in set(re.findall(r"\bAPI-[A-Z0-9]+(?:-[A-Z0-9]+)+\b", content)):
            if api_id not in api_ids:
                relative = path.relative_to(ROOT)
                errors.append(f"{relative}: unknown api_id={api_id}")

    return errors


def main() -> int:
    errors = validate()
    for error in errors:
        print(f"ERROR: {error}")
    print(f"API/security catalog validation: errors={len(errors)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

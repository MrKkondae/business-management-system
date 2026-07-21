#!/usr/bin/env python3
"""BMS 기능 카탈로그의 구조와 원본 문서 참조를 검증한다."""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
CATALOG = DOCS / "03.application-development/02.design/02.catalog/functions.csv"
BFD_DIR = DOCS / "03.application-development/01.analysis/04.business-function-detail"
REQUIREMENTS = DOCS / "03.application-development/01.analysis/01.requirements-definition/00.requirements-definition.md"
SCREENS = DOCS / "03.application-development/01.analysis/06.screen-list/00.screen-list.md"
BACKLOG = DOCS / "00.project-management/product-backlog.md"
ENTITY_DIR = DOCS / "02.architecture/02.data-architecture/02.logical-model/entities"

HEADER = (
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
)
DOMAINS = {"system", "common", "customer", "sales", "contract", "project", "employee"}
FUNCTION_TYPES = {"COMMAND", "QUERY", "BATCH", "INTERFACE"}
DESIGN_STATUSES = {"DESIGN_PENDING", "DESIGN_IN_PROGRESS", "READY", "DEFERRED", "EXCLUDED"}
IMPLEMENTATION_STATUSES = {"NOT_STARTED", "IN_PROGRESS", "IMPLEMENTED", "VERIFIED", "BLOCKED"}
BFD_PATTERN = re.compile(r"^\|\s*(BFD-[0-9]{2}(?:-[0-9]{2}){3})\s*\|\s*([^|]+?)\s*\|")


def markdown_ids(path: Path, pattern: str) -> set[str]:
    return set(re.findall(pattern, path.read_text(encoding="utf-8-sig"), flags=re.MULTILINE))


def bfd_functions() -> dict[str, str]:
    result: dict[str, str] = {}
    for path in sorted(BFD_DIR.glob("[0-9][0-9].*.md")):
        if path.name.startswith("00."):
            continue
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            match = BFD_PATTERN.match(line)
            if match:
                result[match.group(1)] = match.group(2).strip()
    return result


def entity_names() -> set[str]:
    result: set[str] = set()
    for path in ENTITY_DIR.glob("entity-*.csv"):
        with path.open(encoding="utf-8-sig", newline="") as source:
            result.update(row["entity_name"] for row in csv.DictReader(source))
    return result


def split_refs(value: str) -> list[str]:
    return [item.strip() for item in value.split(";") if item.strip()]


def main() -> int:
    errors: list[str] = []
    reviews: list[str] = []
    with CATALOG.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if tuple(reader.fieldnames or ()) != HEADER:
            errors.append(f"헤더 불일치: {reader.fieldnames}")
        rows = list(reader)

    bfd = bfd_functions()
    requirements = markdown_ids(REQUIREMENTS, r"^\|\s*(REQ-[A-Z]+-[0-9]{3})\s*\|")
    screens = markdown_ids(SCREENS, r"^\|\s*([A-Z]{3}-[0-9]{3})\s*\|")
    backlogs = markdown_ids(BACKLOG, r"^\|\s*(PB-[0-9]{3})\s*\|")
    entities = entity_names()

    catalog_ids: set[str] = set()
    for row in rows:
        function_id = row["function_id"]
        if function_id in catalog_ids:
            errors.append(f"중복 기능 ID: {function_id}")
        catalog_ids.add(function_id)
        if function_id not in bfd:
            errors.append(f"BFD에 없는 기능 ID: {function_id}")
        elif row["function_name"] != bfd[function_id]:
            errors.append(f"BFD 기능명 불일치: {function_id}")
        if row["domain"] not in DOMAINS:
            errors.append(f"허용되지 않은 도메인: {function_id}={row['domain']}")
        if row["function_type"] not in FUNCTION_TYPES:
            errors.append(f"허용되지 않은 기능유형: {function_id}={row['function_type']}")
        if row["design_status"] not in DESIGN_STATUSES:
            errors.append(f"허용되지 않은 설계상태: {function_id}={row['design_status']}")
        if row["implementation_status"] not in IMPLEMENTATION_STATUSES:
            errors.append(f"허용되지 않은 구현상태: {function_id}={row['implementation_status']}")
        for ref in split_refs(row["requirement_id"]):
            if ref not in requirements:
                errors.append(f"미등록 요구사항 참조: {function_id}={ref}")
        for ref in split_refs(row["backlog_id"]):
            if ref not in backlogs:
                errors.append(f"미등록 백로그 참조: {function_id}={ref}")
        for ref in split_refs(row["screen_ids"]):
            if ref not in screens:
                errors.append(f"미등록 화면 참조: {function_id}={ref}")
        for ref in split_refs(row["primary_entities"]):
            if ref not in entities:
                errors.append(f"미등록 엔터티 참조: {function_id}={ref}")
        if row["design_status"] in {"DESIGN_PENDING", "DESIGN_IN_PROGRESS", "READY"}:
            for field in ("requirement_id", "backlog_id", "screen_ids"):
                if not row[field]:
                    reviews.append(f"{function_id}:{field}")

    for missing in sorted(set(bfd) - catalog_ids):
        errors.append(f"카탈로그 누락 BFD 기능: {missing}")

    first_count = sum(
        row["design_status"] in {"DESIGN_PENDING", "DESIGN_IN_PROGRESS", "READY"}
        for row in rows
    )
    deferred_count = sum(row["design_status"] == "DEFERRED" for row in rows)
    for error in errors:
        print(f"ERROR {error}")
    print(
        f"SUMMARY rows={len(rows)} first_scope={first_count} deferred={deferred_count} "
        f"errors={len(errors)} review_refs={len(reviews)}"
    )
    if reviews:
        grouped: dict[str, int] = {}
        for review in reviews:
            field = review.rsplit(":", 1)[1]
            grouped[field] = grouped.get(field, 0) + 1
        print("REVIEW " + " ".join(f"{field}={count}" for field, count in sorted(grouped.items())))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

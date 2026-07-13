#!/usr/bin/env python3
"""Validate the BMS data catalog CSV files.

Structural and reference violations are reported as errors. Duplicate standard
terms and entity attributes that are not registered as standard terms are
reported as warnings.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


BUSINESS_AREAS = (
    "system",
    "common",
    "customer",
    "sales",
    "contract",
    "project",
    "employee",
    "cost",
    "revenue",
)

FILE_SCHEMAS = {
    "01.standard/standard-words.csv": (
        "source_no",
        "korean_name",
        "english_name",
        "abbreviation",
        "category",
        "description",
        "note",
    ),
    "01.standard/standard-terms.csv": (
        "source_no",
        "owner_area",
        "korean_name",
        "english_name",
        "column_name",
        "domain_name",
        "description",
        "note",
    ),
    "01.standard/domain-definition.csv": (
        "record_type",
        "category",
        "domain_name",
        "allowed_value",
        "nullable",
        "default_value",
        "description",
        "example_column",
    ),
    "01.standard/db-type-mapping.csv": ("dbms", "domain_name", "data_type"),
}

for area in BUSINESS_AREAS:
    FILE_SCHEMAS[f"02.logical-model/entities/entity-{area}.csv"] = (
        "entity_name",
        "entity_type",
        "description",
        "note",
    )
    FILE_SCHEMAS[f"02.logical-model/attributes/entity-attribute-{area}.csv"] = (
        "entity_name",
        "attribute_sequence",
        "attribute_name",
    )
    FILE_SCHEMAS[f"02.logical-model/relationships/entity-relation-{area}.csv"] = (
        "relation_expression",
        "description",
    )

FILE_SCHEMAS["02.logical-model/relationships/entity-relation-cross.csv"] = (
    "relation_expression",
    "description",
)

REQUIRED_FIELDS = {
    "standard-words": ("source_no", "korean_name", "english_name", "abbreviation"),
    "standard-terms": (
        "source_no",
        "owner_area",
        "korean_name",
        "english_name",
        "column_name",
        "domain_name",
    ),
    "domain-definition": ("record_type", "domain_name"),
    "db-type-mapping": ("dbms", "domain_name", "data_type"),
    "entities": ("entity_name", "entity_type"),
    "attributes": ("entity_name", "attribute_sequence", "attribute_name"),
    "relationships": ("relation_expression",),
}

RELATION_RE = re.compile(
    r"^(.+?)\s+\S+\s*:\s*\S+\s+(.+)$"
)


@dataclass(frozen=True)
class CatalogRow:
    path: Path
    relative_path: str
    line: int
    values: dict[str, str]


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    message: str
    relative_path: str | None = None
    line: int | None = None
    subject: str | None = None

    def format(self) -> str:
        location = self.relative_path or "catalog"
        if self.line is not None:
            location += f":{self.line}"
        return f"{self.severity} {location} [{self.code}] {self.message}"


class Validator:
    def __init__(self, catalog_dir: Path) -> None:
        self.catalog_dir = catalog_dir
        self.findings: list[Finding] = []
        self.rows: dict[str, list[CatalogRow]] = {}

    def add(
        self,
        severity: str,
        code: str,
        message: str,
        row: CatalogRow | None = None,
        relative_path: str | None = None,
        line: int | None = None,
        subject: str | None = None,
    ) -> None:
        self.findings.append(
            Finding(
                severity,
                code,
                message,
                row.relative_path if row else relative_path,
                row.line if row else line,
                subject,
            )
        )

    def validate(self) -> list[Finding]:
        self._validate_files_and_structure()
        self._validate_unique_keys()
        self._validate_references()
        self._validate_terms()
        return self.findings

    def _validate_files_and_structure(self) -> None:
        for relative_path, expected_header in FILE_SCHEMAS.items():
            path = self.catalog_dir / Path(relative_path)
            if not path.is_file():
                self.add(
                    "ERROR",
                    "MISSING_FILE",
                    "스키마에 정의된 CSV 파일이 없습니다.",
                    relative_path=relative_path,
                )
                continue
            parsed_rows = self._read_csv(path, relative_path, expected_header)
            if parsed_rows is not None:
                self.rows[relative_path] = parsed_rows

        expected = set(FILE_SCHEMAS)
        for path in sorted(self.catalog_dir.rglob("*.csv")):
            relative_path = path.relative_to(self.catalog_dir).as_posix()
            if relative_path not in expected:
                self.add(
                    "ERROR",
                    "UNEXPECTED_FILE",
                    "스키마에 정의되지 않은 CSV 파일입니다.",
                    relative_path=relative_path,
                )

    def _read_csv(
        self, path: Path, relative_path: str, expected_header: tuple[str, ...]
    ) -> list[CatalogRow] | None:
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8-sig")
        except UnicodeDecodeError as exc:
            self.add(
                "ERROR",
                "INVALID_ENCODING",
                f"UTF-8로 읽을 수 없습니다: {exc}",
                relative_path=relative_path,
            )
            return None

        if b"\r" in raw:
            self.add(
                "ERROR",
                "INVALID_LINE_ENDING",
                "LF가 아닌 줄바꿈(CR 또는 CRLF)이 포함되어 있습니다.",
                relative_path=relative_path,
            )

        reader = csv.reader(io.StringIO(text, newline=""), strict=True)
        try:
            header = next(reader)
        except StopIteration:
            self.add(
                "ERROR",
                "MISSING_HEADER",
                "CSV 파일이 비어 있습니다.",
                relative_path=relative_path,
            )
            return None
        except csv.Error as exc:
            self.add(
                "ERROR",
                "INVALID_CSV",
                f"헤더를 파싱할 수 없습니다: {exc}",
                relative_path=relative_path,
                line=1,
            )
            return None

        if tuple(header) != expected_header:
            self.add(
                "ERROR",
                "INVALID_HEADER",
                "헤더 또는 열 순서가 다릅니다. "
                f"expected={','.join(expected_header)!r}, actual={','.join(header)!r}",
                relative_path=relative_path,
                line=1,
            )
            return None

        result: list[CatalogRow] = []
        kind = file_kind(relative_path)
        try:
            for values in reader:
                line = reader.line_num
                if not values:
                    self.add(
                        "ERROR",
                        "BLANK_ROW",
                        "빈 행이 포함되어 있습니다.",
                        relative_path=relative_path,
                        line=line,
                    )
                    continue
                if len(values) != len(expected_header):
                    self.add(
                        "ERROR",
                        "INVALID_COLUMN_COUNT",
                        f"열 개수가 {len(values)}개입니다. {len(expected_header)}개여야 합니다.",
                        relative_path=relative_path,
                        line=line,
                    )
                    continue

                row = CatalogRow(
                    path,
                    relative_path,
                    line,
                    dict(zip(expected_header, values)),
                )
                result.append(row)
                for name, value in row.values.items():
                    if "\n" in value or "\r" in value:
                        self.add(
                            "ERROR",
                            "EMBEDDED_NEWLINE",
                            f"{name!r} 셀에 줄바꿈이 포함되어 있습니다.",
                            row=row,
                        )
                for name in REQUIRED_FIELDS[kind]:
                    if not row.values[name].strip():
                        self.add(
                            "ERROR",
                            "MISSING_REQUIRED_VALUE",
                            f"필수 값 {name!r}이 비어 있습니다.",
                            row=row,
                        )
        except csv.Error as exc:
            self.add(
                "ERROR",
                "INVALID_CSV",
                f"CSV를 파싱할 수 없습니다: {exc}",
                relative_path=relative_path,
                line=reader.line_num,
            )
        return result

    def _validate_unique_keys(self) -> None:
        rules: list[tuple[str, Iterable[CatalogRow], tuple[str, ...]]] = [
            ("표준단어 source_no", self._rows_of_kind("standard-words"), ("source_no",)),
            ("표준용어 source_no", self._rows_of_kind("standard-terms"), ("source_no",)),
            (
                "논리 도메인명",
                (
                    row
                    for row in self._rows_of_kind("domain-definition")
                    if row.values["record_type"] == "DOMAIN"
                ),
                ("domain_name",),
            ),
            (
                "DBMS별 도메인 매핑",
                self._rows_of_kind("db-type-mapping"),
                ("dbms", "domain_name"),
            ),
            ("엔터티명", self._rows_of_kind("entities"), ("entity_name",)),
            (
                "엔터티별 속성 순서",
                self._rows_of_kind("attributes"),
                ("entity_name", "attribute_sequence"),
            ),
            (
                "엔터티별 속성명",
                self._rows_of_kind("attributes"),
                ("entity_name", "attribute_name"),
            ),
            (
                "관계식",
                self._rows_of_kind("relationships"),
                ("relation_expression",),
            ),
        ]
        for label, rows, fields in rules:
            self._report_duplicate_keys(label, rows, fields)

        domain_rows = self._rows_of_kind("domain-definition")
        self._report_duplicate_keys(
            "도메인 허용값",
            (
                row
                for row in domain_rows
                if row.values["record_type"] == "ALLOWED_VALUE"
            ),
            ("domain_name", "allowed_value"),
        )

    def _report_duplicate_keys(
        self, label: str, rows: Iterable[CatalogRow], fields: tuple[str, ...]
    ) -> None:
        seen: dict[tuple[str, ...], CatalogRow] = {}
        for row in rows:
            key = tuple(row.values[field].strip() for field in fields)
            if any(not value for value in key):
                continue
            if key in seen:
                first = seen[key]
                display = ", ".join(key)
                self.add(
                    "ERROR",
                    "DUPLICATE_KEY",
                    f"{label} {display!r}이 중복됩니다 "
                    f"(최초: {first.relative_path}:{first.line}).",
                    row=row,
                )
            else:
                seen[key] = row

    def _validate_references(self) -> None:
        domain_rows = list(self._rows_of_kind("domain-definition"))
        domains = {
            row.values["domain_name"].strip()
            for row in domain_rows
            if row.values["record_type"] == "DOMAIN"
        }
        for row in domain_rows:
            record_type = row.values["record_type"].strip()
            if record_type not in {"DOMAIN", "ALLOWED_VALUE"}:
                self.add(
                    "ERROR",
                    "INVALID_RECORD_TYPE",
                    f"지원하지 않는 record_type {record_type!r}입니다.",
                    row=row,
                    subject=record_type,
                )
            elif record_type == "ALLOWED_VALUE":
                self._require_reference(row, "domain_name", domains, "논리 도메인")

        for row in self._rows_of_kind("standard-terms"):
            self._require_reference(row, "domain_name", domains, "논리 도메인")
        for row in self._rows_of_kind("db-type-mapping"):
            self._require_reference(row, "domain_name", domains, "논리 도메인")

        entities = {
            row.values["entity_name"].strip() for row in self._rows_of_kind("entities")
        }
        for row in self._rows_of_kind("attributes"):
            self._require_reference(row, "entity_name", entities, "엔터티")

        for row in self._rows_of_kind("relationships"):
            expression = row.values["relation_expression"].strip()
            match = RELATION_RE.fullmatch(expression)
            if not match:
                if expression:
                    self.add(
                        "ERROR",
                        "INVALID_RELATION_EXPRESSION",
                        "관계식은 '<엔터티> <카디널리티> : <카디널리티> <엔터티>' 형식이어야 합니다.",
                        row=row,
                        subject=expression,
                    )
                continue

    def _require_reference(
        self, row: CatalogRow, field: str, registered: set[str], target_label: str
    ) -> None:
        value = row.values[field].strip()
        if value and value not in registered:
            self.add(
                "ERROR",
                "UNKNOWN_REFERENCE",
                f"{field} 값 {value!r}에 해당하는 {target_label}이 등록되어 있지 않습니다.",
                row=row,
                subject=value,
            )

    def _validate_terms(self) -> None:
        term_rows = list(self._rows_of_kind("standard-terms"))
        groups: dict[str, list[CatalogRow]] = defaultdict(list)
        for row in term_rows:
            owner_area = row.values["owner_area"].strip()
            if owner_area and owner_area not in BUSINESS_AREAS:
                self.add(
                    "ERROR",
                    "INVALID_OWNER_AREA",
                    f"지원하지 않는 owner_area {owner_area!r}입니다.",
                    row=row,
                    subject=owner_area,
                )
            korean_name = row.values["korean_name"].strip()
            if korean_name:
                groups[korean_name].append(row)

        for value, duplicates in groups.items():
            if len(duplicates) < 2:
                continue
            locations = ", ".join(
                f"{row.relative_path}:{row.line}" for row in duplicates
            )
            self.add(
                "WARNING",
                "DUPLICATE_TERM",
                f"표준용어 한글명 {value!r}이 중복 등록되어 있습니다: {locations}",
                row=duplicates[0],
                subject=value,
            )

        registered_terms = {row.values["korean_name"].strip() for row in term_rows}
        for row in self._rows_of_kind("attributes"):
            attribute_name = row.values["attribute_name"].strip()
            if attribute_name and attribute_name not in registered_terms:
                self.add(
                    "WARNING",
                    "UNREGISTERED_TERM",
                    f"속성명 {attribute_name!r}이 표준용어에 등록되어 있지 않습니다.",
                    row=row,
                    subject=attribute_name,
                )

    def _rows_of_kind(self, kind: str) -> Iterable[CatalogRow]:
        for relative_path, rows in self.rows.items():
            if file_kind(relative_path) == kind:
                yield from rows


def file_kind(relative_path: str) -> str:
    if relative_path.endswith("standard-words.csv"):
        return "standard-words"
    if relative_path.endswith("standard-terms.csv"):
        return "standard-terms"
    if relative_path.endswith("domain-definition.csv"):
        return "domain-definition"
    if relative_path.endswith("db-type-mapping.csv"):
        return "db-type-mapping"
    if "/entities/entity-" in relative_path:
        return "entities"
    if "/attributes/entity-attribute-" in relative_path:
        return "attributes"
    if "/relationships/entity-relation-" in relative_path:
        return "relationships"
    raise ValueError(f"Unknown catalog file: {relative_path}")


def _term_target(relative_paths: list[str]) -> str:
    return "01.standard/standard-terms.csv"


def _work_instruction(
    code: str, relative_paths: list[str]
) -> tuple[int, str, str, str, str, str, str]:
    """Return priority, state, executor, reviewer, target, AI action, review action."""
    if code == "DUPLICATE_TERM":
        return (
            2,
            "사람 결정 대기",
            "AI",
            "데이터 아키텍처 담당자",
            "01.standard/standard-terms.csv",
            "중복 정의의 한글명·영문명·컬럼명·도메인을 비교하고 대표 용어와 소유 업무영역 후보를 제안한다. 승인 후 중복 제거 또는 명칭 분리를 CSV에 반영한다.",
            "동일 의미인지 확인하고 대표 용어·소유 업무영역·컬럼명 통합 또는 분리를 결정한다.",
        )
    if code == "UNREGISTERED_TERM":
        return (
            3,
            "AI 등록안 작성",
            "AI",
            "데이터 아키텍처 담당자",
            _term_target(relative_paths),
            "기존 표준용어를 검색하고 표준단어·도메인을 확인한 뒤 신규 표준용어 등록안을 작성한다. 승인된 등록안을 CSV에 반영한다.",
            "업무 의미, 표준 컬럼명, 논리 도메인과 등록 업무영역이 적절한지 검토한다.",
        )
    if code == "UNKNOWN_REFERENCE":
        return (
            1,
            "AI 원인 분석",
            "AI",
            "데이터 아키텍처 담당자(모호할 때)",
            relative_paths[0] if relative_paths else "관련 카탈로그 CSV",
            "참조 대상과 오타·별칭 여부를 조사하고 등록된 도메인 또는 엔터티를 사용하도록 CSV 수정안을 만든다. 의미가 명확하면 수정하고, 모호하면 결정 요청한다.",
            "참조 대상의 업무 의미가 둘 이상 가능할 때 올바른 대상을 결정한다.",
        )
    return (
        1,
        "AI 수정",
        "AI",
        "없음",
        relative_paths[0] if relative_paths else "관련 카탈로그 CSV",
        "스키마 문서와 원본 내용을 대조하여 구조 오류를 수정하고 검증기를 다시 실행한다.",
        "구조 수정으로 업무 의미가 변경되는 경우에만 검토한다.",
    )


def write_work_report(
    path: Path, findings: list[Finding], review_area: str | None = None
) -> int:
    """Write a reviewable work queue, grouping repeated unregistered terms."""
    if review_area:
        attribute_file = f"entity-attribute-{review_area}.csv"
        findings = [
            finding
            for finding in findings
            if finding.severity == "ERROR"
            or finding.code != "UNREGISTERED_TERM"
            or (finding.relative_path or "").endswith(attribute_file)
        ]

    groups: dict[tuple[str, str], list[Finding]] = defaultdict(list)
    for index, finding in enumerate(findings):
        if finding.code == "UNREGISTERED_TERM" and finding.subject:
            key = (finding.code, finding.subject)
        else:
            key = (finding.code, finding.subject or f"finding-{index}")
        groups[key].append(finding)

    rows: list[dict[str, str | int]] = []
    for (code, subject), grouped in groups.items():
        first = grouped[0]
        locations = [
            f"{finding.relative_path}:{finding.line}"
            if finding.line is not None
            else finding.relative_path or "catalog"
            for finding in grouped
        ]
        relative_paths = list(
            dict.fromkeys(
                finding.relative_path
                for finding in grouped
                if finding.relative_path is not None
            )
        )
        priority, state, executor, reviewer, target, ai_action, human_review = (
            _work_instruction(code, relative_paths)
        )
        rows.append(
            {
                "처리순서": priority,
                "심각도": first.severity,
                "검증코드": code,
                "작업상태": state,
                "실행담당": executor,
                "검토담당": reviewer,
                "대상값": first.subject or subject,
                "발생건수": len(grouped),
                "발생위치": " | ".join(locations),
                "수정대상": target,
                "AI작업": ai_action,
                "사람검토사항": human_review,
                "검출내용": first.message,
            }
        )

    rows.sort(
        key=lambda row: (
            int(row["처리순서"]),
            str(row["검증코드"]),
            str(row["대상값"]),
        )
    )
    fieldnames = (
        "처리순서",
        "심각도",
        "검증코드",
        "작업상태",
        "실행담당",
        "검토담당",
        "대상값",
        "발생건수",
        "발생위치",
        "수정대상",
        "AI작업",
        "사람검토사항",
        "검출내용",
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as report_file:
        writer = csv.DictWriter(report_file, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def parse_args() -> argparse.Namespace:
    default_catalog_dir = (
        Path(__file__).resolve().parents[1]
        / "docs"
        / "02.architecture"
        / "02.data-architecture"
    )
    parser = argparse.ArgumentParser(description="BMS 데이터 카탈로그 CSV를 검증합니다.")
    parser.add_argument(
        "--catalog-dir",
        type=Path,
        default=default_catalog_dir,
        help=f"데이터 카탈로그 루트 경로 (기본값: {default_catalog_dir})",
    )
    parser.add_argument(
        "--report",
        type=Path,
        help="담당자와 다음 작업을 포함한 검토용 CSV 보고서 경로",
    )
    parser.add_argument(
        "--review-area",
        choices=BUSINESS_AREAS,
        help="검토용 보고서의 미등록 용어를 지정한 업무영역으로 제한",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.review_area and not args.report:
        print(
            "ERROR arguments [REPORT_REQUIRED] "
            "--review-area는 --report와 함께 사용해야 합니다."
        )
        print("SUMMARY errors=1 warnings=0")
        return 2
    catalog_dir = args.catalog_dir.resolve()
    if not catalog_dir.is_dir():
        print(f"ERROR catalog [MISSING_CATALOG_DIR] 디렉터리가 없습니다: {catalog_dir}")
        print("SUMMARY errors=1 warnings=0")
        return 1

    findings = Validator(catalog_dir).validate()
    if args.report:
        report_path = args.report.resolve()
        try:
            work_items = write_work_report(
                report_path, findings, review_area=args.review_area
            )
        except OSError as exc:
            print(
                "ERROR report [REPORT_WRITE_FAILED] "
                f"검토용 CSV 보고서를 저장할 수 없습니다: {exc}"
            )
            print("SUMMARY errors=1 warnings=0")
            return 1
        scope = args.review_area or "all"
        print(f"REPORT path={report_path} review_area={scope} work_items={work_items}")
    else:
        for finding in findings:
            print(finding.format())

    errors = sum(finding.severity == "ERROR" for finding in findings)
    warnings = sum(finding.severity == "WARNING" for finding in findings)
    print(f"SUMMARY errors={errors} warnings={warnings}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

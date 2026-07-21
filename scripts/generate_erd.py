"""업무영역별 물리 ERD Markdown을 데이터 카탈로그 CSV에서 생성한다."""

from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA_ROOT = ROOT / "docs/02.architecture/02.data-architecture"
TYPE_FILE = DATA_ROOT / "01.standard/db-type-mapping.csv"

LOGICAL_TABLE_ALIASES = {
    "감사대상(논리)": "AUDIT_TARGET_LOGICAL",
    "프로젝트(논리)": "PROJECT_LOGICAL",
}

LOGICAL_COLUMN_ALIASES = {
    "대상ID": "TARGET_ID",
    "프로젝트ID": "PROJECT_ID",
}

AREA_CONFIGS = {
    "system": {
        "title": "시스템 관리",
        "output": "01.system-management-erd.md",
        "intro": "시스템 관리 영역의 PostgreSQL 물리 모델을 계정·권한, 조직·코드·설정, 외부연계·로그 및 공지사항 관점으로 표현한다.",
        "sections": (
            ("계정·권한·메뉴", "사용자 인증과 역할 기반 메뉴 접근 제어 구조이다.", ("TB_SYS_USER", "TB_SYS_ROLE", "TB_SYS_USER_ROLE_REL", "TB_SYS_MENU", "TB_SYS_ROLE_MENU_PERM_REL")),
            ("조직·공통코드·환경설정", "계층형 조직, 공통코드 및 시스템 운영 설정 구조이다.", ("TB_SYS_ORG", "TB_COM_CODE_GRP", "TB_COM_CODE", "TB_SYS_CONFIG")),
            ("외부연계·로그", "외부 API 호출 정책과 호출·접속·감사 로그 구조이다.", ("TB_SYS_EXT_REL_CONFIG", "TB_SYS_API_CALL_LOG", "TB_SYS_ACCESS_LOG", "TB_SYS_LOG")),
            ("공지사항", "게시 기간과 논리삭제를 적용하는 시스템 공지 구조이다.", ("TB_SYS_NOTICE",)),
        ),
        "traceability": (
            ("BFD-01-01", "사용자관리", "TB_SYS_USER, TB_SYS_ACCESS_LOG, TB_SYS_LOG"),
            ("BFD-01-02", "역할관리", "TB_SYS_ROLE, TB_SYS_USER_ROLE_REL, TB_SYS_ROLE_MENU_PERM_REL"),
            ("BFD-01-03", "메뉴관리", "TB_SYS_MENU, TB_SYS_ROLE_MENU_PERM_REL"),
            ("BFD-01-04", "공통코드관리", "TB_COM_CODE_GRP, TB_COM_CODE"),
            ("BFD-01-05", "조직관리", "TB_SYS_ORG"),
            ("BFD-01-06", "시스템환경설정", "TB_SYS_CONFIG, TB_SYS_EXT_REL_CONFIG"),
            ("BFD-01-07", "로그관리", "TB_SYS_ACCESS_LOG, TB_SYS_LOG, TB_SYS_API_CALL_LOG"),
            ("BFD-01-08", "공지사항관리", "TB_SYS_NOTICE"),
        ),
        "overview": (
            '    TB_RES_EMPLOYEE o|--o| TB_SYS_USER : "APP REF: EMP_ID"',
            '    TB_SYS_USER ||--o{ TB_SYS_USER_ROLE_REL : "DB FK: USER_ID"',
            '    TB_SYS_ROLE ||--o{ TB_SYS_USER_ROLE_REL : "DB FK: ROLE_ID"',
            '    TB_SYS_ROLE ||--o{ TB_SYS_ROLE_MENU_PERM_REL : "DB FK: ROLE_ID"',
            '    TB_SYS_MENU ||--o{ TB_SYS_ROLE_MENU_PERM_REL : "DB FK: MENU_ID"',
            '    TB_SYS_MENU o|--o{ TB_SYS_MENU : "APP REF: UP_MENU_ID"',
            '    TB_SYS_ORG o|--o{ TB_SYS_ORG : "APP REF: UP_ORG_ID"',
            '    TB_COM_CODE_GRP ||--o{ TB_COM_CODE : "DB FK: CD_GRP_ID"',
            '    TB_SYS_EXT_REL_CONFIG ||--o{ TB_SYS_API_CALL_LOG : "APP REF: EXT_REL_CONFIG_ID"',
            '    TB_SALES_ANNC_COLLECT_HIST ||--o{ TB_SYS_API_CALL_LOG : "APP REF: COLLECT_HIST_ID"',
            '    TB_SYS_USER o|--o{ TB_SYS_ACCESS_LOG : "APP REF: USER_ID"',
            '    TB_SYS_USER o|--o{ TB_SYS_LOG : "APP REF: PROC_USER_ID"',
            '    AUDIT_TARGET_LOGICAL o|--o{ TB_SYS_LOG : "APP REF: TGT_ID"',
        ),
        "overview_note": "`TB_RES_EMPLOYEE`와 `TB_SALES_ANNC_COLLECT_HIST`는 다른 업무영역의 테이블이며, `AUDIT_TARGET_LOGICAL`은 `(TGT_TYPE_CD, TGT_ID)`로 식별하는 다형 감사대상이다.",
        "notes": (
            "내부 단일 식별자는 애플리케이션에서 생성한 26자리 Monotonic ULID를 사용한다.",
            "모든 일시는 UTC로 저장하고 화면에서 `Asia/Seoul`로 변환한다.",
            "논리삭제된 부모 참조, 계층 순환, 외주인력 계정 생성 금지 및 다형 감사대상 정합성은 애플리케이션에서 검증한다.",
            "로그 테이블은 명시된 로그아웃일시 갱신 외에는 불변으로 취급하며 인증정보·민감값은 저장 전에 마스킹한다.",
            "역할이나 메뉴를 논리삭제하면 기존 관계 이력은 보존하되 다음 로그인부터 권한 계산에서 제외한다.",
            "공통코드 값은 운영 사용 후 변경하거나 재사용하지 않는다.",
        ),
    },
    "common": {
        "title": "공통 업무영역",
        "output": "02.common-management-erd.md",
        "intro": "여러 업무영역에서 재사용하는 알림, 업무대상, 첨부파일 및 메모의 PostgreSQL 물리 모델을 표현한다.",
        "sections": (
            ("알림", "사용자별 업무 이벤트 알림과 읽음 상태를 관리하는 구조이다.", ("TB_COM_NOTI",)),
            ("업무대상·첨부파일·메모", "공통 업무대상을 중심으로 파일과 메모를 연결하는 범용 구조이다.", ("TB_COM_TASK_TGT", "TB_COM_FILE", "TB_COM_MEMO")),
        ),
        "traceability": (
            ("BFD-02-01", "대시보드", "업무영역별 원천 데이터의 파생 조회"),
            ("BFD-02-02", "알림관리", "TB_COM_NOTI"),
            ("BFD-02-03", "첨부파일관리", "TB_COM_TASK_TGT, TB_COM_FILE"),
            ("BFD-02-04", "메모관리", "TB_COM_TASK_TGT, TB_COM_MEMO"),
            ("BFD-02-05", "인증관리", "TB_SYS_USER, TB_SYS_ACCESS_LOG"),
        ),
        "overview": (
            '    TB_SYS_USER ||--o{ TB_COM_NOTI : "APP REF: RCVR_ID"',
            '    TB_COM_TASK_TGT ||--o{ TB_COM_FILE : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o{ TB_COM_MEMO : "DB FK: TASK_TGT_ID"',
            '    TB_SYS_USER ||--o{ TB_COM_MEMO : "APP REF: WRTR_ID"',
            '    TB_COM_TASK_TGT ||--o| CONTRACT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| PROJECT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_OPPTY : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| CONTRACT_DOCUMENT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_ANNC : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_PRPSL : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_RES_MST : "DB FK: TASK_TGT_ID"',
            '    TB_COM_FILE ||--o| CONTRACT_DOCUMENT_LOGICAL : "LOGICAL REF: PDF_FILE_ID"',
        ),
        "overview_note": "계약·프로젝트·계약문서는 아직 물리 모델이 확정되지 않아 `*_LOGICAL`로 표시했다. 영업기회·사업공고·제안·인력의 물리 테이블은 `TASK_TGT_ID`를 필수·고유 참조한다. 업무대상 유형과 실제 연결 엔터티의 일치는 애플리케이션 트랜잭션에서 검증한다.",
        "notes": (
            "업무대상은 허용된 구체 엔터티 하나와만 연결하고 유형코드와 실제 엔터티의 일치를 애플리케이션에서 검증한다.",
            "논리삭제된 업무대상에는 새 첨부파일과 메모를 등록하지 않으며 기존 데이터는 보존 정책에 따라 조회한다.",
            "첨부파일은 하나의 업무대상에만 소속하며 등록 후 업무대상ID를 변경하지 않는다.",
            "파일경로와 저장파일명은 시스템이 생성하고 크기·확장자·실제 형식 및 정규화된 저장경로를 검증한다.",
            "계약문서의 PDF 첨부파일과 계약문서는 동일한 업무대상ID를 사용해야 한다.",
            "알림은 수신자 본인만 조회·읽음 처리하고 메모 수정·삭제는 작성자 또는 관리권한 보유자만 수행한다.",
        ),
    },
    "customer": {
        "title": "고객관리",
        "output": "03.customer-management-erd.md",
        "intro": "고객사, 고객담당자 및 고객 대상 영업활동의 PostgreSQL 물리 모델을 표현한다.",
        "sections": (
            ("고객사·고객담당자", "고객사 기준정보와 소속 담당자의 상태 및 연락정보를 관리하는 구조이다.", ("TB_CUST_MST", "TB_CUST_CONTACT")),
            ("영업활동", "고객사, 선택 고객담당자 및 BMS 담당사용자를 연결하여 고객 접촉 이력을 관리하는 구조이다.", ("TB_CUST_ACTIVITY",)),
        ),
        "traceability": (
            ("BFD-03-01", "고객사관리", "TB_CUST_MST"),
            ("BFD-03-02", "고객담당자관리", "TB_CUST_CONTACT"),
            ("BFD-03-03", "영업활동관리", "TB_CUST_ACTIVITY"),
        ),
        "overview": (
            '    TB_CUST_MST ||--o{ TB_CUST_CONTACT : "APP REF: CUST_ID"',
            '    TB_CUST_MST ||--o{ TB_CUST_ACTIVITY : "APP REF: CUST_ID"',
            '    TB_CUST_CONTACT o|--o{ TB_CUST_ACTIVITY : "APP REF: CUST_CONTACT_ID"',
            '    TB_SYS_USER ||--o{ TB_CUST_ACTIVITY : "APP REF: ACT_CHRG_USER_ID"',
            '    TB_CUST_MST ||--o{ TB_SALES_OPPTY : "APP REF: CUST_ID"',
            '    TB_CUST_MST ||--o{ CONTRACT_LOGICAL : "LOGICAL REF: CUST_ID"',
        ),
        "overview_note": "`TB_SYS_USER`는 시스템관리, `TB_SALES_OPPTY`는 영업관리 영역의 물리 테이블이다. 계약은 아직 물리 모델이 확정되지 않아 `CONTRACT_LOGICAL`로 표시했다.",
        "notes": (
            "고객사와 고객담당자는 `ACTIVE`를 기본 상태로 등록하며 비활성 상태와 논리삭제를 구분한다.",
            "사업자등록번호는 값이 있을 때 삭제 여부와 관계없이 재사용할 수 없다.",
            "영업활동에 고객담당자를 지정하면 해당 담당자가 활동 고객사에 소속되어 있는지 애플리케이션에서 검증한다.",
            "영업활동 등록 시 고객사, 활동유형, 활동일시 및 활동담당사용자를 필수로 입력한다.",
            "비활성 고객사와 담당자는 신규 업무 선택에서 제외하지만 기존 활동과 후속 업무 이력에서는 조회할 수 있다.",
            "대표자명·연락처·이메일·주소 등 개인정보는 권한과 마스킹 정책을 적용한다.",
            "활동일시는 UTC로 저장하고 화면에서 `Asia/Seoul`로 변환한다.",
        ),
    },
    "sales": {
        "title": "영업관리",
        "output": "04.sales-management-erd.md",
        "intro": "사업공고 수집·검토부터 영업기회, 사업성분석, 제안 및 수주까지의 PostgreSQL 물리 모델을 표현한다.",
        "sections": (
            ("사업공고", "수동·자동수집 사업공고와 최초·재공고 관계 및 검토·입찰결과를 관리하는 구조이다.", ("TB_SALES_ANNC",)),
            ("수집조건", "수집조건별 발주기관과 포함·제외 키워드를 관리하는 구조이다.", ("TB_SALES_ANNC_COLLECT_COND", "TB_SALES_ANNC_COLLECT_TGT_INST", "TB_SALES_ANNC_COLLECT_KWD")),
            ("수집처리이력", "수집 실행, 불변 원문, 조달청 원본 응답 필드 및 사업공고 반영 결과를 단계별로 보존하는 구조이다.", ("TB_SALES_ANNC_COLLECT_HIST", "TB_SALES_ANNC_COLLECT_ORGNL", "TB_SALES_ANNC_REL_RCV")),
            ("영업기회·사업성", "사업공고 전환 관계, 고객별 영업기회 및 영업기회별 사업성분석을 관리하는 구조이다.", ("TB_SALES_ANNC_OPPTY_REL", "TB_SALES_OPPTY", "TB_SALES_FEAS_ANALYSIS")),
            ("제안·수주", "사업공고별 단일 제안과 제안별 단일 수주 결과를 관리하는 구조이다.", ("TB_SALES_PRPSL", "TB_SALES_ORDER_RCV")),
        ),
        "traceability": (
            ("BFD-04-01", "사업공고관리", "TB_SALES_ANNC, TB_SALES_ANNC_COLLECT_*, TB_SALES_ANNC_REL_RCV, TB_SALES_ANNC_OPPTY_REL"),
            ("BFD-04-02", "영업기회관리", "TB_SALES_OPPTY, TB_SALES_ANNC_OPPTY_REL"),
            ("BFD-04-03", "사업성분석", "TB_SALES_FEAS_ANALYSIS"),
            ("BFD-04-04", "제안관리", "TB_SALES_PRPSL"),
            ("BFD-04-05", "수주관리", "TB_SALES_ORDER_RCV"),
        ),
        "overview": (
            '    TB_SALES_ANNC_COLLECT_COND ||--o{ TB_SALES_ANNC_COLLECT_TGT_INST : "APP REF: COLLECT_COND_ID"',
            '    TB_SALES_ANNC_COLLECT_COND ||--o{ TB_SALES_ANNC_COLLECT_KWD : "APP REF: COLLECT_COND_ID"',
            '    TB_SALES_ANNC_COLLECT_COND ||--o{ TB_SALES_ANNC_COLLECT_HIST : "APP REF: COLLECT_COND_ID"',
            '    TB_SALES_ANNC_COLLECT_HIST ||--o{ TB_SALES_ANNC_COLLECT_ORGNL : "APP REF: COLLECT_HIST_ID"',
            '    TB_SALES_ANNC_COLLECT_ORGNL ||--o| TB_SALES_ANNC_REL_RCV : "APP REF: COLLECT_ORGNL_ID"',
            '    TB_SALES_ANNC o|--o{ TB_SALES_ANNC_REL_RCV : "APP REF: ANNC_ID"',
            '    TB_SALES_ANNC o|--o{ TB_SALES_ANNC : "DB FK: ORGNL_ANNC_ID"',
            '    TB_SALES_ANNC ||--o| TB_SALES_ANNC_OPPTY_REL : "DB FK: ANNC_ID"',
            '    TB_SALES_OPPTY ||--o{ TB_SALES_ANNC_OPPTY_REL : "DB FK: SALES_OPPTY_ID"',
            '    TB_CUST_MST ||--o{ TB_SALES_OPPTY : "APP REF: CUST_ID"',
            '    TB_SALES_OPPTY ||--o| TB_SALES_FEAS_ANALYSIS : "DB FK: SALES_OPPTY_ID"',
            '    TB_SALES_ANNC ||--o| TB_SALES_PRPSL : "DB FK: ANNC_ID"',
            '    TB_SALES_PRPSL ||--o| TB_SALES_ORDER_RCV : "DB FK: PRPSL_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_ANNC : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_OPPTY : "DB FK: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| TB_SALES_PRPSL : "DB FK: TASK_TGT_ID"',
            '    TB_SALES_ANNC_COLLECT_HIST ||--o{ TB_SYS_API_CALL_LOG : "APP REF: COLLECT_HIST_ID"',
            '    TB_SALES_ORDER_RCV ||--o| CONTRACT_LOGICAL : "LOGICAL REF: ORD_RCV_ID"',
        ),
        "overview_note": "`TB_CUST_MST`는 고객관리, `TB_COM_TASK_TGT`는 공통, `TB_SYS_API_CALL_LOG`는 시스템관리 영역의 물리 테이블이다. 계약은 아직 물리 모델이 확정되지 않아 `CONTRACT_LOGICAL`로 표시했다.",
        "notes": (
            "사업공고·영업기회·제안은 각각 업무대상과 동일 트랜잭션에서 생성하고 `TASK_TGT_ID`를 필수·고유 참조한다.",
            "입찰공고번호와 차수는 값이 있을 때 전체 이력에서 고유하며 재공고는 별도 사업공고로 생성해 원공고ID로 연결한다.",
            "수집 원문과 연계수신은 사업공고 반영 전에 보존하고 외부 응답 필드명과 대소문자를 변경하지 않는다.",
            "정기수집과 즉시수집의 동일 조건 동시 실행을 잠그고 전체 페이지 성공 시에만 마지막 성공 수집일시를 갱신한다.",
            "사업공고 반영 실패는 저장된 연계수신값으로 재처리하며 불필요한 외부 API 재호출을 하지 않는다.",
            "공고별 영업기회 전환, 공고별 제안, 영업기회별 활성 사업성분석 및 제안별 수주 결과의 유일성을 보장한다.",
            "수주여부가 `Y`이면 수주금액과 수주일자가 필수이고 `N`이면 두 값을 저장하지 않는다.",
            "서비스키·인증 헤더·민감한 쿼리 값은 원문과 로그에 저장하기 전에 제거하거나 마스킹한다.",
        ),
    },
    "employee": {
        "title": "인력관리",
        "output": "07.resource-management-erd.md",
        "intro": "직원과 외주인력의 공통 인력정보, 이력·역량·단가 및 프로젝트 투입계획·실적의 PostgreSQL 물리 모델을 표현한다.",
        "sections": (
            ("인력·직원·외주인력", "인력 공통 마스터와 공유 PK 방식의 직원·외주인력 하위유형 및 협력업체를 관리하는 구조이다.", ("TB_RES_MST", "TB_RES_EMPLOYEE", "TB_RES_OUTSRC", "TB_RES_PARTNER_VENDOR")),
            ("경력·학력·교육·상훈", "인력별 복합 PK 순번으로 과거 근무경력, 학력, 교육 및 상훈 이력을 관리하는 구조이다.", ("TB_RES_WORK_CAREER", "TB_RES_ACAD", "TB_RES_EDU", "TB_RES_AWARD")),
            ("기술·단가", "인력의 기술자격·외부 사업 기술경력과 기준연월별 단가를 관리하는 구조이다.", ("TB_RES_SKILL_QUAL", "TB_RES_SKILL_CAREER", "TB_RES_UNIT_AMT")),
            ("프로젝트 투입·월별실적", "프로젝트별 인력 투입기간과 월별 계획·실적 투입률 및 투입MM을 관리하는 구조이다.", ("TB_RES_ASSIGN", "TB_RES_MTHLY_ASSIGN_ACTL")),
        ),
        "traceability": (
            ("BFD-07-01", "직원관리", "TB_RES_MST, TB_RES_EMPLOYEE, TB_RES_WORK_CAREER, TB_RES_ACAD, TB_RES_EDU, TB_RES_AWARD, TB_RES_SKILL_QUAL, TB_RES_SKILL_CAREER"),
            ("BFD-07-03", "단가관리", "TB_RES_UNIT_AMT"),
            ("BFD-07-04", "인력투입관리", "TB_RES_ASSIGN"),
            ("BFD-07-05", "투입률관리", "TB_RES_MTHLY_ASSIGN_ACTL"),
            ("BFD-07-06", "협력업체관리", "TB_RES_PARTNER_VENDOR"),
            ("BFD-07-07", "외주인력관리", "TB_RES_MST, TB_RES_OUTSRC"),
        ),
        "overview": (
            '    TB_COM_TASK_TGT ||--o| TB_RES_MST : "DB FK: TASK_TGT_ID"',
            '    TB_RES_MST ||--o| TB_RES_EMPLOYEE : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o| TB_RES_OUTSRC : "DB FK: RES_ID"',
            '    TB_SYS_ORG ||--o{ TB_RES_EMPLOYEE : "APP REF: ORG_ID"',
            '    TB_RES_EMPLOYEE o|--o| TB_SYS_USER : "APP REF: EMP_ID"',
            '    TB_RES_PARTNER_VENDOR ||--o{ TB_RES_OUTSRC : "APP REF: PARTNER_VENDOR_ID"',
            '    TB_RES_MST ||--o{ TB_RES_WORK_CAREER : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_ACAD : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_EDU : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_AWARD : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_SKILL_QUAL : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_SKILL_CAREER : "DB FK: RES_ID"',
            '    TB_RES_MST ||--o{ TB_RES_UNIT_AMT : "DB FK: RES_ID"',
            '    PROJECT_LOGICAL ||--o{ TB_RES_ASSIGN : "LOGICAL REF: PRJ_ID"',
            '    TB_RES_MST ||--o{ TB_RES_ASSIGN : "DB FK: RES_ID"',
            '    TB_RES_ASSIGN ||--o{ TB_RES_MTHLY_ASSIGN_ACTL : "DB FK: RES_ASSIGN_ID"',
            '    TB_SYS_USER o|--o{ TB_RES_MTHLY_ASSIGN_ACTL : "APP REF: ACTL_CNFM_ID"',
        ),
        "overview_note": "`TB_COM_TASK_TGT`는 공통, `TB_SYS_ORG`와 `TB_SYS_USER`는 시스템관리 영역의 물리 테이블이다. 프로젝트는 아직 물리 모델이 확정되지 않아 `PROJECT_LOGICAL`로 표시했다.",
        "notes": (
            "인력 등록 시 인력 업무대상과 인력 마스터를 동일 트랜잭션에서 생성하고 `TASK_TGT_ID`를 필수·고유 참조한다.",
            "인력구분에 맞는 직원 또는 외주인력 상세 중 하나만 공유 PK로 생성하며 두 하위유형의 동시 존재를 차단한다.",
            "사람 사용자 계정은 내부 직원만 선택적으로 연결하고 외주인력에는 사용자 계정을 생성하지 않는다.",
            "사원번호와 값이 있는 협력업체 사업자등록번호는 삭제 여부와 관계없이 재사용하지 않는다.",
            "같은 프로젝트·인력의 활성 투입기간 중복을 차단하고 서로 다른 프로젝트의 동시 투입은 월간 총투입률 상한으로 통제한다.",
            "월별 계획·실적의 기준연월은 투입기간 안에 있어야 하며 인력별 월간 계획·실적 투입률 합계가 설정 상한을 초과할 수 없다.",
            "확정된 월별 실적은 직접 수정하지 않고 권한이 있는 사용자의 확정취소 절차를 거친다.",
            "생년월일·휴대전화번호·이메일주소 등 개인정보는 접근권한, 마스킹 및 보존기간 정책을 적용한다.",
        ),
    },
}


def read_csv(path: Path) -> list[dict[str, str]]:
    # 기존 카탈로그에는 UTF-8 BOM이 있는 파일과 없는 파일이 함께 존재한다.
    with path.open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def mermaid_type(data_type: str) -> str:
    """Mermaid ER 속성 타입을 파서에 안전한 토큰으로 바꾼다."""
    return (
        data_type.replace("(", "_")
        .replace(")", "")
        .replace(",", "_")
        .replace(" ", "_")
    )


def markdown_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ") if value else "-"


def mermaid_comment(value: str) -> str:
    return value.replace('"', "'").replace("\n", " ")


def relation_cardinality(
    constraint: dict[str, str],
    columns_by_table: dict[str, list[dict[str, str]]],
    unique_indexes: dict[str, set[tuple[str, ...]]],
) -> str:
    child = constraint["table_name"]
    local_columns = tuple(constraint["column_names"].split("|"))
    definitions = {row["column_name"]: row for row in columns_by_table[child]}
    parent_side = "o|" if any(definitions[name]["nullable"] == "Y" for name in local_columns) else "||"
    child_side = "o|" if local_columns in unique_indexes[child] else "o{"
    return f"{parent_side}--{child_side}"


def build_diagram(
    table_names: tuple[str, ...],
    table_labels: dict[str, str],
    columns_by_table: dict[str, list[dict[str, str]]],
    fk_columns: dict[str, set[str]],
    constraints: list[dict[str, str]],
    unique_indexes: dict[str, set[tuple[str, ...]]],
    data_types: dict[str, str],
) -> list[str]:
    lines = ["```mermaid", "erDiagram"]

    relevant_fks = [
        row
        for row in constraints
        if row["constraint_type"] == "FK" and row["table_name"] in table_names
    ]
    external_references: dict[str, set[str]] = defaultdict(set)
    for row in relevant_fks:
        if row["reference_table"] not in table_names:
            external_references[row["reference_table"]].update(row["reference_columns"].split("|"))

    for table_name in table_names:
        lines.append(f"    {table_name} {{")
        for column in columns_by_table[table_name]:
            data_type = column["data_type_override"] or data_types[column["domain_name"]]
            keys: list[str] = []
            if column["pk_yn"] == "Y":
                keys.append("PK")
            if column["column_name"] in fk_columns[table_name]:
                keys.append("FK")
            key_text = f" {','.join(keys)}" if keys else ""
            required = "NULL" if column["nullable"] == "Y" else "NOT NULL"
            default = f", DEFAULT {column['default_value']}" if column["default_value"] else ""
            comment = mermaid_comment(f"{column['korean_name']}; {required}{default}")
            lines.append(
                f"        {mermaid_type(data_type)} {column['column_name']}{key_text} \"{comment}\""
            )
        lines.append("    }")

    for reference_table in sorted(external_references):
        safe_table = LOGICAL_TABLE_ALIASES.get(reference_table, reference_table)
        lines.append(f"    {safe_table} {{")
        for reference_column in sorted(external_references[reference_table]):
            safe_column = LOGICAL_COLUMN_ALIASES.get(reference_column, reference_column)
            lines.append(f'        ID {safe_column} PK "외부 참조 식별자"')
        lines.append("    }")

    for row in relevant_fks:
        parent = LOGICAL_TABLE_ALIASES.get(row["reference_table"], row["reference_table"])
        enforcement = "DB FK" if row["enforcement_type"] == "DATABASE" else "APP REF"
        cardinality = relation_cardinality(row, columns_by_table, unique_indexes)
        lines.append(
            f'    {parent} {cardinality} {row["table_name"]} : "{enforcement}: {row["column_names"]}"'
        )

    lines.append("```")
    return lines


def generate_area(area: str) -> Path:
    config = AREA_CONFIGS[area]
    table_file = DATA_ROOT / f"03.physical-model/tables/table-{area}.csv"
    column_file = DATA_ROOT / f"03.physical-model/columns/column-{area}.csv"
    constraint_file = DATA_ROOT / f"03.physical-model/constraints/constraint-{area}.csv"
    index_file = DATA_ROOT / f"03.physical-model/indexes/index-{area}.csv"
    output = DATA_ROOT / "04.model-diagrams/physical" / config["output"]

    tables = read_csv(table_file)
    columns = read_csv(column_file)
    constraints = read_csv(constraint_file)
    indexes = read_csv(index_file)
    type_rows = read_csv(TYPE_FILE)

    table_labels = {row["table_name"]: row["entity_name"] for row in tables}
    columns_by_table: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in columns:
        columns_by_table[row["table_name"]].append(row)
    for rows in columns_by_table.values():
        rows.sort(key=lambda item: int(item["column_sequence"]))

    data_types = {
        row["domain_name"]: row["data_type"]
        for row in type_rows
        if row["dbms"] == "PostgreSQL"
    }
    fk_columns: dict[str, set[str]] = defaultdict(set)
    for row in constraints:
        if row["constraint_type"] == "FK":
            fk_columns[row["table_name"]].update(row["column_names"].split("|"))

    unique_indexes: dict[str, set[tuple[str, ...]]] = defaultdict(set)
    for row in indexes:
        if row["unique_yn"] == "Y":
            unique_indexes[row["table_name"]].add(tuple(row["column_names"].split("|")))
    for row in constraints:
        if row["constraint_type"] in {"PK", "UK"}:
            unique_indexes[row["table_name"]].add(tuple(row["column_names"].split("|")))

    lines = [
        f"<!-- 이 파일은 python scripts/generate_erd.py --area {area} 명령으로 생성합니다. 직접 수정하지 마십시오. -->",
        f"# {config['title']} 상세 ERD",
        "",
        "## 1. 문서 개요",
        "",
        f"{config['intro']} 원본은 데이터 카탈로그 CSV이며 이 문서는 구현과 리뷰를 위한 파생 산출물이다.",
        "",
        "- 기준 DBMS: PostgreSQL",
        f"- 범위: {config['title']} {len(tables)}개 테이블",
        "- 표기: `PK`는 기본키, `FK`는 논리 참조 컬럼, `DB FK`는 DB 제약 집행, `APP REF`는 애플리케이션 집행, `LOGICAL REF`는 상대 영역 물리화 전 논리 관계",
        "- 타입 표기: Mermaid 호환을 위해 `VARCHAR(26)`은 `VARCHAR_26`, `CHAR(1)`은 `CHAR_1`처럼 괄호를 밑줄로 표시",
        "- 카디널리티: `||` 필수 1, `o|` 선택 1, `o{` 0개 이상",
        "",
        "### 1.1 원본 카탈로그",
        "",
        f"- 테이블: `03.physical-model/tables/table-{area}.csv`",
        f"- 컬럼: `03.physical-model/columns/column-{area}.csv`",
        f"- 제약조건: `03.physical-model/constraints/constraint-{area}.csv`",
        f"- 인덱스: `03.physical-model/indexes/index-{area}.csv`",
        "- 타입 매핑: `01.standard/db-type-mapping.csv`",
        "",
        "### 1.2 업무기능 추적성",
        "",
        "| 기능 ID | 업무기능 | 주요 테이블 |",
        "| --- | --- | --- |",
    ]
    lines.extend(
        f"| {feature_id} | {feature} | {table_names} |"
        for feature_id, feature, table_names in config["traceability"]
    )

    lines.extend(
        [
            "",
            "## 2. 전체 관계 개요",
            "",
            "```mermaid",
            "erDiagram",
        ]
    )
    lines.extend(config["overview"])
    lines.extend(
        [
            "```",
            "",
            f"> {config['overview_note']}",
            "",
            "## 3. 영역별 상세 ERD",
        ]
    )

    for section_number, (title, description, table_names) in enumerate(config["sections"], start=1):
        lines.extend(["", f"### 3.{section_number} {title}", "", description, ""])
        lines.extend(
            build_diagram(
                table_names,
                table_labels,
                columns_by_table,
                fk_columns,
                constraints,
                unique_indexes,
                data_types,
            )
        )
        lines.extend(["", "테이블 대응:"])
        for table_name in table_names:
            lines.append(f"- `{table_name}`: {table_labels[table_name]}")

    fk_rows = [row for row in constraints if row["constraint_type"] == "FK"]
    check_rows = [row for row in constraints if row["constraint_type"] == "CK"]
    lines.extend(
        [
            "",
            "## 4. 관계 구현 명세",
            "",
            "| 관계명 | 자식 컬럼 | 부모 | 집행 | 생성 | 삭제/수정 | 설명 |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in fk_rows:
        parent = f"{row['reference_table']}.{row['reference_columns']}"
        action = f"{row['on_delete']}/{row['on_update']}"
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    row["constraint_name"],
                    f"{row['table_name']}.{row['column_names']}",
                    parent,
                    row["enforcement_type"],
                    row["create_yn"],
                    action,
                    row["description"],
                )
            )
            + " |"
        )

    lines.extend(
        [
            "",
            "## 5. 업무 무결성 규칙",
            "",
            "| 제약조건 | 테이블 | 대상 컬럼 | 검사식 | 설명 |",
            "| --- | --- | --- | --- | --- |",
        ]
    )
    for row in check_rows:
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    row["constraint_name"],
                    row["table_name"],
                    row["column_names"],
                    f"`{row['check_expression']}`",
                    row["description"],
                )
            )
            + " |"
        )

    lines.extend(
        [
            "",
            "## 6. 조회 및 고유성 인덱스",
            "",
            "| 인덱스 | 테이블 | 컬럼 | 고유 | 조건 | 목적 |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in indexes:
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    row["index_name"],
                    row["table_name"],
                    row["column_names"],
                    row["unique_yn"],
                    row["where_condition"],
                    row["description"],
                )
            )
            + " |"
        )

    lines.extend(["", "## 7. 구현 주의사항", ""])
    lines.extend(f"- {note}" for note in config["notes"])
    lines.extend(
        [
            "",
            "## 8. 재생성",
            "",
            "```powershell",
            f"python scripts/generate_erd.py --area {area}",
            "```",
            "",
            "생성 후 전체 데이터 카탈로그 검증을 수행한다.",
            "",
            "```powershell",
            f"python scripts/validate_data_catalog.py --review-area {area} --report tmp/data-catalog-validation-{area}.csv",
            "```",
            "",
        ]
    )

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return output


def generate_overall_erd() -> Path:
    """물리화된 모든 업무영역의 관계 중심 통합 ERD를 생성한다."""
    physical_areas = tuple(
        area
        for area in AREA_CONFIGS
        if (DATA_ROOT / f"03.physical-model/tables/table-{area}.csv").exists()
    )
    output = DATA_ROOT / "04.model-diagrams/physical/00.overall-erd.md"

    tables_by_area: dict[str, list[dict[str, str]]] = {}
    columns_by_table: dict[str, list[dict[str, str]]] = defaultdict(list)
    constraints_by_area: dict[str, list[dict[str, str]]] = {}
    indexes_by_area: dict[str, list[dict[str, str]]] = {}
    table_area: dict[str, str] = {}
    unique_keys: dict[str, set[tuple[str, ...]]] = defaultdict(set)

    for area in physical_areas:
        tables = read_csv(DATA_ROOT / f"03.physical-model/tables/table-{area}.csv")
        columns = read_csv(DATA_ROOT / f"03.physical-model/columns/column-{area}.csv")
        constraints = read_csv(DATA_ROOT / f"03.physical-model/constraints/constraint-{area}.csv")
        indexes = read_csv(DATA_ROOT / f"03.physical-model/indexes/index-{area}.csv")
        tables_by_area[area] = tables
        constraints_by_area[area] = constraints
        indexes_by_area[area] = indexes
        for table in tables:
            table_area[table["table_name"]] = area
        for column in columns:
            columns_by_table[column["table_name"]].append(column)
        for index in indexes:
            if index["unique_yn"] == "Y":
                unique_keys[index["table_name"]].add(tuple(index["column_names"].split("|")))
        for constraint in constraints:
            if constraint["constraint_type"] in {"PK", "UK"}:
                unique_keys[constraint["table_name"]].add(tuple(constraint["column_names"].split("|")))

    all_fk_rows = [
        row
        for area in physical_areas
        for row in constraints_by_area[area]
        if row["constraint_type"] == "FK"
    ]

    def relation_line(row: dict[str, str]) -> str:
        parent = LOGICAL_TABLE_ALIASES.get(row["reference_table"], row["reference_table"])
        if row["reference_table"] in LOGICAL_TABLE_ALIASES:
            enforcement = "LOGICAL REF"
        else:
            enforcement = "DB FK" if row["enforcement_type"] == "DATABASE" else "APP REF"
        cardinality = relation_cardinality(row, columns_by_table, unique_keys)
        return f'    {parent} {cardinality} {row["table_name"]} : "{enforcement}: {row["column_names"]}"'

    cross_rows = [
        row
        for row in all_fk_rows
        if table_area.get(row["reference_table"]) != table_area.get(row["table_name"])
    ]
    total_tables = sum(len(rows) for rows in tables_by_area.values())
    total_columns = sum(len(rows) for rows in columns_by_table.values())
    total_constraints = sum(len(rows) for rows in constraints_by_area.values())
    total_indexes = sum(len(rows) for rows in indexes_by_area.values())

    lines = [
        "<!-- 이 파일은 python scripts/generate_erd.py --overview 명령으로 생성합니다. 직접 수정하지 마십시오. -->",
        "# BMS 전체 ERD",
        "",
        "## 1. 문서 개요",
        "",
        "현재 물리 모델이 확정된 모든 업무영역의 PostgreSQL 테이블과 관계를 통합하여 표현한다. 상세 컬럼은 영역별 ERD에서 확인하며, 아직 물리화되지 않은 업무영역은 논리 확장 경계로 구분한다.",
        "",
        f"- 물리화 범위: {len(physical_areas)}개 업무영역, {total_tables}개 테이블, {total_columns}개 컬럼",
        f"- 구현 메타데이터: {total_constraints}개 제약조건, {total_indexes}개 인덱스",
        "- 표기: `DB FK`는 데이터베이스 집행, `APP REF`는 애플리케이션 집행, `LOGICAL REF`는 상대 영역 물리화 전 논리 관계",
        "- 카디널리티: `||` 필수 1, `o|` 선택 1, `o{` 0개 이상",
        "",
        "## 2. 물리 모델 범위",
        "",
        "| 업무영역 | 테이블 | 컬럼 | 제약조건 | 인덱스 | 상세 ERD |",
        "| --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for area in physical_areas:
        area_columns = sum(len(columns_by_table[row["table_name"]]) for row in tables_by_area[area])
        detail_path = AREA_CONFIGS[area]["output"]
        lines.append(
            f"| {AREA_CONFIGS[area]['title']} | {len(tables_by_area[area])} | {area_columns} | "
            f"{len(constraints_by_area[area])} | {len(indexes_by_area[area])} | [{detail_path}]({detail_path}) |"
        )

    lines.extend(
        [
            "",
            "## 3. 업무영역 간 관계",
            "",
            "```mermaid",
            "erDiagram",
        ]
    )
    lines.extend(relation_line(row) for row in cross_rows)
    lines.extend(
        [
            "```",
            "",
            "영역 간 참조 중 로그 보존, 감사 속성, 선택적 사용자 참조와 미물리화 선행 참조는 애플리케이션에서 집행한다.",
            "",
            "## 4. 업무영역별 관계",
        ]
    )

    for sequence, area in enumerate(physical_areas, start=1):
        internal_rows = [
            row
            for row in all_fk_rows
            if table_area.get(row["table_name"]) == area
            and table_area.get(row["reference_table"]) == area
        ]
        related_tables = {
            name
            for row in internal_rows
            for name in (row["table_name"], row["reference_table"])
        }
        isolated_tables = [
            row["table_name"]
            for row in tables_by_area[area]
            if row["table_name"] not in related_tables
        ]
        lines.extend(
            [
                "",
                f"### 4.{sequence} {AREA_CONFIGS[area]['title']}",
                "",
                "```mermaid",
                "erDiagram",
            ]
        )
        lines.extend(relation_line(row) for row in internal_rows)
        lines.append("```")
        if isolated_tables:
            lines.extend(
                [
                    "",
                    "영역 내부 FK 관계가 없는 테이블: " + ", ".join(f"`{table}`" for table in isolated_tables),
                ]
            )

    lines.extend(
        [
            "",
            "## 5. 미물리화 업무영역의 논리 확장 경계",
            "",
            "계약·프로젝트·예산원가·매출수금 영역은 논리 엔터티가 정의되어 있으나 물리 테이블·컬럼·제약조건은 아직 확정되지 않았다. 다음 관계는 현재 논리 모델의 연결 방향이며 물리화 시 실제 테이블명과 FK 집행 방식을 확정한다.",
            "",
            "```mermaid",
            "erDiagram",
            '    TB_CUST_MST ||--o{ CONTRACT_LOGICAL : "LOGICAL REF: CUST_ID"',
            '    TB_SALES_ORDER_RCV ||--o| CONTRACT_LOGICAL : "LOGICAL REF: ORD_RCV_ID"',
            '    CONTRACT_LOGICAL ||--o{ PROJECT_LOGICAL : "LOGICAL REF"',
            '    TB_COM_TASK_TGT ||--o| CONTRACT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| PROJECT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_TASK_TGT ||--o| CONTRACT_DOCUMENT_LOGICAL : "LOGICAL REF: TASK_TGT_ID"',
            '    TB_COM_FILE ||--o| CONTRACT_DOCUMENT_LOGICAL : "LOGICAL REF: PDF_FILE_ID"',
            '    PROJECT_LOGICAL ||--o{ BUDGET_LOGICAL : "LOGICAL REF"',
            '    PROJECT_LOGICAL ||--o{ COST_LOGICAL : "LOGICAL REF"',
            '    PROJECT_LOGICAL ||--o{ PROFIT_LOSS_LOGICAL : "LOGICAL REF"',
            '    PROJECT_LOGICAL ||--o{ SALES_PLAN_LOGICAL : "LOGICAL REF"',
            '    PROJECT_LOGICAL ||--o{ BILLING_LOGICAL : "LOGICAL REF"',
            "```",
            "",
            "### 5.1 논리 엔터티 목록",
            "",
            "| 업무영역 | 논리 엔터티 |",
            "| --- | --- |",
        ]
    )
    entity_dir = DATA_ROOT / "02.logical-model/entities"
    for entity_file in sorted(entity_dir.glob("entity-*.csv")):
        area = entity_file.stem.removeprefix("entity-")
        if area in physical_areas:
            continue
        entities = read_csv(entity_file)
        entity_names = ", ".join(row["entity_name"] for row in entities) or "-"
        lines.append(f"| {area} | {entity_names} |")

    lines.extend(
        [
            "",
            "## 6. 전체 물리 테이블 목록",
            "",
            "| 업무영역 | 물리 테이블 | 논리 엔터티 | 유형 |",
            "| --- | --- | --- | --- |",
        ]
    )
    for area in physical_areas:
        for row in tables_by_area[area]:
            lines.append(
                f"| {AREA_CONFIGS[area]['title']} | `{row['table_name']}` | {row['entity_name']} | {row['table_type']} |"
            )

    lines.extend(
        [
            "",
            "## 7. 재생성",
            "",
            "```powershell",
            "# 전체 통합 ERD만 생성",
            "python scripts/generate_erd.py --overview",
            "",
            "# 전체 통합 ERD와 지원 영역별 상세 ERD 생성",
            "python scripts/generate_erd.py --all",
            "```",
            "",
            "생성 후 전체 데이터 카탈로그를 검증한다.",
            "",
            "```powershell",
            "python scripts/validate_data_catalog.py",
            "```",
            "",
        ]
    )

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return output


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="BMS 업무영역별 상세 물리 ERD를 생성합니다.")
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--area", choices=sorted(AREA_CONFIGS), help="생성할 업무영역")
    selection.add_argument("--all", action="store_true", help="전체 통합 ERD와 지원하는 모든 업무영역 생성")
    selection.add_argument("--overview", action="store_true", help="전체 통합 ERD 생성")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.overview:
        output = generate_overall_erd()
        print(f"GENERATED overview path={output}")
        return
    areas = AREA_CONFIGS if args.all else (args.area,)
    for area in areas:
        output = generate_area(area)
        print(f"GENERATED area={area} path={output}")
    if args.all:
        output = generate_overall_erd()
        print(f"GENERATED overview path={output}")


if __name__ == "__main__":
    main()

-- Initial roles approved in the permission matrix.
-- Human users, passwords and the bootstrap organization are intentionally excluded.

INSERT INTO tb_sys_role (
    role_id,
    role_nm,
    role_desc,
    reg_id,
    reg_dtm,
    mod_id,
    mod_dtm,
    del_yn
)
VALUES
    ('01KY3HYG000000000000000001', '시스템관리자', '시스템 기준정보와 운영 기능을 관리하는 역할', 'SYSTEM', CURRENT_TIMESTAMP, NULL, NULL, 'N'),
    ('01KY3HYG000000000000000002', '경영진', '전사 현황과 승인된 경영정보를 조회하는 역할', 'SYSTEM', CURRENT_TIMESTAMP, NULL, NULL, 'N'),
    ('01KY3HYG000000000000000003', '영업', '고객과 영업 업무를 처리하는 역할', 'SYSTEM', CURRENT_TIMESTAMP, NULL, NULL, 'N'),
    ('01KY3HYG000000000000000004', 'PM', '담당 프로젝트와 투입 인력을 관리하는 역할', 'SYSTEM', CURRENT_TIMESTAMP, NULL, NULL, 'N'),
    ('01KY3HYG000000000000000005', '일반사용자', '본인에게 허용된 대시보드와 업무를 조회하는 역할', 'SYSTEM', CURRENT_TIMESTAMP, NULL, NULL, 'N')
ON CONFLICT (role_id) DO UPDATE
SET role_nm = EXCLUDED.role_nm,
    role_desc = EXCLUDED.role_desc,
    mod_id = 'SYSTEM',
    mod_dtm = CURRENT_TIMESTAMP,
    del_yn = 'N';

-- Authentication fields were approved after the initial physical schema baseline.
-- Existing rows are normalized before the constraints are installed so upgrades
-- fail safely on duplicate normalized login IDs instead of keeping ambiguous data.

ALTER TABLE tb_sys_user
    ADD COLUMN temp_pwd_expire_dtm timestamp,
    ADD COLUMN sec_ver integer DEFAULT 1 NOT NULL;

UPDATE tb_sys_user
SET login_id = lower(btrim(login_id));

UPDATE tb_sys_user
SET temp_pwd_expire_dtm = pwd_chg_dtm + interval '24 hours'
WHERE pwd_init_req_yn = 'Y';

ALTER TABLE tb_sys_user
    ADD CONSTRAINT ck_tb_sys_user_05
        CHECK (
            (pwd_init_req_yn = 'Y'
                AND temp_pwd_expire_dtm IS NOT NULL
                AND temp_pwd_expire_dtm > pwd_chg_dtm)
            OR
            (pwd_init_req_yn = 'N' AND temp_pwd_expire_dtm IS NULL)
        ),
    ADD CONSTRAINT ck_tb_sys_user_06
        CHECK (login_id = lower(btrim(login_id))),
    ADD CONSTRAINT ck_tb_sys_user_07
        CHECK (sec_ver >= 1);

COMMENT ON COLUMN tb_sys_user.temp_pwd_expire_dtm IS
    '최초 등록 전용 임시 비밀번호 만료 일시';
COMMENT ON COLUMN tb_sys_user.sec_ver IS
    '권한 또는 자격 증명 변경 시 증가하는 세션 보안 버전';

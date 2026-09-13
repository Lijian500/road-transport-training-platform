-- 签到签退凭据绑定会话、操作及事件序号，避免抽验结果被复用。
ALTER TABLE study_session
    ADD COLUMN attendance_action VARCHAR(16) NULL,
    ADD COLUMN attendance_sequence BIGINT NULL,
    ADD COLUMN attendance_verified_at DATETIME(3) NULL;

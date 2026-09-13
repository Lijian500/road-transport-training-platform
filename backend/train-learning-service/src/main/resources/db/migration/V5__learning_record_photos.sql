-- 学习核验照片存入私有对象存储，学习库仅持有引用；历史记录保持为空。
ALTER TABLE study_session
    ADD COLUMN attendance_photo_object_id BIGINT NULL COMMENT '待消费的人脸验证照片对象ID',
    ADD COLUMN sign_in_photo_object_id BIGINT NULL COMMENT '签到照片对象ID',
    ADD COLUMN sign_out_photo_object_id BIGINT NULL COMMENT '签退照片对象ID';

ALTER TABLE face_check_log
    ADD COLUMN photo_object_id BIGINT NULL COMMENT '本次抽验提交照片对象ID';

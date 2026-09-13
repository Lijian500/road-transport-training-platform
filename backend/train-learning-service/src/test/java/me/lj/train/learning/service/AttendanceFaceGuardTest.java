package me.lj.train.learning.service;

import me.lj.train.learning.model.entity.StudySessionEntity;
import me.lj.train.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证签到签退凭据不能缺失、过期或跨操作和序号复用。 */
class AttendanceFaceGuardTest {
    @Test
    void rejectsMissingExpiredAndReusedProof() {
        LocalDateTime now = LocalDateTime.now();
        StudySessionEntity session = new StudySessionEntity();
        session.setFaceCheckEnabled(true);
        assertThatThrownBy(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_IN", 1, now))
                .isInstanceOf(BusinessException.class);
        session.setAttendanceAction("SIGN_IN");
        session.setAttendanceSequence(1L);
        session.setAttendanceVerifiedAt(now);
        assertThatCode(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_IN", 1, now))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_OUT", 1, now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_IN", 2, now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_IN", 1, now.plusSeconds(61)))
                .isInstanceOf(BusinessException.class);
    }

    /** 仅消费成功的对应事件才能将凭据照片归档。 */
    @Test
    void archivesPhotoOnlyForVerifiedAction() {
        LocalDateTime now = LocalDateTime.now();
        StudySessionEntity session = new StudySessionEntity();
        session.setFaceCheckEnabled(true);
        session.setAttendanceAction("SIGN_IN");
        session.setAttendanceSequence(1L);
        session.setAttendanceVerifiedAt(now);
        session.setAttendancePhotoObjectId(8L);
        assertThatThrownBy(() -> FaceCheckServiceImpl.requireAttendance(session, "SIGN_OUT", 1, now))
                .isInstanceOf(BusinessException.class);
        assertThat(session.getSignInPhotoObjectId()).isNull();
        assertThat(session.getSignOutPhotoObjectId()).isNull();
        FaceCheckServiceImpl.requireAttendance(session, "SIGN_IN", 1, now);
        assertThat(session.getSignInPhotoObjectId()).isEqualTo(8L);
        session.setAttendanceAction("SIGN_OUT");
        session.setAttendanceSequence(2L);
        session.setAttendancePhotoObjectId(9L);
        FaceCheckServiceImpl.requireAttendance(session, "SIGN_OUT", 2, now);
        assertThat(session.getSignOutPhotoObjectId()).isEqualTo(9L);
        assertThat(session.getSignInPhotoObjectId()).isEqualTo(8L);
    }

    @Test
    void doesNotRequireProofWhenPlanDisablesFaceChecks() {
        assertThatCode(() -> FaceCheckServiceImpl.requireAttendance(
                new StudySessionEntity(), "SIGN_IN", 1, LocalDateTime.now())).doesNotThrowAnyException();
    }
}

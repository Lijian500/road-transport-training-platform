package me.lj.train.learning.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.learning.mapper.*;
import me.lj.train.learning.model.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 学习档案所有权、零进度和零提交超时任务测试。 */
@ExtendWith(MockitoExtension.class)
class LearningRecordServiceImplTest {
    @Mock private PlatformTransactionManager transactions;
    @Mock private StudyProgressMapper progress;
    @Mock private StudySessionMapper sessions;
    @Mock private StudyEventLogMapper events;
    @Mock private FaceCheckTaskMapper faces;
    @Mock private FaceCheckLogMapper logs;
    @Mock private me.lj.train.learning.support.FaceReferenceImageClient images;
    private LearningRecordServiceImpl service;

    /** 设置只有本人档案权限的学员。 */
    @BeforeEach void setUp() {
        service = new LearningRecordServiceImpl(transactions, progress, sessions, events, faces, logs, images);
        LoginUser user = new LoginUser(); user.setEnterpriseId(20L); user.setUserId(10L);
        user.setPermissions(List.of("student:plan:view")); UserContext.set(user);
    }
    /** 清理上下文。 */
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test void shouldReturnNoProgressWithoutCreatingRows() {
        when(progress.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        assertThat(service.myCourses(1L).getData()).isEmpty();
        verify(progress, never()).insertSelective(any(StudyProgressEntity.class));
        verifyNoInteractions(transactions, sessions, events, faces, logs, images);
    }

    @Test void shouldRejectAnotherStudentsSessionBeforeReadingEvidence() {
        StudySessionEntity session = session(); session.setUserId(11L);
        when(sessions.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session);
        assertThat(service.myEvents(1L, 1, 10).getCode()).isEqualTo(AppErrorCode.RESOURCE_NOT_FOUND.getCode());
        verifyNoInteractions(events, faces, logs);
    }

    @Test void shouldRejectAnotherEnterpriseSession() {
        StudySessionEntity session = session(); session.setEnterpriseId(21L);
        when(sessions.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session);
        assertThat(service.myFaceChecks(1L, 1, 10).getCode()).isEqualTo(AppErrorCode.RESOURCE_NOT_FOUND.getCode());
        verifyNoInteractions(events, faces, logs);
    }

    @Test void shouldIncludeTimedOutFaceTaskWithoutAnySubmittedPhoto() {
        when(sessions.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session());
        FaceCheckTaskEntity task = new FaceCheckTaskEntity(); task.setId(3L); task.setStatus("TIMED_OUT");
        task.setAttemptCount(0); task.setFailureReason("DEADLINE_EXCEEDED");
        Page<FaceCheckTaskEntity> page = new Page<>(1, 10); page.setRecords(List.of(task)); page.setTotalRow(1);
        when(faces.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(page);
        when(logs.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        var result = service.myFaceChecks(1L, 1, 10);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords()).hasSize(1);
        assertThat(result.getData().getRecords().get(0).status()).isEqualTo("TIMED_OUT");
        assertThat(result.getData().getRecords().get(0).attempts()).isEmpty();
        verifyNoInteractions(transactions);
    }

    /** 返回各次抽验自己的照片，历史未留存记录保留空值。 */
    @Test void shouldIncludeEachAttemptPhoto() {
        when(sessions.selectOneByQuery(any(QueryWrapper.class))).thenReturn(session());
        FaceCheckTaskEntity task = new FaceCheckTaskEntity(); task.setId(3L);
        Page<FaceCheckTaskEntity> page = new Page<>(1, 10); page.setRecords(List.of(task)); page.setTotalRow(1);
        when(faces.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(page);
        FaceCheckLogEntity first = new FaceCheckLogEntity(); first.setTaskId(3L); first.setPhotoObjectId(8L);
        FaceCheckLogEntity second = new FaceCheckLogEntity(); second.setTaskId(3L);
        when(logs.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(first, second));
        when(images.learningPhotoUrl(8L)).thenReturn("https://example.test/photo");
        var result = service.myFaceChecks(1L, 1, 10);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords().get(0).attempts())
                .extracting(me.lj.train.api.learning.LearningRecordModels.FaceAttemptView::photoUrl)
                .containsExactly("https://example.test/photo", null);
    }

    /** 签到、签退照片分别映射到对应的会话字段。 */
    @Test void shouldIncludeAttendancePhotos() {
        StudySessionEntity row = session(); row.setSignInPhotoObjectId(8L); row.setSignOutPhotoObjectId(9L);
        Page<StudySessionEntity> page = new Page<>(1, 10); page.setRecords(List.of(row)); page.setTotalRow(1);
        when(sessions.paginate(anyInt(), anyInt(), any(QueryWrapper.class))).thenReturn(page);
        when(images.learningPhotoUrl(8L)).thenReturn("sign-in");
        when(images.learningPhotoUrl(9L)).thenReturn("sign-out");
        var result = service.mySessions(new me.lj.train.api.learning.LearningRecordModels.SessionQuery(
                1, 10, 1L, null, null, null));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRecords().get(0).signInPhotoUrl()).isEqualTo("sign-in");
        assertThat(result.getData().getRecords().get(0).signOutPhotoUrl()).isEqualTo("sign-out");
    }

    @Test void shouldRejectAdminEntryForStudent() {
        assertThat(service.adminCourses(1L).getCode()).isEqualTo(AppErrorCode.FORBIDDEN.getCode());
        verifyNoInteractions(progress);
    }

    @Test void shouldNotInterpretEmptyTaskIdsAsAllTasks() {
        assertThat(service.myDurations(List.of()).getData()).isEmpty();
        verifyNoInteractions(progress);
    }

    /** 构造当前学员已终止会话，历史查询不依赖活动状态。 */
    private StudySessionEntity session() {
        StudySessionEntity session = new StudySessionEntity(); session.setId(1L);
        session.setEnterpriseId(20L); session.setUserId(10L); session.setStatus("TERMINATED");
        return session;
    }
}

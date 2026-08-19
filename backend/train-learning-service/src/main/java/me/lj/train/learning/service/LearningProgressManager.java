package me.lj.train.learning.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.learning.LearningModels.CourseProgressView;
import me.lj.train.api.learning.LearningModels.CoursewareProgressView;
import me.lj.train.api.learning.LearningModels.PlanProgressView;
import me.lj.train.api.training.LearningAccessModels.LearningCourseRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningCoursewareRuleView;
import me.lj.train.api.training.LearningAccessModels.LearningTaskContextView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.learning.mapper.StudyCoursewareProgressMapper;
import me.lj.train.learning.mapper.StudyProgressMapper;
import me.lj.train.learning.model.entity.StudyCoursewareProgressEntity;
import me.lj.train.learning.model.entity.StudyProgressEntity;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.learning.model.table.StudyCoursewareProgressTableDef.STUDY_COURSEWARE_PROGRESS;
import static me.lj.train.learning.model.table.StudyProgressTableDef.STUDY_PROGRESS;

/**
 * 初始化并组装课程、课件学习进度。
 */
@Component
public class LearningProgressManager {

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    private final StudyProgressMapper progressMapper;
    private final StudyCoursewareProgressMapper coursewareProgressMapper;

    public LearningProgressManager(
            StudyProgressMapper progressMapper,
            StudyCoursewareProgressMapper coursewareProgressMapper) {
        this.progressMapper = progressMapper;
        this.coursewareProgressMapper = coursewareProgressMapper;
    }

    public void ensureProgress(
            Long enterpriseId, Long userId, LearningTaskContextView context) {
        for (LearningCourseRuleView rule : context.courses()) {
            StudyProgressEntity progress = progressMapper.selectOneByQuery(QueryWrapper.create()
                    .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                    .and(STUDY_PROGRESS.USER_ID.eq(userId))
                    .and(STUDY_PROGRESS.PLAN_ID.eq(context.planId()))
                    .and(STUDY_PROGRESS.PLAN_COURSE_ID.eq(rule.id())));
            if (progress == null) {
                progress = new StudyProgressEntity();
                progress.setId(IdGenerator.nextId());
                progress.setEnterpriseId(enterpriseId);
                progress.setUserId(userId);
                progress.setTaskId(context.taskId());
                progress.setPlanId(context.planId());
                progress.setPlanCourseId(rule.id());
                progress.setCourseName(rule.courseName());
                progress.setSortOrder(rule.sortOrder());
                progress.setRequiredDurationMs(rule.requiredDurationSeconds() * 1000L);
                progress.setAllowSeek(rule.allowSeek());
                progress.setProgressReportIntervalSeconds(rule.progressReportIntervalSeconds());
                progress.setStudyToleranceSeconds(rule.studyToleranceSeconds());
                progress.setStatus(NOT_STARTED);
                try {
                    progressMapper.insertSelective(progress);
                } catch (DuplicateKeyException ignored) {
                    // 同一学员并发首次进入时由数据库唯一约束保留先提交的快照进度。
                }
            }
            for (LearningCoursewareRuleView courseware : rule.coursewares()) {
                long count = coursewareProgressMapper.selectCountByQuery(QueryWrapper.create()
                        .where(STUDY_COURSEWARE_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                        .and(STUDY_COURSEWARE_PROGRESS.USER_ID.eq(userId))
                        .and(STUDY_COURSEWARE_PROGRESS.PLAN_COURSE_ID.eq(rule.id()))
                        .and(STUDY_COURSEWARE_PROGRESS.COURSEWARE_SNAPSHOT_ID.eq(courseware.id())));
                if (count == 0) {
                    StudyCoursewareProgressEntity value = new StudyCoursewareProgressEntity();
                    value.setId(IdGenerator.nextId());
                    value.setEnterpriseId(enterpriseId);
                    value.setUserId(userId);
                    value.setTaskId(context.taskId());
                    value.setPlanId(context.planId());
                    value.setPlanCourseId(rule.id());
                    value.setCoursewareSnapshotId(courseware.id());
                    value.setCoursewareTitle(courseware.title());
                    value.setSortOrder(courseware.sortOrder());
                    value.setDurationMs(courseware.durationSeconds() * 1000L);
                    value.setStatus(NOT_STARTED);
                    try {
                        coursewareProgressMapper.insertSelective(value);
                    } catch (DuplicateKeyException ignored) {
                        // 并发初始化相同课件时无需覆盖已存在的服务端进度。
                    }
                }
            }
        }
    }

    public StudyProgressEntity requireProgress(
            Long enterpriseId, Long userId, Long planId, Long planCourseId) {
        StudyProgressEntity progress = progressMapper.selectOneByQuery(QueryWrapper.create()
                .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                .and(STUDY_PROGRESS.USER_ID.eq(userId))
                .and(STUDY_PROGRESS.PLAN_ID.eq(planId))
                .and(STUDY_PROGRESS.PLAN_COURSE_ID.eq(planCourseId)));
        if (progress == null) {
            throw new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED);
        }
        return progress;
    }

    public List<StudyCoursewareProgressEntity> coursewares(
            Long enterpriseId, Long userId, Long planCourseId) {
        return coursewareProgressMapper.selectListByQuery(QueryWrapper.create()
                .where(STUDY_COURSEWARE_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                .and(STUDY_COURSEWARE_PROGRESS.USER_ID.eq(userId))
                .and(STUDY_COURSEWARE_PROGRESS.PLAN_COURSE_ID.eq(planCourseId))
                .orderBy(STUDY_COURSEWARE_PROGRESS.SORT_ORDER.asc(),
                        STUDY_COURSEWARE_PROGRESS.ID.asc()));
    }

    public boolean allTaskCoursesCompleted(Long enterpriseId, Long userId, Long taskId) {
        List<StudyProgressEntity> records = progressMapper.selectListByQuery(QueryWrapper.create()
                .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                .and(STUDY_PROGRESS.USER_ID.eq(userId))
                .and(STUDY_PROGRESS.TASK_ID.eq(taskId)));
        return !records.isEmpty() && records.stream()
                .allMatch(value -> COMPLETED.equals(value.getStatus()));
    }

    public PlanProgressView toPlanView(
            Long enterpriseId, Long userId, LearningTaskContextView context) {
        List<StudyProgressEntity> progressRecords = progressMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(STUDY_PROGRESS.ENTERPRISE_ID.eq(enterpriseId))
                        .and(STUDY_PROGRESS.USER_ID.eq(userId))
                        .and(STUDY_PROGRESS.TASK_ID.eq(context.taskId())));
        Map<Long, StudyProgressEntity> byCourse = progressRecords.stream()
                .collect(Collectors.toMap(StudyProgressEntity::getPlanCourseId, Function.identity()));
        List<CourseProgressView> courses = context.courses().stream()
                .map(rule -> toCourseView(byCourse.get(rule.id()), coursewares(
                        enterpriseId, userId, rule.id())))
                .toList();
        boolean learningCompleted = !courses.isEmpty()
                && courses.stream().allMatch(value -> COMPLETED.equals(value.status()));
        boolean pending = learningCompleted && !COMPLETED.equals(context.taskStudyStatus());
        return new PlanProgressView(
                context.taskId(), context.planId(), context.taskStudyStatus(),
                context.taskCompletionStatus(), pending, courses);
    }

    public CourseProgressView toCourseView(
            StudyProgressEntity progress, List<StudyCoursewareProgressEntity> coursewares) {
        if (progress == null) {
            throw new BusinessException(AppErrorCode.LEARNING_ACCESS_DENIED);
        }
        List<CoursewareProgressView> views = coursewares.stream()
                .map(value -> new CoursewareProgressView(
                        value.getCoursewareSnapshotId(), value.getCoursewareTitle(),
                        value.getSortOrder(), value.getDurationMs(),
                        value.getConfirmedPositionMs(), value.getMaxConfirmedPositionMs(),
                        value.getStatus(), value.getCompletedAt()))
                .toList();
        return new CourseProgressView(
                progress.getPlanCourseId(), progress.getCourseName(), progress.getSortOrder(),
                progress.getRequiredDurationMs(), progress.getEffectiveDurationMs(),
                progress.isAllowSeek(), progress.getProgressReportIntervalSeconds(),
                progress.getStudyToleranceSeconds(), progress.getStatus(), views,
                progress.getCompletedAt());
    }

    public StudyCoursewareProgressEntity requireCourseware(
            List<StudyCoursewareProgressEntity> values, Long snapshotId) {
        return values.stream().filter(value -> value.getCoursewareSnapshotId().equals(snapshotId))
                .findFirst().orElseThrow(() -> new BusinessException(
                        AppErrorCode.LEARNING_ACCESS_DENIED, "课件不属于当前计划课程"));
    }
}

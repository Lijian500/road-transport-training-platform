package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.api.training.PlanModels.PlanCourseView;
import me.lj.train.api.training.PlanModels.PlanCoursewareSnapshotView;
import me.lj.train.api.training.PlanModels.PlanUserView;
import me.lj.train.api.training.PlanModels.PlanView;
import me.lj.train.api.training.PlanModels.StudentPlanView;
import me.lj.train.api.training.PlanModels.StudentPlanCourseView;
import me.lj.train.api.training.PlanModels.StudentPlanCoursewareView;
import me.lj.train.training.mapper.PlanCourseMapper;
import me.lj.train.training.mapper.PlanCoursewareSnapshotMapper;
import me.lj.train.training.mapper.PlanUserMapper;
import me.lj.train.training.model.entity.PlanCourseEntity;
import me.lj.train.training.model.entity.PlanCoursewareSnapshotEntity;
import me.lj.train.training.model.entity.PlanEntity;
import me.lj.train.training.model.entity.PlanUserEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.lj.train.training.model.table.PlanCourseTableDef.PLAN_COURSE;
import static me.lj.train.training.model.table.PlanCoursewareSnapshotTableDef.PLAN_COURSEWARE_SNAPSHOT;
import static me.lj.train.training.model.table.PlanUserTableDef.PLAN_USER;

/**
 * 统一组装管理端计划与学员任务视图，确保两端读取同一份发布快照。
 */
@Component
public class PlanViewAssembler {

    private final PlanCourseMapper planCourseMapper;
    private final PlanCoursewareSnapshotMapper snapshotMapper;
    private final PlanUserMapper planUserMapper;

    public PlanViewAssembler(
            PlanCourseMapper planCourseMapper,
            PlanCoursewareSnapshotMapper snapshotMapper,
            PlanUserMapper planUserMapper) {
        this.planCourseMapper = planCourseMapper;
        this.snapshotMapper = snapshotMapper;
        this.planUserMapper = planUserMapper;
    }

    public PlanView toPlanView(PlanEntity plan, boolean includeDetails) {
        List<PlanCourseView> courses = includeDetails
                ? listCourseViews(plan.getId(), plan.getEnterpriseId())
                : Collections.emptyList();
        List<PlanUserView> users = includeDetails
                ? listUserViews(plan.getId(), plan.getEnterpriseId())
                : Collections.emptyList();
        return new PlanView(
                plan.getId(), plan.getPlanName(), plan.getDescription(),
                plan.getStartAt(), plan.getEndAt(), plan.getStatus(),
                plan.isExamRequired(), plan.getExamPassScore(), courses, users,
                plan.getPublishedAt(), plan.getCancelledAt(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }

    public StudentPlanView toStudentPlanView(
            PlanEntity plan, PlanUserEntity task, boolean includeCourses) {
        return new StudentPlanView(
                task.getId(), plan.getId(), plan.getPlanName(), plan.getDescription(),
                plan.getStartAt(), plan.getEndAt(), plan.getStatus(),
                task.getAssignmentStatus(), task.getStudyStatus(), task.getExamStatus(),
                task.getCompletionStatus(), includeCourses
                        ? listStudentCourseViews(plan.getId(), plan.getEnterpriseId())
                        : Collections.emptyList(),
                plan.getPublishedAt());
    }

    private List<StudentPlanCourseView> listStudentCourseViews(Long planId, Long enterpriseId) {
        List<PlanCourseEntity> courses = planCourseMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_COURSE.PLAN_ID.eq(planId))
                .orderBy(PLAN_COURSE.SORT_ORDER.asc(), PLAN_COURSE.ID.asc()));
        if (courses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> planCourseIds = courses.stream().map(PlanCourseEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<PlanCoursewareSnapshotEntity>> snapshotMap = snapshotMapper
                .selectListByQuery(QueryWrapper.create()
                        .where(PLAN_COURSEWARE_SNAPSHOT.ENTERPRISE_ID.eq(enterpriseId))
                        .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_COURSE_ID.in(planCourseIds))
                        .orderBy(PLAN_COURSEWARE_SNAPSHOT.SORT_ORDER.asc(),
                                PLAN_COURSEWARE_SNAPSHOT.ID.asc()))
                .stream().collect(Collectors.groupingBy(
                        PlanCoursewareSnapshotEntity::getPlanCourseId));
        return courses.stream().map(course -> new StudentPlanCourseView(
                course.getId(), course.getCourseName(), course.getRequiredDurationSeconds(),
                course.isAllowSeek(), course.getProgressReportIntervalSeconds(),
                course.getStudyToleranceSeconds(), course.getSortOrder(), snapshotMap
                        .getOrDefault(course.getId(), Collections.emptyList()).stream()
                        .map(snapshot -> new StudentPlanCoursewareView(
                                snapshot.getId(), snapshot.getCoursewareTitle(),
                                snapshot.getDurationSeconds(), snapshot.getSortOrder()))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    public List<PlanCourseView> listCourseViews(Long planId, Long enterpriseId) {
        List<PlanCourseEntity> courses = planCourseMapper.selectListByQuery(QueryWrapper.create()
                .where(PLAN_COURSE.ENTERPRISE_ID.eq(enterpriseId))
                .and(PLAN_COURSE.PLAN_ID.eq(planId))
                .orderBy(PLAN_COURSE.SORT_ORDER.asc(), PLAN_COURSE.ID.asc()));
        if (courses.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> planCourseIds = courses.stream()
                .map(PlanCourseEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<PlanCoursewareSnapshotEntity>> snapshotMap = snapshotMapper
                .selectListByQuery(QueryWrapper.create()
                        .where(PLAN_COURSEWARE_SNAPSHOT.ENTERPRISE_ID.eq(enterpriseId))
                        .and(PLAN_COURSEWARE_SNAPSHOT.PLAN_COURSE_ID.in(planCourseIds))
                        .orderBy(PLAN_COURSEWARE_SNAPSHOT.SORT_ORDER.asc(),
                                PLAN_COURSEWARE_SNAPSHOT.ID.asc()))
                .stream()
                .collect(Collectors.groupingBy(PlanCoursewareSnapshotEntity::getPlanCourseId));
        return courses.stream().map(course -> new PlanCourseView(
                course.getId(), course.getCourseId(), course.getCourseName(),
                course.getRequiredDurationSeconds(), course.isAllowSeek(),
                course.getProgressReportIntervalSeconds(), course.getStudyToleranceSeconds(),
                course.getSortOrder(), snapshotMap
                        .getOrDefault(course.getId(), Collections.emptyList())
                        .stream()
                        .map(this::toSnapshotView)
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    public List<PlanUserView> listUserViews(Long planId, Long enterpriseId) {
        return planUserMapper.selectListByQuery(QueryWrapper.create()
                        .where(PLAN_USER.ENTERPRISE_ID.eq(enterpriseId))
                        .and(PLAN_USER.PLAN_ID.eq(planId))
                        .orderBy(PLAN_USER.DISPLAY_NAME.asc(), PLAN_USER.ID.asc()))
                .stream()
                .map(task -> new PlanUserView(
                        task.getId(), task.getUserId(), task.getOrgId(), task.getOrgName(),
                        task.getUsername(), task.getDisplayName(), task.getAssignmentStatus(),
                        task.getStudyStatus(), task.getExamStatus(), task.getCompletionStatus(),
                        task.getCompletedAt()))
                .collect(Collectors.toList());
    }

    private PlanCoursewareSnapshotView toSnapshotView(PlanCoursewareSnapshotEntity snapshot) {
        return new PlanCoursewareSnapshotView(
                snapshot.getId(), snapshot.getSourceCoursewareId(), snapshot.getStorageObjectId(),
                snapshot.getCoursewareTitle(), snapshot.getDurationSeconds(), snapshot.getSortOrder());
    }
}

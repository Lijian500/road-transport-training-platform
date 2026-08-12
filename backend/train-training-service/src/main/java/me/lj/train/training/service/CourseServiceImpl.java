package me.lj.train.training.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.CourseModels.ChangeCourseStatusCommand;
import me.lj.train.api.training.CourseModels.CourseQuery;
import me.lj.train.api.training.CourseModels.CourseView;
import me.lj.train.api.training.CourseModels.CoursewareView;
import me.lj.train.api.training.CourseModels.CreateCourseCommand;
import me.lj.train.api.training.CourseModels.DeleteCoursewareCommand;
import me.lj.train.api.training.CourseModels.ReorderCoursewaresCommand;
import me.lj.train.api.training.CourseModels.UpdateCourseCommand;
import me.lj.train.api.training.CourseModels.UpdateCoursewareCommand;
import me.lj.train.api.training.CourseService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.page.PageRequest;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.COURSE_DISABLED;
import static me.lj.train.training.constant.TrainingConstants.COURSE_DRAFT;
import static me.lj.train.training.constant.TrainingConstants.COURSE_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_RETAINED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_CREATE;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_DELETE;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_STATUS;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_UPDATE;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_VIEW;
import static me.lj.train.training.constant.TrainingPermissions.COURSEWARE_MANAGE;
import static me.lj.train.training.model.table.CourseTableDef.COURSE;
import static me.lj.train.training.model.table.CoursewareTableDef.COURSEWARE;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;
import static me.lj.train.training.model.table.UploadSessionTableDef.UPLOAD_SESSION;

/**
 * 企业课程与课件RPC实现。
 */
@DubboService(timeout = 8000, retries = 0)
public class CourseServiceImpl extends TrainingServiceSupport implements CourseService {

    private final CourseMapper courseMapper;
    private final CoursewareMapper coursewareMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final UploadSessionMapper uploadSessionMapper;

    public CourseServiceImpl(
            PlatformTransactionManager transactionManager,
            CourseMapper courseMapper,
            CoursewareMapper coursewareMapper,
            StorageObjectMapper storageObjectMapper,
            UploadSessionMapper uploadSessionMapper) {
        super(transactionManager);
        this.courseMapper = courseMapper;
        this.coursewareMapper = coursewareMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.uploadSessionMapper = uploadSessionMapper;
    }

    @Override
    public Result<PageResult<CourseView>> page(CourseQuery query) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_VIEW);
            PageRequest request = query.toPageRequest();
            String keyword = trim(query.keyword());
            String status = normalizeOptionalStatus(query.status());
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(COURSE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(COURSE.DELETED_AT.isNull())
                    .and(COURSE.COURSE_NAME.like(keyword).when(hasText(keyword)))
                    .and(COURSE.STATUS.eq(status).when(hasText(status)))
                    .orderBy(COURSE.CREATED_AT.desc());
            Page<CourseEntity> page = courseMapper.paginate(
                    request.getPageNumber(), request.getPageSize(), wrapper);
            List<CourseView> records = page.getRecords().stream()
                    .map(course -> toView(course, false))
                    .collect(Collectors.toList());
            return PageResult.of(records, page.getTotalRow(), request);
        });
    }

    @Override
    public Result<CourseView> create(CreateCourseCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_CREATE);
            validateCourseRules(command.name(), command.description(),
                    command.requiredDurationSeconds(), command.progressReportIntervalSeconds(),
                    command.studyToleranceSeconds());
            Long operatorId = UserContext.require().getUserId();
            CourseEntity course = new CourseEntity();
            course.setId(IdGenerator.nextId());
            course.setEnterpriseId(enterpriseId);
            applyCourseValues(course, command.name(), command.description(),
                    command.requiredDurationSeconds(), command.allowSeek(),
                    command.progressReportIntervalSeconds(), command.studyToleranceSeconds());
            course.setStatus(COURSE_DRAFT);
            course.setEverEnabled(false);
            course.setCreatedBy(operatorId);
            course.setUpdatedBy(operatorId);
            courseMapper.insertSelective(course);
            return toView(requireCourse(course.getId(), enterpriseId), true);
        });
    }

    @Override
    public Result<CourseView> get(Long id) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_VIEW);
            return toView(requireCourse(id, enterpriseId), true);
        });
    }

    @Override
    public Result<CourseView> update(UpdateCourseCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_UPDATE);
            CourseEntity course = requireCourse(command.id(), enterpriseId);
            requireEditable(course);
            validateCourseRules(command.name(), command.description(),
                    command.requiredDurationSeconds(), command.progressReportIntervalSeconds(),
                    command.studyToleranceSeconds());
            UpdateWrapper<CourseEntity> update = UpdateWrapper.of(CourseEntity.class)
                    .set(COURSE.COURSE_NAME, TrainingGuard.requireText(command.name(), "课程名称", 128))
                    .set(COURSE.DESCRIPTION,
                            TrainingGuard.optionalText(command.description(), "课程简介", 1000))
                    .set(COURSE.REQUIRED_DURATION_SECONDS, command.requiredDurationSeconds())
                    .set(COURSE.ALLOW_SEEK, command.allowSeek())
                    .set(COURSE.PROGRESS_REPORT_INTERVAL_SECONDS,
                            command.progressReportIntervalSeconds())
                    .set(COURSE.STUDY_TOLERANCE_SECONDS, command.studyToleranceSeconds())
                    .set(COURSE.UPDATED_BY, UserContext.require().getUserId());
            courseMapper.updateByCondition(update.toEntity(), activeCourseCondition(course.getId(), enterpriseId));
            return toView(requireCourse(course.getId(), enterpriseId), true);
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_DELETE);
            CourseEntity course = requireCourse(id, enterpriseId);
            if (!COURSE_DRAFT.equals(course.getStatus())) {
                throw new BusinessException(AppErrorCode.COURSE_STATE_INVALID,
                        "只有草稿课程可以删除");
            }
            Long operatorId = UserContext.require().getUserId();
            LocalDateTime now = LocalDateTime.now();
            listCoursewares(course.getId(), enterpriseId).forEach(courseware -> {
                markObject(courseware.getStorageObjectId(), enterpriseId,
                        OBJECT_PENDING_DELETE, operatorId);
                softDeleteCourseware(courseware.getId(), enterpriseId, operatorId, now);
            });
            if (course.getCoverObjectId() != null) {
                markObject(course.getCoverObjectId(), enterpriseId,
                        OBJECT_PENDING_DELETE, operatorId);
            }
            CourseEntity deleted = new CourseEntity();
            deleted.setDeletedBy(operatorId);
            deleted.setDeletedAt(now);
            deleted.setUpdatedBy(operatorId);
            courseMapper.updateByCondition(deleted, activeCourseCondition(course.getId(), enterpriseId));
            UploadSessionEntityUpdate.expireCourseSessions(
                    uploadSessionMapper, enterpriseId, course.getId(), operatorId, now);
        });
    }

    @Override
    public Result<CourseView> changeStatus(ChangeCourseStatusCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_STATUS);
            CourseEntity course = requireCourse(command.id(), enterpriseId);
            String targetStatus = normalizeTargetStatus(command.status());
            if (COURSE_ENABLED.equals(targetStatus)) {
                if (!COURSE_DRAFT.equals(course.getStatus()) && !COURSE_DISABLED.equals(course.getStatus())) {
                    throw new BusinessException(AppErrorCode.COURSE_STATE_INVALID);
                }
                validateEnableConditions(course, enterpriseId);
            } else if (!COURSE_ENABLED.equals(course.getStatus())) {
                throw new BusinessException(AppErrorCode.COURSE_STATE_INVALID,
                        "只有启用中的课程可以禁用");
            }
            UpdateWrapper<CourseEntity> update = UpdateWrapper.of(CourseEntity.class)
                    .set(COURSE.STATUS, targetStatus)
                    .set(COURSE.EVER_ENABLED, course.isEverEnabled() || COURSE_ENABLED.equals(targetStatus))
                    .set(COURSE.UPDATED_BY, UserContext.require().getUserId());
            courseMapper.updateByCondition(update.toEntity(), activeCourseCondition(course.getId(), enterpriseId));
            return toView(requireCourse(course.getId(), enterpriseId), true);
        });
    }

    @Override
    public Result<CoursewareView> updateCourseware(UpdateCoursewareCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            CourseEntity course = requireCourse(command.courseId(), enterpriseId);
            requireEditable(course);
            CoursewareEntity courseware = requireCourseware(command.id(), course.getId(), enterpriseId);
            UpdateWrapper<CoursewareEntity> update = UpdateWrapper.of(CoursewareEntity.class)
                    .set(COURSEWARE.COURSEWARE_TITLE,
                            TrainingGuard.requireText(command.title(), "课件标题", 128))
                    .set(COURSEWARE.UPDATED_BY, UserContext.require().getUserId());
            coursewareMapper.updateByCondition(update.toEntity(), activeCoursewareCondition(
                    courseware.getId(), course.getId(), enterpriseId));
            return toCoursewareView(requireCourseware(command.id(), course.getId(), enterpriseId));
        });
    }

    @Override
    public Result<?> deleteCourseware(DeleteCoursewareCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            CourseEntity course = requireCourse(command.courseId(), enterpriseId);
            requireEditable(course);
            CoursewareEntity courseware = requireCourseware(command.id(), course.getId(), enterpriseId);
            Long operatorId = UserContext.require().getUserId();
            softDeleteCourseware(courseware.getId(), enterpriseId, operatorId, LocalDateTime.now());
            markObject(courseware.getStorageObjectId(), enterpriseId,
                    course.isEverEnabled() ? OBJECT_RETAINED : OBJECT_PENDING_DELETE,
                    operatorId);
        });
    }

    @Override
    public Result<?> reorderCoursewares(ReorderCoursewaresCommand command) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            CourseEntity course = requireCourse(command.courseId(), enterpriseId);
            requireEditable(course);
            List<CoursewareEntity> coursewares = listCoursewares(course.getId(), enterpriseId);
            List<Long> orderedIds = command.coursewareIds() == null
                    ? Collections.emptyList()
                    : command.coursewareIds().stream().filter(Objects::nonNull).collect(Collectors.toList());
            Set<Long> expectedIds = coursewares.stream()
                    .map(CoursewareEntity::getId).collect(Collectors.toSet());
            if (orderedIds.size() != expectedIds.size()
                    || orderedIds.stream().distinct().count() != orderedIds.size()
                    || !expectedIds.equals(orderedIds.stream().collect(Collectors.toSet()))) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID,
                        "排序列表必须包含课程全部有效课件且不能重复");
            }
            Long operatorId = UserContext.require().getUserId();
            for (int index = 0; index < orderedIds.size(); index++) {
                UpdateWrapper<CoursewareEntity> update = UpdateWrapper.of(CoursewareEntity.class)
                        .set(COURSEWARE.SORT_ORDER, index + 1)
                        .set(COURSEWARE.UPDATED_BY, operatorId);
                coursewareMapper.updateByCondition(update.toEntity(), activeCoursewareCondition(
                        orderedIds.get(index), course.getId(), enterpriseId));
            }
        });
    }

    @Override
    public Result<?> deleteCover(Long courseId) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            CourseEntity course = requireCourse(courseId, enterpriseId);
            requireEditable(course);
            if (course.getCoverObjectId() == null) {
                return;
            }
            Long operatorId = UserContext.require().getUserId();
            markObject(course.getCoverObjectId(), enterpriseId,
                    course.isEverEnabled() ? OBJECT_RETAINED : OBJECT_PENDING_DELETE,
                    operatorId);
            UpdateWrapper<CourseEntity> update = UpdateWrapper.of(CourseEntity.class)
                    .set(COURSE.COVER_OBJECT_ID, null)
                    .set(COURSE.UPDATED_BY, operatorId);
            courseMapper.updateByCondition(update.toEntity(), activeCourseCondition(course.getId(), enterpriseId));
        });
    }

    private void validateEnableConditions(CourseEntity course, Long enterpriseId) {
        List<CoursewareEntity> coursewares = listCoursewares(course.getId(), enterpriseId);
        long totalDuration = coursewares.stream().mapToLong(CoursewareEntity::getDurationSeconds).sum();
        if (coursewares.isEmpty()) {
            throw new BusinessException(AppErrorCode.COURSE_ENABLE_INVALID,
                    "启用前至少需要一个已就绪的视频课件");
        }
        if (course.getRequiredDurationSeconds() > totalDuration) {
            throw new BusinessException(AppErrorCode.COURSE_ENABLE_INVALID,
                    "规定时长不能超过有效视频总时长");
        }
        long activeUploads = uploadSessionMapper.selectCountByQuery(QueryWrapper.create()
                .where(UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId))
                .and(UPLOAD_SESSION.COURSE_ID.eq(course.getId()))
                .and(UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED)));
        if (activeUploads > 0) {
            throw new BusinessException(AppErrorCode.COURSE_ENABLE_INVALID,
                    "课程仍有未完成上传任务");
        }
    }

    private void validateCourseRules(
            String name,
            String description,
            int requiredDurationSeconds,
            int reportIntervalSeconds,
            int toleranceSeconds) {
        TrainingGuard.requireText(name, "课程名称", 128);
        TrainingGuard.optionalText(description, "课程简介", 1000);
        if (requiredDurationSeconds <= 0) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "规定时长必须大于0秒");
        }
        if (reportIntervalSeconds < 10 || reportIntervalSeconds > 30) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "进度上报间隔必须为10至30秒");
        }
        if (toleranceSeconds < 0 || toleranceSeconds > 300) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "学时误差必须为0至300秒");
        }
    }

    private void applyCourseValues(
            CourseEntity course,
            String name,
            String description,
            int requiredDurationSeconds,
            boolean allowSeek,
            int reportIntervalSeconds,
            int toleranceSeconds) {
        course.setCourseName(TrainingGuard.requireText(name, "课程名称", 128));
        course.setDescription(TrainingGuard.optionalText(description, "课程简介", 1000));
        course.setRequiredDurationSeconds(requiredDurationSeconds);
        course.setAllowSeek(allowSeek);
        course.setProgressReportIntervalSeconds(reportIntervalSeconds);
        course.setStudyToleranceSeconds(toleranceSeconds);
    }

    private CourseEntity requireCourse(Long id, Long enterpriseId) {
        CourseEntity course = id == null ? null : courseMapper.selectOneByQuery(QueryWrapper.create()
                .where(COURSE.ID.eq(id))
                .and(COURSE.ENTERPRISE_ID.eq(enterpriseId))
                .and(COURSE.DELETED_AT.isNull()));
        if (course == null) {
            throw new BusinessException(AppErrorCode.COURSE_NOT_FOUND);
        }
        TrainingGuard.checkEnterprise(course.getEnterpriseId(), enterpriseId);
        return course;
    }

    private CoursewareEntity requireCourseware(Long id, Long courseId, Long enterpriseId) {
        CoursewareEntity courseware = id == null ? null : coursewareMapper.selectOneByQuery(
                activeCoursewareQuery(id, courseId, enterpriseId));
        if (courseware == null) {
            throw new BusinessException(AppErrorCode.COURSEWARE_NOT_FOUND);
        }
        return courseware;
    }

    private void requireEditable(CourseEntity course) {
        if (COURSE_ENABLED.equals(course.getStatus())) {
            throw new BusinessException(AppErrorCode.COURSE_STATE_INVALID,
                    "启用中的课程只允许预览或禁用");
        }
    }

    private List<CoursewareEntity> listCoursewares(Long courseId, Long enterpriseId) {
        return coursewareMapper.selectListByQuery(QueryWrapper.create()
                .where(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                .and(COURSEWARE.COURSE_ID.eq(courseId))
                .and(COURSEWARE.DELETED_AT.isNull())
                .orderBy(COURSEWARE.SORT_ORDER.asc(), COURSEWARE.CREATED_AT.asc()));
    }

    private CourseView toView(CourseEntity course, boolean includeCoursewares) {
        StorageObjectEntity cover = course.getCoverObjectId() == null
                ? null : storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                        .where(STORAGE_OBJECT.ID.eq(course.getCoverObjectId()))
                        .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(course.getEnterpriseId())));
        List<CoursewareEntity> entities = listCoursewares(course.getId(), course.getEnterpriseId());
        List<CoursewareView> coursewares = includeCoursewares
                ? entities.stream().map(this::toCoursewareView).collect(Collectors.toList())
                : Collections.emptyList();
        long totalDuration = entities.stream().mapToLong(CoursewareEntity::getDurationSeconds).sum();
        return new CourseView(
                course.getId(), course.getCourseName(), course.getDescription(),
                cover == null ? null : cover.getId(),
                cover == null ? null : cover.getOriginalFilename(),
                cover == null ? null : cover.getFileSize(),
                cover == null ? null : cover.getContentType(),
                course.getRequiredDurationSeconds(), course.isAllowSeek(),
                course.getProgressReportIntervalSeconds(), course.getStudyToleranceSeconds(),
                course.getStatus(), course.isEverEnabled(), entities.size(), totalDuration,
                coursewares, course.getCreatedAt(), course.getUpdatedAt());
    }

    private CoursewareView toCoursewareView(CoursewareEntity courseware) {
        StorageObjectEntity object = storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                .where(STORAGE_OBJECT.ID.eq(courseware.getStorageObjectId()))
                .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(courseware.getEnterpriseId())));
        if (object == null) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    "课件存储元数据不存在");
        }
        return new CoursewareView(
                courseware.getId(), object.getId(), courseware.getCoursewareTitle(),
                object.getOriginalFilename(), object.getContentType(), object.getFileSize(),
                courseware.getDurationSeconds(), courseware.getSortOrder(), courseware.getCreatedAt());
    }

    private void markObject(
            Long objectId, Long enterpriseId, String status, Long operatorId) {
        if (objectId == null) {
            return;
        }
        UpdateWrapper<StorageObjectEntity> update = UpdateWrapper.of(StorageObjectEntity.class)
                .set(STORAGE_OBJECT.STATUS, status)
                .set(STORAGE_OBJECT.UPDATED_BY, operatorId);
        storageObjectMapper.updateByCondition(update.toEntity(), STORAGE_OBJECT.ID.eq(objectId)
                .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(enterpriseId)));
    }

    private void softDeleteCourseware(
            Long id, Long enterpriseId, Long operatorId, LocalDateTime deletedAt) {
        UpdateWrapper<CoursewareEntity> update = UpdateWrapper.of(CoursewareEntity.class)
                .set(COURSEWARE.DELETED_BY, operatorId)
                .set(COURSEWARE.DELETED_AT, deletedAt)
                .set(COURSEWARE.UPDATED_BY, operatorId);
        coursewareMapper.updateByCondition(update.toEntity(), COURSEWARE.ID.eq(id)
                .and(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                .and(COURSEWARE.DELETED_AT.isNull()));
    }

    private com.mybatisflex.core.query.QueryCondition activeCourseCondition(Long id, Long enterpriseId) {
        return COURSE.ID.eq(id).and(COURSE.ENTERPRISE_ID.eq(enterpriseId)).and(COURSE.DELETED_AT.isNull());
    }

    private com.mybatisflex.core.query.QueryCondition activeCoursewareCondition(
            Long id, Long courseId, Long enterpriseId) {
        return COURSEWARE.ID.eq(id)
                .and(COURSEWARE.COURSE_ID.eq(courseId))
                .and(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                .and(COURSEWARE.DELETED_AT.isNull());
    }

    private QueryWrapper activeCoursewareQuery(Long id, Long courseId, Long enterpriseId) {
        return QueryWrapper.create().where(activeCoursewareCondition(id, courseId, enterpriseId));
    }

    private String normalizeOptionalStatus(String value) {
        String status = trim(value);
        if (!hasText(status)) {
            return null;
        }
        if (!COURSE_DRAFT.equals(status) && !COURSE_ENABLED.equals(status)
                && !COURSE_DISABLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "课程状态不正确");
        }
        return status;
    }

    private String normalizeTargetStatus(String value) {
        String status = trim(value);
        if (!COURSE_ENABLED.equals(status) && !COURSE_DISABLED.equals(status)) {
            throw new BusinessException(AppErrorCode.PARAM_INVALID, "目标状态只能为ENABLED或DISABLED");
        }
        return status;
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 上传会话批量过期更新，单独收敛表字段操作。
     */
    private static final class UploadSessionEntityUpdate {

        private UploadSessionEntityUpdate() {
        }

        private static void expireCourseSessions(
                UploadSessionMapper mapper,
                Long enterpriseId,
                Long courseId,
                Long operatorId,
                LocalDateTime now) {
            UpdateWrapper<me.lj.train.training.model.entity.UploadSessionEntity> update =
                    UpdateWrapper.of(me.lj.train.training.model.entity.UploadSessionEntity.class)
                            .set(UPLOAD_SESSION.EXPIRES_AT, now)
                            .set(UPLOAD_SESSION.UPDATED_BY, operatorId);
            mapper.updateByCondition(update.toEntity(),
                    UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId)
                            .and(UPLOAD_SESSION.COURSE_ID.eq(courseId))
                            .and(UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED)));
        }
    }
}

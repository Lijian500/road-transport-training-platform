package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.training.CourseStorageService;
import me.lj.train.api.training.StorageModels.CoursewarePreviewCommand;
import me.lj.train.api.training.StorageModels.CreateCoursewareUploadSessionCommand;
import me.lj.train.api.training.StorageModels.CreateCoverUploadSessionCommand;
import me.lj.train.api.training.StorageModels.CreatePartUrlsCommand;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.api.training.StorageModels.StorageCapabilityView;
import me.lj.train.api.training.StorageModels.UploadCompleteView;
import me.lj.train.api.training.StorageModels.UploadSessionView;
import me.lj.train.api.training.StorageModels.UploadedPartView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.CourseMapper;
import me.lj.train.training.mapper.CoursewareMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.model.entity.UploadSessionEntity;
import me.lj.train.training.storage.ObjectStorageService;
import me.lj.train.training.storage.ObjectStorageService.ObjectMetadata;
import me.lj.train.training.storage.ObjectStorageService.SignedRequest;
import me.lj.train.training.storage.ObjectStorageService.StoredPart;
import me.lj.train.training.storage.UploadFileValidator;
import me.lj.train.training.storage.UploadFileValidator.FileDeclaration;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import static me.lj.train.training.constant.TrainingConstants.COURSE_ENABLED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_ACTIVE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_RETAINED;
import static me.lj.train.training.constant.TrainingConstants.PROVIDER_ALIYUN_OSS;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COVER;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_VIDEO;
import static me.lj.train.training.constant.TrainingPermissions.COURSE_VIEW;
import static me.lj.train.training.constant.TrainingPermissions.COURSEWARE_MANAGE;
import static me.lj.train.training.model.table.CourseTableDef.COURSE;
import static me.lj.train.training.model.table.CoursewareTableDef.COURSEWARE;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;
import static me.lj.train.training.model.table.UploadSessionTableDef.UPLOAD_SESSION;

/**
 * 课程文件直传、断点续传及预览RPC实现。
 */
@DubboService(timeout = 15000, retries = 0)
public class CourseStorageServiceImpl extends TrainingServiceSupport implements CourseStorageService {

    private static final int FILE_HEADER_LENGTH = 32;

    private final CourseMapper courseMapper;
    private final CoursewareMapper coursewareMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final UploadSessionMapper uploadSessionMapper;
    private final ObjectStorageService objectStorageService;
    private final OssStorageProperties properties;

    public CourseStorageServiceImpl(
            PlatformTransactionManager transactionManager,
            CourseMapper courseMapper,
            CoursewareMapper coursewareMapper,
            StorageObjectMapper storageObjectMapper,
            UploadSessionMapper uploadSessionMapper,
            ObjectStorageService objectStorageService,
            OssStorageProperties properties) {
        super(transactionManager);
        this.courseMapper = courseMapper;
        this.coursewareMapper = coursewareMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.uploadSessionMapper = uploadSessionMapper;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
    }

    @Override
    public Result<StorageCapabilityView> capability() {
        return execute(() -> {
            TrainingGuard.requireEnterprisePermission(COURSE_VIEW);
            return new StorageCapabilityView(
                    objectStorageService.isEnabled(),
                    objectStorageService.isEnabled() ? "阿里云OSS上传可用" : objectStorageService.disabledMessage(),
                    PROVIDER_ALIYUN_OSS,
                    properties.getPartSizeBytes(), properties.getMaxVideoBytes(),
                    properties.getMaxCoverBytes(), properties.getUploadUrlTtlSeconds(),
                    properties.getPreviewUrlTtlSeconds(),
                    List.of("video/mp4"),
                    List.of("image/jpeg", "image/png", "image/webp"));
        });
    }

    @Override
    public Result<UploadSessionView> createCoverUploadSession(CreateCoverUploadSessionCommand command) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            FileDeclaration file = UploadFileValidator.validateCover(
                    command.originalFilename(), command.contentType(), command.fileSizeBytes(), properties);
            CourseEntity course = requireEditableCourse(command.courseId(), enterpriseId);
            UploadSessionEntity session = newSession(
                    enterpriseId, course.getId(), file, command.fileSizeBytes(),
                    command.clientLastModified(), null, null);
            SignedRequest signedRequest = objectStorageService.presignPut(
                    session.getObjectKey(), file.contentType(), uploadTtl());
            runInTransaction(() -> {
                requireEditableCourse(course.getId(), enterpriseId);
                uploadSessionMapper.insertSelective(session);
            });
            return toSessionView(session, toSignedRequest(null, signedRequest));
        });
    }

    @Override
    public Result<UploadSessionView> createCoursewareUploadSession(
            CreateCoursewareUploadSessionCommand command) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            FileDeclaration file = UploadFileValidator.validateVideo(
                    command.originalFilename(), command.contentType(), command.fileSizeBytes(),
                    command.durationSeconds(), properties);
            String title = TrainingGuard.requireText(command.title(), "课件标题", 128);
            CourseEntity course = requireEditableCourse(command.courseId(), enterpriseId);
            UploadSessionEntity session = newSession(
                    enterpriseId, course.getId(), file, command.fileSizeBytes(),
                    command.clientLastModified(), command.durationSeconds(), title);
            String uploadId = objectStorageService.initiateMultipartUpload(
                    session.getObjectKey(), file.contentType());
            session.setOssUploadId(uploadId);
            try {
                runInTransaction(() -> {
                    requireEditableCourse(course.getId(), enterpriseId);
                    uploadSessionMapper.insertSelective(session);
                });
            } catch (RuntimeException exception) {
                abortQuietly(session);
                throw exception;
            }
            return toSessionView(session, null);
        });
    }

    @Override
    public Result<List<SignedRequestView>> createPartUrls(CreatePartUrlsCommand command) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            UploadSessionEntity session = requireActiveSession(command.sessionId(), enterpriseId);
            if (!UPLOAD_VIDEO.equals(session.getUploadType())) {
                throw new BusinessException(AppErrorCode.UPLOAD_SESSION_INVALID,
                        "封面上传不使用分片签名");
            }
            List<Integer> partNumbers = command.partNumbers() == null
                    ? Collections.emptyList()
                    : command.partNumbers().stream().filter(number -> number != null).distinct()
                            .sorted().collect(Collectors.toList());
            if (partNumbers.isEmpty() || partNumbers.size() > 100) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID,
                        "每次必须申请1至100个分片签名");
            }
            if (partNumbers.stream().anyMatch(number -> number < 1 || number > session.getPartCount())) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "分片编号超出上传会话范围");
            }
            return partNumbers.stream()
                    .map(number -> toSignedRequest(number, objectStorageService.presignUploadPart(
                            session.getObjectKey(), session.getOssUploadId(), number, uploadTtl())))
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Result<List<UploadedPartView>> listParts(Long sessionId) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            UploadSessionEntity session = requireActiveSession(sessionId, enterpriseId);
            if (!UPLOAD_VIDEO.equals(session.getUploadType())) {
                return Collections.emptyList();
            }
            ObjectMetadata mergedObject = objectStorageService.headObject(session.getObjectKey());
            if (mergedObject != null) {
                return mergedObjectParts(session, mergedObject);
            }
            return objectStorageService.listParts(session.getObjectKey(), session.getOssUploadId())
                    .stream()
                    .map(this::toUploadedPartView)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Result<UploadCompleteView> complete(Long sessionId) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            UploadSessionEntity session = requireSession(sessionId, enterpriseId);
            if (UPLOAD_COMPLETED.equals(session.getStatus())) {
                return toCompleteView(session);
            }
            requireActive(session);
            requireEditableCourse(session.getCourseId(), enterpriseId);

            ObjectMetadata metadata = objectStorageService.headObject(session.getObjectKey());
            if (metadata == null && UPLOAD_VIDEO.equals(session.getUploadType())) {
                List<StoredPart> parts = objectStorageService.listParts(
                        session.getObjectKey(), session.getOssUploadId());
                validateParts(session, parts);
                objectStorageService.completeMultipartUpload(
                        session.getObjectKey(), session.getOssUploadId(), parts);
                metadata = objectStorageService.headObject(session.getObjectKey());
            }
            if (metadata == null) {
                throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                        "尚未检测到已上传的OSS对象");
            }

            try {
                byte[] prefix = objectStorageService.readObjectPrefix(
                        session.getObjectKey(), FILE_HEADER_LENGTH);
                UploadFileValidator.validateObject(
                        session.getUploadType(), session.getExpectedContentType(),
                        session.getExpectedFileSize(), metadata, prefix);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == AppErrorCode.STORAGE_OBJECT_INVALID) {
                    rejectInvalidUpload(session, metadata);
                }
                throw exception;
            }

            ObjectMetadata verifiedMetadata = metadata;
            return runInTransaction(() -> bindCompletedUpload(session.getId(), enterpriseId, verifiedMetadata));
        });
    }

    @Override
    public Result<?> cancel(Long sessionId) {
        return executeVoid(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSEWARE_MANAGE);
            requireStorageEnabled();
            UploadSessionEntity session = requireSession(sessionId, enterpriseId);
            if (UPLOAD_CANCELLED.equals(session.getStatus())) {
                return;
            }
            if (UPLOAD_COMPLETED.equals(session.getStatus())) {
                throw new BusinessException(AppErrorCode.UPLOAD_SESSION_INVALID,
                        "已完成的上传不能取消");
            }
            if (UPLOAD_VIDEO.equals(session.getUploadType())) {
                objectStorageService.abortMultipartUpload(
                        session.getObjectKey(), session.getOssUploadId());
            }
            if (objectStorageService.headObject(session.getObjectKey()) != null) {
                objectStorageService.deleteObject(session.getObjectKey());
            }
            runInTransaction(() -> updateSessionStatus(
                    session.getId(), enterpriseId, UPLOAD_CANCELLED, null));
        });
    }

    @Override
    public Result<SignedRequestView> coverPreviewUrl(Long courseId) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_VIEW);
            requireStorageEnabled();
            CourseEntity course = requireCourse(courseId, enterpriseId);
            if (course.getCoverObjectId() == null) {
                throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "课程尚未设置封面");
            }
            StorageObjectEntity object = requireObject(course.getCoverObjectId(), enterpriseId);
            return toSignedRequest(null, objectStorageService.presignGet(
                    object.getObjectKey(), previewTtl()));
        });
    }

    @Override
    public Result<SignedRequestView> coursewarePreviewUrl(CoursewarePreviewCommand command) {
        return execute(() -> {
            Long enterpriseId = TrainingGuard.requireEnterprisePermission(COURSE_VIEW);
            requireStorageEnabled();
            requireCourse(command.courseId(), enterpriseId);
            CoursewareEntity courseware = coursewareMapper.selectOneByQuery(QueryWrapper.create()
                    .where(COURSEWARE.ID.eq(command.coursewareId()))
                    .and(COURSEWARE.COURSE_ID.eq(command.courseId()))
                    .and(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                    .and(COURSEWARE.DELETED_AT.isNull()));
            if (courseware == null) {
                throw new BusinessException(AppErrorCode.COURSEWARE_NOT_FOUND);
            }
            StorageObjectEntity object = requireObject(courseware.getStorageObjectId(), enterpriseId);
            return toSignedRequest(null, objectStorageService.presignGet(
                    object.getObjectKey(), previewTtl()));
        });
    }

    private UploadSessionEntity newSession(
            Long enterpriseId,
            Long courseId,
            FileDeclaration file,
            long fileSizeBytes,
            Long clientLastModified,
            Integer durationSeconds,
            String title) {
        UploadSessionEntity session = new UploadSessionEntity();
        session.setId(IdGenerator.nextId());
        session.setEnterpriseId(enterpriseId);
        session.setCourseId(courseId);
        session.setStorageObjectId(IdGenerator.nextId());
        if (UPLOAD_VIDEO.equals(file.uploadType())) {
            session.setCoursewareId(IdGenerator.nextId());
        }
        session.setUploadType(file.uploadType());
        session.setBucketName(objectStorageService.bucketName());
        session.setObjectKey(buildObjectKey(
                enterpriseId, courseId, file.uploadType(), file.extension()));
        session.setOriginalFilename(file.filename());
        session.setExpectedContentType(file.contentType());
        session.setExpectedFileSize(fileSizeBytes);
        session.setClientLastModified(clientLastModified);
        session.setVideoDurationSeconds(durationSeconds);
        session.setCoursewareTitle(title);
        session.setPartSizeBytes(properties.getPartSizeBytes());
        session.setPartCount(UPLOAD_VIDEO.equals(file.uploadType())
                ? partCount(fileSizeBytes) : 1);
        session.setStatus(UPLOAD_INITIATED);
        session.setExpiresAt(LocalDateTime.now().plusHours(properties.getUploadSessionHours()));
        Long operatorId = UserContext.require().getUserId();
        session.setCreatedBy(operatorId);
        session.setUpdatedBy(operatorId);
        return session;
    }

    private UploadCompleteView bindCompletedUpload(
            Long sessionId, Long enterpriseId, ObjectMetadata metadata) {
        UploadSessionEntity session = requireSession(sessionId, enterpriseId);
        if (UPLOAD_COMPLETED.equals(session.getStatus())) {
            return toCompleteView(session);
        }
        requireActive(session);
        CourseEntity course = requireEditableCourse(session.getCourseId(), enterpriseId);
        Long operatorId = UserContext.require().getUserId();

        StorageObjectEntity object = storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                .where(STORAGE_OBJECT.ID.eq(session.getStorageObjectId()))
                .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(enterpriseId)));
        if (object == null) {
            object = new StorageObjectEntity();
            object.setId(session.getStorageObjectId());
            object.setEnterpriseId(enterpriseId);
            object.setProvider(PROVIDER_ALIYUN_OSS);
            object.setBucketName(session.getBucketName());
            object.setObjectKey(session.getObjectKey());
            object.setOriginalFilename(session.getOriginalFilename());
            object.setObjectType(session.getUploadType());
            object.setContentType(session.getExpectedContentType());
            object.setFileSize(metadata.sizeBytes());
            object.setEtag(metadata.etag());
            object.setStatus(OBJECT_ACTIVE);
            object.setCreatedBy(operatorId);
            object.setUpdatedBy(operatorId);
            storageObjectMapper.insertSelective(object);
        } else {
            TrainingGuard.checkEnterprise(object.getEnterpriseId(), enterpriseId);
        }

        if (UPLOAD_VIDEO.equals(session.getUploadType())) {
            bindCourseware(session, enterpriseId, operatorId);
        } else {
            bindCover(course, object.getId(), operatorId);
        }
        updateSessionStatus(session.getId(), enterpriseId, UPLOAD_COMPLETED, LocalDateTime.now());
        session.setStatus(UPLOAD_COMPLETED);
        return toCompleteView(session);
    }

    private void bindCourseware(UploadSessionEntity session, Long enterpriseId, Long operatorId) {
        CoursewareEntity existing = coursewareMapper.selectOneByQuery(QueryWrapper.create()
                .where(COURSEWARE.ID.eq(session.getCoursewareId()))
                .and(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId)));
        if (existing != null) {
            TrainingGuard.checkEnterprise(existing.getEnterpriseId(), enterpriseId);
            return;
        }
        int nextOrder = coursewareMapper.selectListByQuery(QueryWrapper.create()
                        .where(COURSEWARE.ENTERPRISE_ID.eq(enterpriseId))
                        .and(COURSEWARE.COURSE_ID.eq(session.getCourseId()))
                        .and(COURSEWARE.DELETED_AT.isNull()))
                .stream()
                .map(CoursewareEntity::getSortOrder)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        CoursewareEntity courseware = new CoursewareEntity();
        courseware.setId(session.getCoursewareId());
        courseware.setEnterpriseId(enterpriseId);
        courseware.setCourseId(session.getCourseId());
        courseware.setStorageObjectId(session.getStorageObjectId());
        courseware.setCoursewareTitle(session.getCoursewareTitle());
        courseware.setDurationSeconds(session.getVideoDurationSeconds());
        courseware.setSortOrder(nextOrder);
        courseware.setCreatedBy(operatorId);
        courseware.setUpdatedBy(operatorId);
        coursewareMapper.insertSelective(courseware);
    }

    private void bindCover(CourseEntity course, Long newObjectId, Long operatorId) {
        if (course.getCoverObjectId() != null && !course.getCoverObjectId().equals(newObjectId)) {
            UpdateWrapper<StorageObjectEntity> oldObject = UpdateWrapper.of(StorageObjectEntity.class)
                    .set(STORAGE_OBJECT.STATUS,
                            course.isEverEnabled() ? OBJECT_RETAINED : OBJECT_PENDING_DELETE)
                    .set(STORAGE_OBJECT.UPDATED_BY, operatorId);
            storageObjectMapper.updateByCondition(oldObject.toEntity(),
                    STORAGE_OBJECT.ID.eq(course.getCoverObjectId())
                            .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(course.getEnterpriseId())));
        }
        UpdateWrapper<CourseEntity> update = UpdateWrapper.of(CourseEntity.class)
                .set(COURSE.COVER_OBJECT_ID, newObjectId)
                .set(COURSE.UPDATED_BY, operatorId);
        courseMapper.updateByCondition(update.toEntity(),
                COURSE.ID.eq(course.getId())
                        .and(COURSE.ENTERPRISE_ID.eq(course.getEnterpriseId()))
                        .and(COURSE.DELETED_AT.isNull()));
    }

    private void validateParts(UploadSessionEntity session, List<StoredPart> parts) {
        if (parts == null || parts.size() != session.getPartCount()) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    "已上传分片数量不完整");
        }
        List<StoredPart> ordered = parts.stream()
                .sorted(Comparator.comparingInt(StoredPart::partNumber))
                .collect(Collectors.toList());
        for (int index = 0; index < ordered.size(); index++) {
            StoredPart part = ordered.get(index);
            int expectedPartNumber = index + 1;
            long expectedSize = expectedPartNumber == session.getPartCount()
                    ? session.getExpectedFileSize()
                            - session.getPartSizeBytes() * (session.getPartCount() - 1L)
                    : session.getPartSizeBytes();
            if (part.partNumber() != expectedPartNumber || part.sizeBytes() != expectedSize
                    || part.etag() == null || part.etag().isBlank()) {
                throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                        "分片编号、大小或ETag校验失败");
            }
        }
    }

    private void rejectInvalidUpload(UploadSessionEntity session, ObjectMetadata metadata) {
        boolean deleteFailed = false;
        try {
            objectStorageService.deleteObject(session.getObjectKey());
        } catch (RuntimeException ignored) {
            deleteFailed = true;
        }
        try {
            boolean shouldQueueDelete = deleteFailed;
            runInTransaction(() -> {
                if (shouldQueueDelete) {
                    queueInvalidObjectForDeletion(session, metadata);
                }
                updateSessionStatus(
                        session.getId(), session.getEnterpriseId(), UPLOAD_CANCELLED, null);
            });
        } catch (RuntimeException ignored) {
            // 状态更新失败时保留会话，后续重试仍会再次执行校验。
        }
    }

    private void queueInvalidObjectForDeletion(
            UploadSessionEntity session, ObjectMetadata metadata) {
        if (storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                .where(STORAGE_OBJECT.ID.eq(session.getStorageObjectId()))
                .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(session.getEnterpriseId()))) != null) {
            return;
        }
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(session.getStorageObjectId());
        object.setEnterpriseId(session.getEnterpriseId());
        object.setProvider(PROVIDER_ALIYUN_OSS);
        object.setBucketName(session.getBucketName());
        object.setObjectKey(session.getObjectKey());
        object.setOriginalFilename(session.getOriginalFilename());
        object.setObjectType(session.getUploadType());
        object.setContentType(session.getExpectedContentType());
        object.setFileSize(metadata.sizeBytes());
        object.setEtag(metadata.etag());
        object.setStatus(OBJECT_PENDING_DELETE);
        object.setCreatedBy(session.getCreatedBy());
        object.setUpdatedBy(session.getUpdatedBy());
        storageObjectMapper.insertSelective(object);
    }

    private void updateSessionStatus(
            Long sessionId, Long enterpriseId, String status, LocalDateTime completedAt) {
        UpdateWrapper<UploadSessionEntity> update = UpdateWrapper.of(UploadSessionEntity.class)
                .set(UPLOAD_SESSION.STATUS, status)
                .set(UPLOAD_SESSION.COMPLETED_AT, completedAt)
                .set(UPLOAD_SESSION.UPDATED_BY, UserContext.require().getUserId());
        uploadSessionMapper.updateByCondition(update.toEntity(),
                UPLOAD_SESSION.ID.eq(sessionId)
                        .and(UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId)));
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

    private CourseEntity requireEditableCourse(Long id, Long enterpriseId) {
        CourseEntity course = requireCourse(id, enterpriseId);
        if (COURSE_ENABLED.equals(course.getStatus())) {
            throw new BusinessException(AppErrorCode.COURSE_STATE_INVALID,
                    "启用中的课程不能修改封面或课件");
        }
        return course;
    }

    private UploadSessionEntity requireSession(Long id, Long enterpriseId) {
        UploadSessionEntity session = id == null ? null : uploadSessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(UPLOAD_SESSION.ID.eq(id))
                        .and(UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId)));
        if (session == null) {
            throw new BusinessException(AppErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        TrainingGuard.checkEnterprise(session.getEnterpriseId(), enterpriseId);
        return session;
    }

    private UploadSessionEntity requireActiveSession(Long id, Long enterpriseId) {
        UploadSessionEntity session = requireSession(id, enterpriseId);
        requireActive(session);
        return session;
    }

    private void requireActive(UploadSessionEntity session) {
        if (!UPLOAD_INITIATED.equals(session.getStatus())
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AppErrorCode.UPLOAD_SESSION_INVALID,
                    "上传会话已结束或已过期");
        }
    }

    private StorageObjectEntity requireObject(Long id, Long enterpriseId) {
        StorageObjectEntity object = id == null ? null : storageObjectMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(STORAGE_OBJECT.ID.eq(id))
                        .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(enterpriseId))
                        .and(STORAGE_OBJECT.STATUS.eq(OBJECT_ACTIVE)));
        if (object == null) {
            throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "存储对象不存在");
        }
        return object;
    }

    private String buildObjectKey(
            Long enterpriseId, Long courseId, String uploadType, String extension) {
        String prefix = properties.getObjectPrefix() == null
                ? "road-training" : properties.getObjectPrefix().trim();
        prefix = prefix.replaceAll("^/+|/+$", "");
        String folder = UPLOAD_VIDEO.equals(uploadType) ? "videos" : "covers";
        return String.format(Locale.ROOT, "%s/enterprises/%d/courses/%d/%s/%s.%s",
                prefix, enterpriseId, courseId, folder,
                UUID.randomUUID().toString().replace("-", ""), extension);
    }

    private int partCount(long fileSizeBytes) {
        long count = (fileSizeBytes + properties.getPartSizeBytes() - 1L)
                / properties.getPartSizeBytes();
        if (count <= 0 || count > 10_000) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID,
                    "文件分片数量超出OSS限制");
        }
        return (int) count;
    }

    private UploadSessionView toSessionView(
            UploadSessionEntity session, SignedRequestView uploadRequest) {
        return new UploadSessionView(
                session.getId(), session.getCourseId(), session.getCoursewareId(),
                session.getUploadType(), session.getOriginalFilename(),
                session.getExpectedFileSize(), session.getClientLastModified(),
                session.getPartSizeBytes(), session.getPartCount(), session.getStatus(),
                session.getExpiresAt(), uploadRequest);
    }

    private SignedRequestView toSignedRequest(Integer partNumber, SignedRequest request) {
        return new SignedRequestView(
                partNumber, request.url(), request.method(), request.headers(),
                LocalDateTime.ofInstant(request.expiresAt(), ZoneId.systemDefault()));
    }

    private UploadedPartView toUploadedPartView(StoredPart part) {
        Instant instant = part.lastModified();
        return new UploadedPartView(
                part.partNumber(), part.sizeBytes(), part.etag(),
                instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    /**
     * OSS已完成合并但数据库尚未绑定时，按会话切片还原完整进度，前端随后重试完成接口。
     */
    private List<UploadedPartView> mergedObjectParts(
            UploadSessionEntity session, ObjectMetadata metadata) {
        if (metadata.sizeBytes() != session.getExpectedFileSize()) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    "已合并OSS对象大小与上传会话不一致");
        }
        List<UploadedPartView> parts = new ArrayList<>(session.getPartCount());
        for (int partNumber = 1; partNumber <= session.getPartCount(); partNumber++) {
            long size = partNumber == session.getPartCount()
                    ? session.getExpectedFileSize()
                            - session.getPartSizeBytes() * (session.getPartCount() - 1L)
                    : session.getPartSizeBytes();
            parts.add(new UploadedPartView(partNumber, size, metadata.etag(), null));
        }
        return parts;
    }

    private UploadCompleteView toCompleteView(UploadSessionEntity session) {
        Long resourceId = UPLOAD_VIDEO.equals(session.getUploadType())
                ? session.getCoursewareId() : session.getCourseId();
        return new UploadCompleteView(
                session.getId(), session.getCourseId(), resourceId,
                session.getStorageObjectId(), session.getUploadType(), UPLOAD_COMPLETED);
    }

    private void abortQuietly(UploadSessionEntity session) {
        try {
            objectStorageService.abortMultipartUpload(
                    session.getObjectKey(), session.getOssUploadId());
        } catch (RuntimeException ignored) {
            // 数据库写入失败时尽力取消，失败的OSS分片由Bucket生命周期规则兜底。
        }
    }

    private void requireStorageEnabled() {
        if (!objectStorageService.isEnabled()) {
            throw new BusinessException(AppErrorCode.UPLOAD_DISABLED,
                    objectStorageService.disabledMessage());
        }
    }

    private Duration uploadTtl() {
        return Duration.ofSeconds(properties.getUploadUrlTtlSeconds());
    }

    private Duration previewTtl() {
        return Duration.ofSeconds(properties.getPreviewUrlTtlSeconds());
    }
}

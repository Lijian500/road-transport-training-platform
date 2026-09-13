package me.lj.train.training.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.api.admin.FaceReferenceModels.FaceReferenceView;
import me.lj.train.api.training.PrivateImageModels.CreateFaceReferenceUploadSessionCommand;
import me.lj.train.api.training.PrivateImageModels.PrivateImageContentView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadCompleteView;
import me.lj.train.api.training.PrivateImageModels.PrivateImageUploadSessionView;
import me.lj.train.api.training.PrivateImageStorageService;
import me.lj.train.api.training.StorageModels.SignedRequestView;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.mapper.PrivateImageUploadSessionMapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.model.entity.PrivateImageUploadSessionEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.storage.ObjectStorageService;
import me.lj.train.training.storage.ObjectStorageService.ObjectMetadata;
import me.lj.train.training.storage.ObjectStorageService.SignedRequest;
import me.lj.train.training.storage.UploadFileValidator;
import me.lj.train.training.storage.UploadFileValidator.FileDeclaration;
import me.lj.train.training.support.FaceReferenceDirectoryClient;
import me.lj.train.training.support.TrainingGuard;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import static me.lj.train.training.constant.TrainingConstants.OBJECT_ACTIVE;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_DELETED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.PROVIDER_ALIYUN_OSS;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_CANCELLED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COMPLETED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_FACE_REFERENCE;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.constant.TrainingPermissions.FACE_CHECK_MANAGE;
import static me.lj.train.training.constant.TrainingPermissions.FACE_CHECK_VIEW;
import static me.lj.train.training.model.table.PrivateImageUploadSessionTableDef.PRIVATE_IMAGE_UPLOAD_SESSION;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;

/**
 * 人脸登记照直传、私有读取及延迟删除实现。
 */
@DubboService(timeout = 15000, retries = 0)
public class PrivateImageStorageServiceImpl extends TrainingServiceSupport
        implements PrivateImageStorageService {

    private static final int FILE_HEADER_LENGTH = 32;

    private final PrivateImageUploadSessionMapper uploadSessionMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final ObjectStorageService objectStorageService;
    private final OssStorageProperties properties;
    private final FaceReferenceDirectoryClient directoryClient;

    public PrivateImageStorageServiceImpl(
            PlatformTransactionManager transactionManager,
            PrivateImageUploadSessionMapper uploadSessionMapper,
            StorageObjectMapper storageObjectMapper,
            ObjectStorageService objectStorageService,
            OssStorageProperties properties,
            FaceReferenceDirectoryClient directoryClient) {
        super(transactionManager);
        this.uploadSessionMapper = uploadSessionMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
        this.directoryClient = directoryClient;
    }

    @Override
    public Result<PrivateImageUploadSessionView> createFaceReferenceUploadSession(
            CreateFaceReferenceUploadSessionCommand command) {
        return execute(() -> {
            Long enterpriseId = requireImageWriter(command == null ? null : command.userId());
            requireStorageEnabled();
            if (command == null || command.userId() == null) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "登记照所属用户不能为空");
            }
            FaceReferenceView user = directoryClient.get(command.userId());
            if (user == null || !command.userId().equals(user.userId())) {
                throw new BusinessException(AppErrorCode.USER_NOT_FOUND);
            }
            FileDeclaration file = UploadFileValidator.validateFaceReference(
                    command.originalFilename(), command.contentType(),
                    command.fileSizeBytes(), properties);
            PrivateImageUploadSessionEntity session = newSession(
                    enterpriseId, command.userId(), file, command.fileSizeBytes(),
                    command.clientLastModified());
            SignedRequest request = objectStorageService.presignPut(
                    session.getObjectKey(), file.contentType(), uploadTtl());
            runInTransaction(() -> uploadSessionMapper.insertSelective(session));
            return toSessionView(session, toSignedRequest(request));
        });
    }

    @Override
    public Result<PrivateImageUploadCompleteView> completeFaceReferenceUpload(Long sessionId) {
        return execute(() -> {
            Long enterpriseId = requireImageUser().getEnterpriseId();
            requireStorageEnabled();
            PrivateImageUploadSessionEntity session = requireSession(sessionId, enterpriseId);
            requireImageWriter(session.getOwnerUserId());
            if (UPLOAD_COMPLETED.equals(session.getStatus())) {
                requireObject(session.getStorageObjectId(), enterpriseId, false);
                return toCompleteView(session);
            }
            requireActive(session);
            ObjectMetadata metadata = objectStorageService.headObject(session.getObjectKey());
            byte[] prefix = metadata == null ? null
                    : objectStorageService.readObjectPrefix(session.getObjectKey(), FILE_HEADER_LENGTH);
            try {
                UploadFileValidator.validateObject(
                        session.getUploadType(), session.getExpectedContentType(),
                        session.getExpectedFileSize(), metadata, prefix);
            } catch (RuntimeException exception) {
                rejectInvalidUpload(session);
                throw exception;
            }
            Long operatorId = UserContext.require().getUserId();
            runInTransaction(() -> {
                PrivateImageUploadSessionEntity locked = requireSession(sessionId, enterpriseId, true);
                if (UPLOAD_COMPLETED.equals(locked.getStatus())) {
                    return;
                }
                requireActive(locked);
                StorageObjectEntity object = new StorageObjectEntity();
                object.setId(locked.getStorageObjectId());
                object.setEnterpriseId(enterpriseId);
                object.setOwnerUserId(locked.getOwnerUserId());
                object.setProvider(PROVIDER_ALIYUN_OSS);
                object.setBucketName(locked.getBucketName());
                object.setObjectKey(locked.getObjectKey());
                object.setOriginalFilename(locked.getOriginalFilename());
                object.setObjectType(UPLOAD_FACE_REFERENCE);
                object.setContentType(locked.getExpectedContentType());
                object.setFileSize(metadata.sizeBytes());
                object.setEtag(metadata.etag());
                object.setStatus(OBJECT_ACTIVE);
                object.setCreatedBy(operatorId);
                object.setUpdatedBy(operatorId);
                storageObjectMapper.insertSelective(object);
                updateSessionStatus(locked.getId(), enterpriseId, UPLOAD_COMPLETED, LocalDateTime.now());
            });
            session.setStatus(UPLOAD_COMPLETED);
            return toCompleteView(session);
        });
    }

    @Override
    public Result<PrivateImageContentView> read(Long storageObjectId) {
        return execute(() -> {
            LoginUser operator = requireImageUser();
            requireStorageEnabled();
            StorageObjectEntity object = requireObject(storageObjectId, operator.getEnterpriseId(), true);
            requireOwnerOrPermission(object, operator, FACE_CHECK_VIEW, FACE_CHECK_MANAGE);
            byte[] content = objectStorageService.readObject(object.getObjectKey());
            if (content.length != object.getFileSize()
                    || content.length > properties.getMaxFaceReferenceBytes()) {
                throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                        "登记照对象大小校验失败");
            }
            return new PrivateImageContentView(
                    object.getId(), object.getContentType(), content, sha256(content));
        });
    }

    @Override
    public Result<SignedRequestView> previewUrl(Long storageObjectId) {
        return execute(() -> {
            LoginUser operator = requireImageUser();
            requireStorageEnabled();
            StorageObjectEntity object = requireObject(storageObjectId, operator.getEnterpriseId(), true);
            requireOwnerOrPermission(object, operator, FACE_CHECK_VIEW, FACE_CHECK_MANAGE);
            return toSignedRequest(objectStorageService.presignGet(
                    object.getObjectKey(), Duration.ofSeconds(properties.getPreviewUrlTtlSeconds())));
        });
    }

    /** 服务端留存学习照片，所有者始终取自登录身份。 */
    @Override
    public Result<Long> saveLearningPhoto(byte[] content) {
        return execute(() -> {
            LoginUser user = requireLearningPhotoUser();
            if (!user.hasPermission("student:learning:study")) throw new BusinessException(AppErrorCode.FORBIDDEN);
            requireStorageEnabled();
            if (content == null || content.length == 0 || content.length > properties.getMaxFaceReferenceBytes()) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "学习照片大小不正确");
            }
            String contentType = (content[0] & 0xff) == 0xff ? "image/jpeg"
                    : (content[0] & 0xff) == 0x89 ? "image/png" : "image/webp";
            UploadFileValidator.validateObject("LEARNING_PHOTO", contentType, content.length,
                    new ObjectMetadata(content.length, contentType, null), content);
            Long id = IdGenerator.nextId();
            String key = buildObjectKey(user.getEnterpriseId(), user.getUserId(), "image")
                    .replace("/face-references/", "/learning-photos/");
            objectStorageService.putObject(key, contentType, content);
            StorageObjectEntity object = new StorageObjectEntity();
            object.setId(id);
            object.setEnterpriseId(user.getEnterpriseId());
            object.setOwnerUserId(user.getUserId());
            object.setProvider(PROVIDER_ALIYUN_OSS);
            object.setBucketName(objectStorageService.bucketName());
            object.setObjectKey(key);
            object.setOriginalFilename("learning-photo.image");
            object.setObjectType("LEARNING_PHOTO");
            object.setContentType(contentType);
            object.setFileSize((long) content.length);
            object.setStatus(OBJECT_ACTIVE);
            object.setCreatedBy(user.getUserId());
            object.setUpdatedBy(user.getUserId());
            try {
                runInTransaction(() -> storageObjectMapper.insertSelective(object));
            } catch (RuntimeException exception) {
                objectStorageService.deleteObject(key);
                throw exception;
            }
            return id;
        });
    }

    /** 学员仅查看本人照片，统计管理员仅查看本企业照片。 */
    @Override
    public Result<SignedRequestView> learningPhotoPreview(Long storageObjectId) {
        return execute(() -> {
            LoginUser user = requireLearningPhotoUser();
            boolean administrator = user.hasPermission("admin:statistics:view");
            if (!administrator && !user.hasPermission("student:plan:view")) throw new BusinessException(AppErrorCode.FORBIDDEN);
            if (storageObjectId == null) throw new BusinessException(AppErrorCode.PARAM_INVALID);
            StorageObjectEntity object = storageObjectMapper.selectOneByQuery(QueryWrapper.create()
                    .where(STORAGE_OBJECT.ID.eq(storageObjectId))
                    .and(STORAGE_OBJECT.ENTERPRISE_ID.eq(user.getEnterpriseId()))
                    .and(STORAGE_OBJECT.OWNER_USER_ID.eq(user.getUserId()).when(!administrator))
                    .and(STORAGE_OBJECT.OBJECT_TYPE.eq("LEARNING_PHOTO"))
                    .and(STORAGE_OBJECT.STATUS.eq(OBJECT_ACTIVE)));
            if (object == null) throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "学习照片不存在");
            requireStorageEnabled();
            return toSignedRequest(objectStorageService.presignGet(object.getObjectKey(),
                    Duration.ofSeconds(properties.getPreviewUrlTtlSeconds())));
        });
    }

    /** 学习证据沿用档案的企业与密码状态限制。 */
    private LoginUser requireLearningPhotoUser() {
        LoginUser user = UserContext.require();
        if (user.isMustChangePassword()) throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        if (user.isPlatformAdmin() || user.getEnterpriseId() == null) throw new BusinessException(AppErrorCode.FORBIDDEN);
        return user;
    }

    @Override
    public Result<?> delete(Long storageObjectId) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = requireImageUser().getEnterpriseId();
            StorageObjectEntity object = requireObject(storageObjectId, enterpriseId, false);
            requireImageWriter(object.getOwnerUserId());
            if (OBJECT_PENDING_DELETE.equals(object.getStatus())
                    || OBJECT_DELETED.equals(object.getStatus())) {
                return;
            }
            UpdateWrapper<StorageObjectEntity> update = UpdateWrapper.of(StorageObjectEntity.class)
                    .set(STORAGE_OBJECT.STATUS, OBJECT_PENDING_DELETE)
                    .set(STORAGE_OBJECT.UPDATED_BY, UserContext.require().getUserId());
            storageObjectMapper.updateByCondition(update.toEntity(),
                    STORAGE_OBJECT.ID.eq(object.getId())
                            .and((enterpriseId == null ? STORAGE_OBJECT.ENTERPRISE_ID.isNull() : STORAGE_OBJECT.ENTERPRISE_ID.eq(enterpriseId)))
                            .and(STORAGE_OBJECT.OBJECT_TYPE.eq(UPLOAD_FACE_REFERENCE)));
        });
    }

    /** 登记照允许本人维护，跨用户操作保留管理权限校验。 */
    private Long requireImageWriter(Long userId) {
        LoginUser operator = requireImageUser();
        if (operator.getUserId().equals(userId)) return operator.getEnterpriseId();
        return TrainingGuard.requireEnterprisePermission(FACE_CHECK_MANAGE);
    }

    private PrivateImageUploadSessionEntity newSession(
            Long enterpriseId,
            Long userId,
            FileDeclaration file,
            long fileSizeBytes,
            Long clientLastModified) {
        Long operatorId = UserContext.require().getUserId();
        PrivateImageUploadSessionEntity session = new PrivateImageUploadSessionEntity();
        session.setId(IdGenerator.nextId());
        session.setEnterpriseId(enterpriseId);
        session.setOwnerUserId(userId);
        session.setStorageObjectId(IdGenerator.nextId());
        session.setUploadType(UPLOAD_FACE_REFERENCE);
        session.setBucketName(objectStorageService.bucketName());
        session.setObjectKey(buildObjectKey(enterpriseId, userId, file.extension()));
        session.setOriginalFilename(file.filename());
        session.setExpectedContentType(file.contentType());
        session.setExpectedFileSize(fileSizeBytes);
        session.setClientLastModified(clientLastModified);
        session.setStatus(UPLOAD_INITIATED);
        session.setExpiresAt(LocalDateTime.now().plusHours(properties.getUploadSessionHours()));
        session.setCreatedBy(operatorId);
        session.setUpdatedBy(operatorId);
        return session;
    }

    private PrivateImageUploadSessionEntity requireSession(Long id, Long enterpriseId) {
        return requireSession(id, enterpriseId, false);
    }

    private PrivateImageUploadSessionEntity requireSession(
            Long id, Long enterpriseId, boolean lock) {
        QueryWrapper query = QueryWrapper.create()
                .where(PRIVATE_IMAGE_UPLOAD_SESSION.ID.eq(id))
                .and((enterpriseId == null ? PRIVATE_IMAGE_UPLOAD_SESSION.ENTERPRISE_ID.isNull() : PRIVATE_IMAGE_UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId)));
        if (lock) {
            query.forUpdate();
        }
        PrivateImageUploadSessionEntity session = id == null ? null
                : uploadSessionMapper.selectOneByQuery(query);
        if (session == null) {
            throw new BusinessException(AppErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        if (!java.util.Objects.equals(session.getEnterpriseId(), enterpriseId)) {
            throw new BusinessException(AppErrorCode.DATA_SCOPE_VIOLATION);
        }
        return session;
    }

    private void requireActive(PrivateImageUploadSessionEntity session) {
        if (!UPLOAD_INITIATED.equals(session.getStatus())
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AppErrorCode.UPLOAD_SESSION_INVALID);
        }
    }

    private StorageObjectEntity requireObject(
            Long id, Long enterpriseId, boolean requireActive) {
        QueryWrapper query = QueryWrapper.create()
                .where(STORAGE_OBJECT.ID.eq(id))
                .and((enterpriseId == null ? STORAGE_OBJECT.ENTERPRISE_ID.isNull() : STORAGE_OBJECT.ENTERPRISE_ID.eq(enterpriseId)))
                .and(STORAGE_OBJECT.OBJECT_TYPE.eq(UPLOAD_FACE_REFERENCE));
        if (requireActive) {
            query.and(STORAGE_OBJECT.STATUS.eq(OBJECT_ACTIVE));
        }
        StorageObjectEntity object = id == null ? null : storageObjectMapper.selectOneByQuery(query);
        if (object == null) {
            throw new BusinessException(AppErrorCode.RESOURCE_NOT_FOUND, "人脸登记照不存在");
        }
        return object;
    }

    private void requireOwnerOrPermission(
            StorageObjectEntity object, LoginUser operator, String... permissions) {
        if (operator.getUserId().equals(object.getOwnerUserId())) {
            return;
        }
        if (operator.getEnterpriseId() == null) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        for (String permission : permissions) {
            if (operator.hasPermission(permission)) {
                return;
            }
        }
        throw new BusinessException(AppErrorCode.FORBIDDEN);
    }

    /** 企业人员或平台管理员均可维护本人私有图片。 */
    private LoginUser requireImageUser() {
        LoginUser operator = UserContext.require();
        if (operator.getEnterpriseId() == null && !operator.isPlatformAdmin()) {
            throw new BusinessException(AppErrorCode.FORBIDDEN);
        }
        return operator;
    }

    private void updateSessionStatus(
            Long sessionId, Long enterpriseId, String status, LocalDateTime completedAt) {
        UpdateWrapper<PrivateImageUploadSessionEntity> update =
                UpdateWrapper.of(PrivateImageUploadSessionEntity.class)
                        .set(PRIVATE_IMAGE_UPLOAD_SESSION.STATUS, status)
                        .set(PRIVATE_IMAGE_UPLOAD_SESSION.COMPLETED_AT, completedAt)
                        .set(PRIVATE_IMAGE_UPLOAD_SESSION.UPDATED_BY,
                                UserContext.require().getUserId());
        uploadSessionMapper.updateByCondition(update.toEntity(),
                PRIVATE_IMAGE_UPLOAD_SESSION.ID.eq(sessionId)
                        .and((enterpriseId == null ? PRIVATE_IMAGE_UPLOAD_SESSION.ENTERPRISE_ID.isNull() : PRIVATE_IMAGE_UPLOAD_SESSION.ENTERPRISE_ID.eq(enterpriseId))));
    }

    private void rejectInvalidUpload(PrivateImageUploadSessionEntity session) {
        try {
            objectStorageService.deleteObject(session.getObjectKey());
        } finally {
            runInTransaction(() -> updateSessionStatus(
                    session.getId(), session.getEnterpriseId(), UPLOAD_CANCELLED, null));
        }
    }

    private String buildObjectKey(Long enterpriseId, Long userId, String extension) {
        String prefix = properties.getObjectPrefix() == null
                ? "road-training" : properties.getObjectPrefix().trim();
        prefix = prefix.replaceAll("^/+|/+$", "");
        return String.format(Locale.ROOT, "%s/enterprises/%d/users/%d/face-references/%s.%s",
                prefix, enterpriseId, userId,
                UUID.randomUUID().toString().replace("-", ""), extension);
    }

    private PrivateImageUploadSessionView toSessionView(
            PrivateImageUploadSessionEntity session, SignedRequestView request) {
        return new PrivateImageUploadSessionView(
                session.getId(), session.getOwnerUserId(), session.getStorageObjectId(),
                session.getOriginalFilename(), session.getExpectedFileSize(), session.getStatus(),
                session.getExpiresAt(), request);
    }

    private PrivateImageUploadCompleteView toCompleteView(
            PrivateImageUploadSessionEntity session) {
        return new PrivateImageUploadCompleteView(
                session.getId(), session.getOwnerUserId(), session.getStorageObjectId(),
                UPLOAD_COMPLETED);
    }

    private SignedRequestView toSignedRequest(SignedRequest request) {
        return new SignedRequestView(
                null, request.url(), request.method(), request.headers(),
                LocalDateTime.ofInstant(request.expiresAt(), ZoneId.systemDefault()));
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM缺少SHA-256算法", exception);
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
}

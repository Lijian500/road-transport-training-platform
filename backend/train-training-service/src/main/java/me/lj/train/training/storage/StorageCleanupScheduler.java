package me.lj.train.training.storage;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.training.mapper.StorageObjectMapper;
import me.lj.train.training.mapper.PrivateImageUploadSessionMapper;
import me.lj.train.training.mapper.UploadSessionMapper;
import me.lj.train.training.model.entity.PrivateImageUploadSessionEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.model.entity.UploadSessionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static me.lj.train.training.constant.TrainingConstants.OBJECT_DELETED;
import static me.lj.train.training.constant.TrainingConstants.OBJECT_PENDING_DELETE;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COVER;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_EXPIRED;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_INITIATED;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;
import static me.lj.train.training.model.table.PrivateImageUploadSessionTableDef.PRIVATE_IMAGE_UPLOAD_SESSION;
import static me.lj.train.training.model.table.UploadSessionTableDef.UPLOAD_SESSION;

/**
 * 重试清理过期分片任务和待删除OSS对象。
 */
@Component
public class StorageCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageCleanupScheduler.class);
    private static final int CLEANUP_BATCH_SIZE = 50;

    private final UploadSessionMapper uploadSessionMapper;
    private final PrivateImageUploadSessionMapper privateImageUploadSessionMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final ObjectStorageService objectStorageService;
    private final TransactionTemplate transactionTemplate;

    public StorageCleanupScheduler(
            UploadSessionMapper uploadSessionMapper,
            PrivateImageUploadSessionMapper privateImageUploadSessionMapper,
            StorageObjectMapper storageObjectMapper,
            ObjectStorageService objectStorageService,
            PlatformTransactionManager transactionManager) {
        this.uploadSessionMapper = uploadSessionMapper;
        this.privateImageUploadSessionMapper = privateImageUploadSessionMapper;
        this.storageObjectMapper = storageObjectMapper;
        this.objectStorageService = objectStorageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 固定延迟清理，单个对象失败不影响同批其他任务，失败项留待下次重试。
     */
    @Scheduled(fixedDelayString = "${training.storage.cleanup-delay-ms:600000}")
    public void cleanup() {
        if (!objectStorageService.isEnabled()) {
            return;
        }
        cleanupExpiredSessions();
        cleanupExpiredPrivateImageSessions();
        cleanupPendingObjects();
    }

    private void cleanupExpiredPrivateImageSessions() {
        List<PrivateImageUploadSessionEntity> sessions =
                privateImageUploadSessionMapper.selectListByQuery(QueryWrapper.create()
                        .where(PRIVATE_IMAGE_UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED))
                        .and(PRIVATE_IMAGE_UPLOAD_SESSION.EXPIRES_AT.le(LocalDateTime.now()))
                        .orderBy(PRIVATE_IMAGE_UPLOAD_SESSION.EXPIRES_AT.asc())
                        .limit(CLEANUP_BATCH_SIZE));
        sessions.forEach(session -> {
            try {
                if (objectStorageService.headObject(session.getObjectKey()) != null) {
                    objectStorageService.deleteObject(session.getObjectKey());
                }
                transactionTemplate.executeWithoutResult(status -> markPrivateSessionExpired(session));
            } catch (RuntimeException exception) {
                LOGGER.warn("清理过期私有图片上传会话失败，sessionId={}", session.getId(), exception);
            }
        });
    }

    private void cleanupExpiredSessions() {
        List<UploadSessionEntity> sessions = uploadSessionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED))
                        .and(UPLOAD_SESSION.EXPIRES_AT.le(LocalDateTime.now()))
                        .orderBy(UPLOAD_SESSION.EXPIRES_AT.asc())
                        .limit(CLEANUP_BATCH_SIZE));
        sessions.forEach(session -> {
            try {
                if (UPLOAD_COVER.equals(session.getUploadType())) {
                    if (objectStorageService.headObject(session.getObjectKey()) != null) {
                        objectStorageService.deleteObject(session.getObjectKey());
                    }
                } else {
                    objectStorageService.abortMultipartUpload(
                            session.getObjectKey(), session.getOssUploadId());
                    if (objectStorageService.headObject(session.getObjectKey()) != null) {
                        objectStorageService.deleteObject(session.getObjectKey());
                    }
                }
                transactionTemplate.executeWithoutResult(status -> markSessionExpired(session));
            } catch (RuntimeException exception) {
                LOGGER.warn("清理过期上传会话失败，sessionId={}", session.getId(), exception);
            }
        });
    }

    private void cleanupPendingObjects() {
        List<StorageObjectEntity> objects = storageObjectMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(STORAGE_OBJECT.STATUS.eq(OBJECT_PENDING_DELETE))
                        .orderBy(STORAGE_OBJECT.UPDATED_AT.asc())
                        .limit(CLEANUP_BATCH_SIZE));
        objects.forEach(object -> {
            try {
                objectStorageService.deleteObject(object.getObjectKey());
                transactionTemplate.executeWithoutResult(status -> markObjectDeleted(object));
            } catch (RuntimeException exception) {
                LOGGER.warn("删除待清理OSS对象失败，storageObjectId={}", object.getId(), exception);
            }
        });
    }

    private void markSessionExpired(UploadSessionEntity session) {
        UpdateWrapper<UploadSessionEntity> update = UpdateWrapper.of(UploadSessionEntity.class)
                .set(UPLOAD_SESSION.STATUS, UPLOAD_EXPIRED)
                .set(UPLOAD_SESSION.UPDATED_BY, session.getUpdatedBy());
        uploadSessionMapper.updateByCondition(update.toEntity(),
                UPLOAD_SESSION.ID.eq(session.getId())
                        .and(UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED)));
    }

    private void markObjectDeleted(StorageObjectEntity object) {
        UpdateWrapper<StorageObjectEntity> update = UpdateWrapper.of(StorageObjectEntity.class)
                .set(STORAGE_OBJECT.STATUS, OBJECT_DELETED)
                .set(STORAGE_OBJECT.UPDATED_BY, object.getUpdatedBy());
        storageObjectMapper.updateByCondition(update.toEntity(),
                STORAGE_OBJECT.ID.eq(object.getId())
                        .and(STORAGE_OBJECT.STATUS.eq(OBJECT_PENDING_DELETE)));
    }

    private void markPrivateSessionExpired(PrivateImageUploadSessionEntity session) {
        UpdateWrapper<PrivateImageUploadSessionEntity> update =
                UpdateWrapper.of(PrivateImageUploadSessionEntity.class)
                        .set(PRIVATE_IMAGE_UPLOAD_SESSION.STATUS, UPLOAD_EXPIRED)
                        .set(PRIVATE_IMAGE_UPLOAD_SESSION.UPDATED_BY, session.getUpdatedBy());
        privateImageUploadSessionMapper.updateByCondition(update.toEntity(),
                PRIVATE_IMAGE_UPLOAD_SESSION.ID.eq(session.getId())
                        .and(PRIVATE_IMAGE_UPLOAD_SESSION.STATUS.eq(UPLOAD_INITIATED)));
    }
}

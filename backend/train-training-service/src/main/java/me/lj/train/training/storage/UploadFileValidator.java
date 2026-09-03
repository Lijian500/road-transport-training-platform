package me.lj.train.training.storage;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.training.config.OssStorageProperties;
import me.lj.train.training.support.TrainingGuard;

import java.util.Locale;
import java.util.Set;

import static me.lj.train.training.constant.TrainingConstants.UPLOAD_COVER;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_FACE_REFERENCE;
import static me.lj.train.training.constant.TrainingConstants.UPLOAD_VIDEO;

/**
 * 上传文件声明信息与真实文件头校验。
 */
public final class UploadFileValidator {

    private static final Set<String> COVER_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private UploadFileValidator() {
    }

    public static FileDeclaration validateVideo(
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            int durationSeconds,
            OssStorageProperties properties) {
        String filename = normalizeFilename(originalFilename);
        String normalizedContentType = normalizeContentType(contentType);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".mp4")
                || !"video/mp4".equals(normalizedContentType)) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID,
                    "视频仅支持MP4格式");
        }
        validateSize(fileSizeBytes, properties.getMaxVideoBytes(), "视频");
        if (durationSeconds <= 0 || durationSeconds > 86_400) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID,
                    "视频时长必须大于0且不能超过24小时");
        }
        return new FileDeclaration(filename, normalizedContentType, "mp4", UPLOAD_VIDEO);
    }

    public static FileDeclaration validateCover(
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            OssStorageProperties properties) {
        String filename = normalizeFilename(originalFilename);
        String normalizedContentType = normalizeContentType(contentType);
        String extension = extension(filename);
        boolean extensionMatches = switch (normalizedContentType) {
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/png" -> "png".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            default -> false;
        };
        if (!COVER_CONTENT_TYPES.contains(normalizedContentType) || !extensionMatches) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID,
                    "封面仅支持JPG、PNG或WebP格式，扩展名需与内容类型一致");
        }
        validateSize(fileSizeBytes, properties.getMaxCoverBytes(), "封面");
        return new FileDeclaration(filename, normalizedContentType, extension, UPLOAD_COVER);
    }

    public static FileDeclaration validateFaceReference(
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            OssStorageProperties properties) {
        FileDeclaration cover = validateCover(
                originalFilename, contentType, fileSizeBytes, properties);
        validateSize(fileSizeBytes, properties.getMaxFaceReferenceBytes(), "人脸登记照");
        return new FileDeclaration(
                cover.filename(), cover.contentType(), cover.extension(), UPLOAD_FACE_REFERENCE);
    }

    public static void validateObject(
            String uploadType,
            String expectedContentType,
            long expectedSize,
            ObjectStorageService.ObjectMetadata metadata,
            byte[] prefix) {
        if (metadata == null) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID, "OSS对象不存在");
        }
        if (metadata.sizeBytes() != expectedSize) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    "OSS对象大小与上传会话不一致");
        }
        String actualContentType = normalizeContentType(metadata.contentType());
        if (!normalizeContentType(expectedContentType).equals(actualContentType)) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    "OSS对象内容类型与上传会话不一致");
        }
        boolean validHeader = UPLOAD_VIDEO.equals(uploadType)
                ? isMp4(prefix)
                : isJpeg(prefix) || isPng(prefix) || isWebp(prefix);
        if (!validHeader) {
            throw new BusinessException(AppErrorCode.STORAGE_OBJECT_INVALID,
                    UPLOAD_VIDEO.equals(uploadType)
                            ? "视频文件头不是有效MP4"
                            : UPLOAD_FACE_REFERENCE.equals(uploadType)
                                    ? "人脸登记照文件头格式无效" : "封面文件头格式无效");
        }
    }

    private static String normalizeFilename(String value) {
        String filename = TrainingGuard.requireText(value, "原文件名", 255);
        if (filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID, "原文件名不能包含路径");
        }
        return filename;
    }

    private static String normalizeContentType(String value) {
        if (value == null) {
            return "";
        }
        int parameterIndex = value.indexOf(';');
        String normalized = parameterIndex < 0 ? value : value.substring(0, parameterIndex);
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static void validateSize(long value, long maximum, String label) {
        if (value <= 0 || value > maximum) {
            throw new BusinessException(AppErrorCode.UPLOAD_FILE_INVALID,
                    label + "大小必须大于0且不能超过" + maximum + "字节");
        }
    }

    private static boolean isMp4(byte[] data) {
        return data != null && data.length >= 8
                && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p';
    }

    private static boolean isJpeg(byte[] data) {
        return data != null && data.length >= 3
                && unsigned(data[0]) == 0xFF && unsigned(data[1]) == 0xD8
                && unsigned(data[2]) == 0xFF;
    }

    private static boolean isPng(byte[] data) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (data == null || data.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(data[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] data) {
        return data != null && data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    public record FileDeclaration(
            String filename,
            String contentType,
            String extension,
            String uploadType) {
    }
}

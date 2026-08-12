package me.lj.train.api.training;

import me.lj.train.common.core.page.PageRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程管理RPC请求及响应模型。
 */
public final class CourseModels {

    private CourseModels() {
    }

    public record CourseQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreateCourseCommand(
            String name,
            String description,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds) implements Serializable {
    }

    public record UpdateCourseCommand(
            Long id,
            String name,
            String description,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds) implements Serializable {
    }

    public record ChangeCourseStatusCommand(Long id, String status) implements Serializable {
    }

    public record UpdateCoursewareCommand(
            Long courseId,
            Long id,
            String title) implements Serializable {
    }

    public record DeleteCoursewareCommand(Long courseId, Long id) implements Serializable {
    }

    public record ReorderCoursewaresCommand(
            Long courseId,
            List<Long> coursewareIds) implements Serializable {
    }

    public record CourseView(
            Long id,
            String name,
            String description,
            Long coverObjectId,
            String coverFilename,
            Long coverSizeBytes,
            String coverContentType,
            int requiredDurationSeconds,
            boolean allowSeek,
            int progressReportIntervalSeconds,
            int studyToleranceSeconds,
            String status,
            boolean everEnabled,
            int coursewareCount,
            long totalVideoDurationSeconds,
            List<CoursewareView> coursewares,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) implements Serializable {
    }

    public record CoursewareView(
            Long id,
            Long storageObjectId,
            String title,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            int durationSeconds,
            int sortOrder,
            LocalDateTime createdAt) implements Serializable {
    }
}

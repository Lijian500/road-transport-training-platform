package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 人脸抽验提交日志实体，照片仅保留不可逆摘要。 */
@Table("face_check_log")
public class FaceCheckLogEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long taskId;
    private Long sessionId;
    private String requestId;
    private int attemptNo;
    private String result;
    private String failureReason;
    private Double similarity;
    private long elapsedMs;
    private String imageSha256;
    private String responsePayload;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { this.taskId = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { this.sessionId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int value) { this.attemptNo = value; }
    public String getResult() { return result; }
    public void setResult(String value) { this.result = value; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String value) { this.failureReason = value; }
    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double value) { this.similarity = value; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long value) { this.elapsedMs = value; }
    public String getImageSha256() { return imageSha256; }
    public void setImageSha256(String value) { this.imageSha256 = value; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String value) { this.responsePayload = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}

package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 关键学习事件和幂等响应实体。 */
@Table("study_event_log")
public class StudyEventLogEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private Long sessionId;
    private String requestId;
    private long sequenceNo;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long coursewareSnapshotId;
    private long reportedPositionMs;
    private long confirmedPositionMs;
    private long creditedDurationMs;
    private String resultCode;
    private String responsePayload;
    private LocalDateTime serverTime;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long value) { this.enterpriseId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { this.userId = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { this.sessionId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }
    public long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(long value) { this.sequenceNo = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String value) { this.fromStatus = value; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String value) { this.toStatus = value; }
    public Long getCoursewareSnapshotId() { return coursewareSnapshotId; }
    public void setCoursewareSnapshotId(Long value) { this.coursewareSnapshotId = value; }
    public long getReportedPositionMs() { return reportedPositionMs; }
    public void setReportedPositionMs(long value) { this.reportedPositionMs = value; }
    public long getConfirmedPositionMs() { return confirmedPositionMs; }
    public void setConfirmedPositionMs(long value) { this.confirmedPositionMs = value; }
    public long getCreditedDurationMs() { return creditedDurationMs; }
    public void setCreditedDurationMs(long value) { this.creditedDurationMs = value; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String value) { this.resultCode = value; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String value) { this.responsePayload = value; }
    public LocalDateTime getServerTime() { return serverTime; }
    public void setServerTime(LocalDateTime value) { this.serverTime = value; }
}

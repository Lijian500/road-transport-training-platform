package me.lj.train.learning.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/** 学习任务事件Outbox实体。 */
@Table("mq_outbox")
public class MqOutboxEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private String eventId;
    private String businessKey;
    private String aggregateType;
    private Long aggregateId;
    private String routingKey;
    private String payload;
    private String status;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getEventId() { return eventId; }
    public void setEventId(String value) { this.eventId = value; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String value) { this.businessKey = value; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String value) { this.aggregateType = value; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long value) { this.aggregateId = value; }
    public String getRoutingKey() { return routingKey; }
    public void setRoutingKey(String value) { this.routingKey = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { this.payload = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int value) { this.retryCount = value; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime value) { this.nextRetryAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { this.lastError = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime value) { this.sentAt = value; }
}

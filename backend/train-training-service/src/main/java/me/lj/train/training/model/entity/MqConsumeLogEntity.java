package me.lj.train.training.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * RabbitMQ任务投影消费幂等记录。
 */
@Table("mq_consume_log")
public class MqConsumeLogEntity {

    @Id(keyType = KeyType.None)
    private Long id;
    private String consumerName;
    private String eventId;
    private String eventType;
    private LocalDateTime processedAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String value) { this.consumerName = value; }
    public String getEventId() { return eventId; }
    public void setEventId(String value) { this.eventId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime value) { this.processedAt = value; }
}

package me.lj.train.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学习会话超时和Outbox非敏感配置。
 */
@ConfigurationProperties(prefix = "learning")
public class LearningProperties {

    private int sessionIdleHours = 24;
    private long timeoutScanIntervalMs = 5000L;
    private long outboxPollIntervalMs = 1000L;
    private int outboxBatchSize = 100;
    private int outboxMaxRetries = 10;

    public int getSessionIdleHours() { return sessionIdleHours; }
    public void setSessionIdleHours(int value) { this.sessionIdleHours = value; }
    public long getTimeoutScanIntervalMs() { return timeoutScanIntervalMs; }
    public void setTimeoutScanIntervalMs(long value) { this.timeoutScanIntervalMs = value; }
    public long getOutboxPollIntervalMs() { return outboxPollIntervalMs; }
    public void setOutboxPollIntervalMs(long value) { this.outboxPollIntervalMs = value; }
    public int getOutboxBatchSize() { return outboxBatchSize; }
    public void setOutboxBatchSize(int value) { this.outboxBatchSize = value; }
    public int getOutboxMaxRetries() { return outboxMaxRetries; }
    public void setOutboxMaxRetries(int value) { this.outboxMaxRetries = value; }
}

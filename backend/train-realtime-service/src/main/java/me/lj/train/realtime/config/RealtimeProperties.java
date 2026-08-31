package me.lj.train.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 实时学习连接、心跳和协议限制配置。 */
@ConfigurationProperties(prefix = "app.realtime")
public class RealtimeProperties {

    private List<String> allowedOrigins = new ArrayList<String>(Arrays.asList(
            "http://localhost:5173", "http://127.0.0.1:5173"));
    private int heartbeatIntervalSeconds = 20;
    private int connectionTimeoutSeconds = 60;
    private int authRecheckSeconds = 60;
    private int maxMessageBytes = 16_384;
    private int ackTimeoutSeconds = 10;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? new ArrayList<String>() : new ArrayList<String>(allowedOrigins);
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getAuthRecheckSeconds() {
        return authRecheckSeconds;
    }

    public void setAuthRecheckSeconds(int authRecheckSeconds) {
        this.authRecheckSeconds = authRecheckSeconds;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public void setMaxMessageBytes(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    public int getAckTimeoutSeconds() {
        return ackTimeoutSeconds;
    }

    public void setAckTimeoutSeconds(int ackTimeoutSeconds) {
        this.ackTimeoutSeconds = ackTimeoutSeconds;
    }
}

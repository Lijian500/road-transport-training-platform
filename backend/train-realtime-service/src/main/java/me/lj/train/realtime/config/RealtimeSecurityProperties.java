package me.lj.train.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 实时服务JWT校验配置。 */
@ConfigurationProperties(prefix = "app.security")
public class RealtimeSecurityProperties {

    private String issuer = "road-transport-training";
    private String publicKeyPath;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }
}

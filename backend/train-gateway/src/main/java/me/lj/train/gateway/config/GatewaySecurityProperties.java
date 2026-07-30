package me.lj.train.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway JWT校验配置。
 */
@ConfigurationProperties(prefix = "app.security")
public class GatewaySecurityProperties {

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

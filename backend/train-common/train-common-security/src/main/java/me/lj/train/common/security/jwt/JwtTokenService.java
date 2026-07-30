package me.lj.train.common.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.model.LoginUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * RSA Access Token签发与校验服务。
 */
public class JwtTokenService {

    private final String issuer;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenService(String issuer, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.issuer = issuer;
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    public JwtTokenService(String issuer, RSAPublicKey publicKey) {
        this.issuer = issuer;
        this.encoder = null;
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    public String createAccessToken(LoginUser loginUser, String sessionId, Duration ttl) {
        if (encoder == null) {
            throw new IllegalStateException("当前服务未配置JWT私钥");
        }
        Instant issuedAt = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(loginUser.getUserId()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(SecurityConstants.CLAIM_USERNAME, loginUser.getUsername())
                .claim(SecurityConstants.CLAIM_SESSION_ID, sessionId)
                .claim(SecurityConstants.CLAIM_LOGIN_VERSION, loginUser.getLoginVersion());
        if (loginUser.getEnterpriseId() != null) {
            builder.claim(SecurityConstants.CLAIM_ENTERPRISE_ID, String.valueOf(loginUser.getEnterpriseId()));
        }
        return encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }

    public AccessTokenClaims decode(String token) {
        Jwt jwt = decoder.decode(token);
        if (!issuer.equals(jwt.getClaimAsString("iss"))) {
            throw new IllegalArgumentException("JWT签发方不正确");
        }
        String enterpriseId = jwt.getClaimAsString(SecurityConstants.CLAIM_ENTERPRISE_ID);
        Number loginVersion = jwt.getClaim(SecurityConstants.CLAIM_LOGIN_VERSION);
        return new AccessTokenClaims(
                Long.valueOf(jwt.getSubject()),
                enterpriseId == null || enterpriseId.isEmpty() ? null : Long.valueOf(enterpriseId),
                jwt.getClaimAsString(SecurityConstants.CLAIM_USERNAME),
                jwt.getClaimAsString(SecurityConstants.CLAIM_SESSION_ID),
                loginVersion == null ? 0L : loginVersion.longValue());
    }
}

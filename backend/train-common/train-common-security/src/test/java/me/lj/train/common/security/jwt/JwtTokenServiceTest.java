package me.lj.train.common.security.jwt;

import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void shouldCreateAndDecodeRsaAccessToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        JwtTokenService tokenService = new JwtTokenService(
                "road-transport-training",
                (RSAPrivateKey) keyPair.getPrivate(),
                (RSAPublicKey) keyPair.getPublic());
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1001L);
        loginUser.setEnterpriseId(2001L);
        loginUser.setUsername("admin");
        loginUser.setLoginVersion(3L);

        String token = tokenService.createAccessToken(loginUser, "session-1", Duration.ofMinutes(30));
        AccessTokenClaims claims = tokenService.decode(token);

        assertThat(claims.getUserId()).isEqualTo(1001L);
        assertThat(claims.getEnterpriseId()).isEqualTo(2001L);
        assertThat(claims.getSessionId()).isEqualTo("session-1");
        assertThat(claims.getLoginVersion()).isEqualTo(3L);
    }
}

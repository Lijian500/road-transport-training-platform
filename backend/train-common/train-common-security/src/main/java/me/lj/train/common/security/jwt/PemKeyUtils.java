package me.lj.train.common.security.jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 读取PKCS#8私钥及X.509公钥。
 */
public final class PemKeyUtils {

    private PemKeyUtils() {
    }

    public static RSAPrivateKey readPrivateKey(String keyPath) {
        try {
            String content = readPem(keyPath, "PRIVATE KEY");
            byte[] bytes = Base64.getMimeDecoder().decode(content);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取JWT私钥：" + keyPath, exception);
        }
    }

    public static RSAPublicKey readPublicKey(String keyPath) {
        try {
            String content = readPem(keyPath, "PUBLIC KEY");
            byte[] bytes = Base64.getMimeDecoder().decode(content);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取JWT公钥：" + keyPath, exception);
        }
    }

    private static String readPem(String keyPath, String type) throws IOException {
        if (keyPath == null || keyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT密钥路径不能为空");
        }
        String content = Files.readString(Path.of(keyPath), StandardCharsets.UTF_8);
        return content
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}

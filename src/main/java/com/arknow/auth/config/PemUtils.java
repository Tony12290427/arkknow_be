package com.arknow.auth.config;

import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PemUtils {
    private PemUtils() {}

    public static RSAPublicKey loadPublicKey(Resource resource) {
        try {
            String pem = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                     .replace("-----END PUBLIC KEY-----", "")
                     .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load public key", e);
        }
    }

    public static RSAPrivateKey loadPrivateKey(Resource resource) {
        try {
            String pem = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                     .replace("-----END PRIVATE KEY-----", "")
                     .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load private key", e);
        }
    }
}

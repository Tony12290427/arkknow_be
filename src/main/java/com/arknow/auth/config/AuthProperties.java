package com.arknow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * Type-safe configuration holder for all auth-related properties.
 * <p>
 * Binds the {@code auth.*} namespace from {@code application.yml} into structured nested classes.
 * Using {@code @ConfigurationProperties} instead of scattered {@code @Value} annotations keeps
 * related configuration together and enables IDE auto-completion.
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private final Jwt jwt = new Jwt();
    private final Verification verification = new Verification();
    private final Password password = new Password();

    /** JWT signing, issuig, and expiry configuration. */
    @Data
    public static class Jwt {
        /** The {@code iss} claim value. */
        private String issuer = "arkknow";
        /** Access token lifetime. Default 15 minutes to limit the blast radius of a leaked token. */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        /** Refresh token lifetime. Longer-lived but stored in a Redis whitelist for revocation. */
        private Duration refreshTokenTtl = Duration.ofDays(7);
        /** The {@code kid} header value, used for key rotation identification. */
        private String keyId = "arkknow-key";
        /** PEM-encoded RSA private key (PKCS#8). Loaded at startup; if missing, the app fails fast. */
        private Resource privateKey;
        /** PEM-encoded RSA public key (X.509). Used for signature verification. */
        private Resource publicKey;
    }

    /** Verification code policy. */
    @Data
    public static class Verification {
        /** Number of digits in the generated code. 6 digits = 1M combinations. */
        private int codeLength = 6;
        /** How long the code remains valid after issuance. */
        private Duration ttl = Duration.ofMinutes(5);
        /** Maximum wrong attempts before the code is locked for a cooldown period. */
        private int maxAttempts = 5;
        /** Minimum interval between successive send requests for the same identifier. */
        private Duration sendInterval = Duration.ofSeconds(60);
        /** Maximum codes one identifier can receive per calendar day. */
        private int dailyLimit = 10;
    }

    /** Password policy enforced during registration and password changes. */
    @Data
    public static class Password {
        /** BCrypt cost factor. 12 = 2^12 rounds, balancing security (~0.3s/hash) and user experience. */
        private int bcryptStrength = 12;
        /** Minimum password length in characters. */
        private int minLength = 8;
    }
}

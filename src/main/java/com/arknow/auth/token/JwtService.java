package com.arknow.auth.token;

import com.arknow.auth.config.AuthProperties;
import com.arknow.auth.config.PemUtils;
import com.arknow.user.domain.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT token issuance, verification, and claim extraction.
 * <p>
 * Uses RS256 (RSA + SHA-256) asymmetric signing so that other services can verify tokens
 * using only the public key, without needing the private key. This is essential for a
 * distributed architecture where multiple services independently validate access tokens.
 * <p>
 * Access tokens carry user identity claims (uid, nickname) and expire quickly (15 min).
 * Refresh tokens carry minimal claims and are tracked in a Redis whitelist for revocation.
 */
@Service
public class JwtService {
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "uid";

    private final AuthProperties properties;
    private final Clock clock = Clock.systemUTC();
    /** Loaded from PEM resource at startup via {@link #initKeys()}. */
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
    }

    /** Parses PEM-encoded key files into Java RSA key objects. Fails fast if keys are missing. */
    @PostConstruct
    void initKeys() {
        this.privateKey = PemUtils.loadPrivateKey(properties.getJwt().getPrivateKey());
        this.publicKey = PemUtils.loadPublicKey(properties.getJwt().getPublicKey());
    }

    /**
     * Issues a pair of access and refresh tokens for a user.
     * <p>
     * The refresh token's JWT ID is stored in Redis for whitelist-based revocation.
     * The access token is fully stateless — its validity depends only on the signature and expiry.
     */
    public TokenPair issueTokenPair(User user) {
        String refreshTokenId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now(clock);
        Instant accessExpiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.getJwt().getRefreshTokenTtl());

        String accessToken = encodeToken(user, issuedAt, accessExpiresAt, "access", UUID.randomUUID().toString());
        String refreshToken = encodeRefreshToken(user, issuedAt, refreshExpiresAt, refreshTokenId);
        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, refreshTokenId);
    }

    /**
     * Decodes and verifies a JWT string.
     *
     * @return the parsed signed JWT
     * @throws IllegalArgumentException if the token is malformed or signature is invalid
     */
    public SignedJWT decode(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            return jwt;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode JWT", e);
        }
    }

    /** Extracts the {@code uid} claim as a long. */
    public long extractUserId(SignedJWT jwt) {
        try {
            Object claim = jwt.getJWTClaimsSet().getClaim(CLAIM_USER_ID);
            if (claim instanceof Number number) return number.longValue();
            if (claim instanceof String text) return Long.parseLong(text);
            throw new IllegalArgumentException("Invalid user id in token");
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Failed to extract user id", e);
        }
    }

    /** Extracts the {@code token_type} claim ("access" or "refresh"). */
    public String extractTokenType(SignedJWT jwt) {
        try {
            Object claim = jwt.getJWTClaimsSet().getClaim(CLAIM_TOKEN_TYPE);
            return claim != null ? claim.toString() : "";
        } catch (java.text.ParseException e) {
            return "";
        }
    }

    /** Extracts the JWT ID ({@code jti}) claim. */
    public String extractTokenId(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet().getJWTID();
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Failed to extract token id", e);
        }
    }

    /** Encodes an access token with full user claims for convenience in downstream services. */
    private String encodeToken(User user, Instant issuedAt, Instant expiresAt, String tokenType, String tokenId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getJwt().getIssuer())
                    .subject(String.valueOf(user.getId()))
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(tokenId)
                    .claim(CLAIM_TOKEN_TYPE, tokenType)
                    .claim(CLAIM_USER_ID, user.getId())
                    .claim("nickname", user.getNickname())
                    .build();
            return sign(claims);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode token", e);
        }
    }

    /** Encodes a refresh token with minimal claims to keep the payload small. */
    private String encodeRefreshToken(User user, Instant issuedAt, Instant expiresAt, String tokenId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getJwt().getIssuer())
                    .subject(String.valueOf(user.getId()))
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(tokenId)
                    .claim(CLAIM_TOKEN_TYPE, "refresh")
                    .claim(CLAIM_USER_ID, user.getId())
                    .build();
            return sign(claims);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode refresh token", e);
        }
    }

    /** Signs a claims set with RS256 and returns the serialized JWT string. */
    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(properties.getJwt().getKeyId()).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to sign JWT", e);
        }
    }
}

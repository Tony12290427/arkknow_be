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

@Service
public class JwtService {
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USER_ID = "uid";

    private final AuthProperties properties;
    private final Clock clock = Clock.systemUTC();
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initKeys() {
        this.privateKey = PemUtils.loadPrivateKey(properties.getJwt().getPrivateKey());
        this.publicKey = PemUtils.loadPublicKey(properties.getJwt().getPublicKey());
    }

    public TokenPair issueTokenPair(User user) {
        String refreshTokenId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now(clock);
        Instant accessExpiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issuedAt.plus(properties.getJwt().getRefreshTokenTtl());

        String accessToken = encodeToken(user, issuedAt, accessExpiresAt, "access", UUID.randomUUID().toString());
        String refreshToken = encodeRefreshToken(user, issuedAt, refreshExpiresAt, refreshTokenId);
        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, refreshTokenId);
    }

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

    public String extractTokenType(SignedJWT jwt) {
        try {
            Object claim = jwt.getJWTClaimsSet().getClaim(CLAIM_TOKEN_TYPE);
            return claim != null ? claim.toString() : "";
        } catch (java.text.ParseException e) {
            return "";
        }
    }

    public String extractTokenId(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet().getJWTID();
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException("Failed to extract token id", e);
        }
    }

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

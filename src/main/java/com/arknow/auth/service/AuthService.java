package com.arknow.auth.service;

import com.arknow.auth.api.dto.*;
import com.arknow.auth.audit.LoginLogService;
import com.arknow.auth.config.AuthProperties;
import com.arknow.auth.model.ClientInfo;
import com.arknow.auth.model.IdentifierType;
import com.arknow.auth.token.JwtService;
import com.arknow.auth.token.RefreshTokenStore;
import com.arknow.auth.token.TokenPair;
import com.arknow.auth.util.IdentifierValidator;
import com.arknow.auth.verification.*;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.user.domain.User;
import com.arknow.user.service.UserService;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Core authentication orchestration service.
 * <p>
 * Coordinates the full lifecycle of auth operations: send-code, register, login,
 * refresh, logout, and current-user lookup. This service acts as a facade:
 * it delegates to specialized services ({@link VerificationService}, {@link JwtService},
 * {@link RefreshTokenStore}, etc.) while owning the process flow and business rules.
 * <p>
 * Each public method is self-contained: if {@code register} is called without first
 * calling {@code sendCode}, it still validates independently. No method trusts the caller.
 */
@Service
public class AuthService {
    private final UserService userService;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginLogService loginLogService;
    private final AuthProperties authProperties;

    public AuthService(UserService userService, VerificationService verificationService,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenStore refreshTokenStore, LoginLogService loginLogService,
                       AuthProperties authProperties) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.loginLogService = loginLogService;
        this.authProperties = authProperties;
    }

    // ==================== sendCode ====================

    /**
     * Sends a verification code after validating the identifier and checking existence
     * against the requested scene's rules.
     * <p>
     * Scene rules:
     * <ul>
     *   <li>REGISTER — identifier must NOT exist</li>
     *   <li>LOGIN / RESET_PASSWORD — identifier MUST exist</li>
     * </ul>
     * This prevents attackers from probing the system for registered accounts:
     * we return a clear error, prioritizing user experience over information hiding
     * (which is acceptable for a community platform, though not for banking systems).
     */
    public SendCodeResponse sendCode(SendCodeRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        String normalized = normalizeIdentifier(request.identifierType(), request.identifier());
        boolean exists = identifierExists(request.identifierType(), normalized);
        if (request.scene() == VerificationScene.REGISTER && exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        if ((request.scene() == VerificationScene.LOGIN || request.scene() == VerificationScene.RESET_PASSWORD) && !exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);
        }
        SendCodeResult result = verificationService.sendCode(request.scene(), normalized, request.identifierType());
        return new SendCodeResponse(result.identifier(), result.scene(), result.expireSeconds());
    }

    // ==================== register ====================

    /**
     * Registers a new user, validates the verification code, and returns signed tokens.
     * <p>
     * Steps:
     * <ol>
     *   <li>Check terms agreement</li>
     *   <li>Validate identifier format and uniqueness</li>
     *   <li>Verify the code (consumes it on success)</li>
     *   <li>Build user with Builder pattern and optional BCrypt password</li>
     *   <li>Persist, issue tokens, store refresh token whitelist, record audit</li>
     * </ol>
     * The password is optional: users can register with verification code only
     * and set a password later. BCrypt with cost=12 makes offline attacks impractical.
     */
    public AuthResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);
        }
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);
        }
        ensureVerificationSuccess(verificationService.verify(VerificationScene.REGISTER, identifier, request.code()));

        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)
                .nickname(generateNickname())
                .avatar(null)
                .bio(null)
                .tagsJson("[]")
                .role("USER")
                .build();

        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        }

        userService.createUser(user);
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);
        loginLogService.record(user.getId(), identifier, "REGISTER", clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");

        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    // ==================== login ====================

    /**
     * Authenticates a user via password or verification code.
     * <p>
     * Dual-channel design:
     * <ul>
     *   <li>PASSWORD: verifies BCrypt hash against stored password</li>
     *   <li>CODE: verifies a one-time verification code sent to the identifier</li>
     * </ul>
     * If neither is provided, throws BAD_REQUEST. Failed login attempts are audited.
     */
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        validateIdentifier(request.identifierType(), request.identifier());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));

        String channel;
        if (StringUtils.hasText(request.password())) {
            channel = "PASSWORD";
            boolean hasHash = StringUtils.hasText(user.getPasswordHash());
            boolean matches = hasHash && passwordEncoder.matches(request.password().trim(), user.getPasswordHash());
            if (!matches) {
                loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "FAILED");
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else if (StringUtils.hasText(request.code())) {
            channel = "CODE";
            ensureVerificationSuccess(verificationService.verify(VerificationScene.LOGIN, identifier, request.code()));
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供验证码或密码");
        }

        TokenPair tokenPair = jwtService.issueTokenPair(user);
        storeRefreshToken(user.getId(), tokenPair);
        loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");
        return new AuthResponse(mapUser(user), mapToken(tokenPair));
    }

    // ==================== refresh ====================

    /**
     * Rotates a refresh token: issues a new token pair, revokes the old refresh token.
     * <p>
     * Token rotation mitigates refresh token theft: if a stolen refresh token is used,
     * the legitimate user's next refresh attempt will fail (because the old token was revoked),
     * alerting the system to potential compromise.
     */
    public TokenResponse refresh(TokenRefreshRequest request) {
        SignedJWT jwt = decodeRefreshToken(request.refreshToken());

        if (!Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        long userId = jwtService.extractUserId(jwt);
        String tokenId = jwtService.extractTokenId(jwt);

        if (!refreshTokenStore.isTokenValid(userId, tokenId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        TokenPair tokenPair = jwtService.issueTokenPair(user);
        refreshTokenStore.revokeToken(userId, tokenId);
        storeRefreshToken(userId, tokenPair);

        return mapToken(tokenPair);
    }

    // ==================== logout ====================

    /**
     * Revokes a refresh token so it can no longer be used to obtain new access tokens.
     * <p>
     * The access token is NOT revoked — it remains valid until its short TTL expires.
     * This is by design: access tokens are stateless and cannot be individually revoked
     * without introducing a server-side check on every request (which would defeat their purpose).
     */
    public void logout(LogoutRequest request) {
        if (!StringUtils.hasText(request.refreshToken())) return;
        decodeRefreshTokenSafely(request.refreshToken()).ifPresent(jwt -> {
            if (Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
                long userId = jwtService.extractUserId(jwt);
                String tokenId = jwtService.extractTokenId(jwt);
                refreshTokenStore.revokeToken(userId, tokenId);
            }
        });
    }

    // ==================== reset password ====================

    /**
     * Resets the user's password using a verification code.
     * <p>
     * After a successful reset, all existing refresh tokens for the user are revoked,
     * forcing re-login on all devices. This is a security best practice: if a password
     * is being reset, any existing sessions may belong to an attacker.
     */
    public void resetPassword(PasswordResetRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());
        validatePassword(request.newPassword());
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        ensureVerificationSuccess(verificationService.verify(VerificationScene.RESET_PASSWORD, identifier, request.code()));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));
        userService.updatePassword(user);
        refreshTokenStore.revokeAll(user.getId());
    }

    // ==================== change password ====================

    /**
     * Changes the authenticated user's password after verifying the old password.
     * <p>
     * Users registered via verification code without a password cannot use this endpoint —
     * they must first set a password via the reset-password flow.
     * After a successful change, all existing refresh tokens are revoked, forcing re-login
     * on all devices.
     */
    public void changePassword(long userId, String oldPassword, String newPassword) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (!StringUtils.hasText(user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号未设置密码，请先通过验证码设置密码");
        }
        if (!passwordEncoder.matches(oldPassword.trim(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码错误");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        userService.updatePassword(user);
        refreshTokenStore.revokeAll(user.getId());
    }

    // ==================== me ====================

    /** Returns the current user's profile based on the authenticated JWT. */
    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    // ==================== email bind/unbind ====================

    /**
     * Binds an email to the authenticated user's account.
     * Requires a verification code sent to the target email.
     */
    public void bindEmail(long userId, String email, String code) {
        validateIdentifier(IdentifierType.EMAIL, email);
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (userService.existsByEmail(normalized)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS, "该邮箱已被绑定");
        }

        ensureVerificationSuccess(verificationService.verify(VerificationScene.REGISTER, normalized, code));

        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        user.setEmail(normalized);
        userService.updateEmail(user.getId(), normalized);
    }

    /**
     * Unbinds the email from the authenticated user's account.
     */
    public void unbindEmail(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (!StringUtils.hasText(user.getEmail())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        userService.updateEmail(user.getId(), null);
    }

    // ==================== delete account ====================

    /**
     * Soft-deletes the authenticated user's account.
     * Revokes all refresh tokens, forcing logout on all devices.
     */
    public void deleteAccount(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        userService.softDelete(user.getId());
        refreshTokenStore.revokeAll(user.getId());
    }

    // ==================== helpers ====================

    /** Maps verification statuses to appropriate business exceptions. */
    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result.isSuccess()) return;
        VerificationCodeStatus status = result.status();
        if (status == VerificationCodeStatus.NOT_FOUND || status == VerificationCodeStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (status == VerificationCodeStatus.MISMATCH) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH);
        }
        if (status == VerificationCodeStatus.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验失败");
    }

    /** Validates phone (11 digits starting with 1) or email (RFC-like pattern) format. */
    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");
        }
    }

    /** Enforces minimum length and mixed-character requirements. */
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码不能为空");
        }
        String trimmed = password.trim();
        if (trimmed.length() < authProperties.getPassword().getMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码长度至少" + authProperties.getPassword().getMinLength() + "位");
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码需包含字母和数字");
        }
    }

    /**
     * Normalizes identifiers for case-insensitive comparison.
     * <p>
     * Email addresses are trimmed and lowercased because RFC 5321 specifies that the
     * local part MAY be case-sensitive, but in practice all major providers treat it
     * case-insensitively. We follow the pragmatic approach.
     */
    private String normalizeIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> identifier.trim();
            case EMAIL -> identifier.trim().toLowerCase(Locale.ROOT);
        };
    }

    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);
            case EMAIL -> userService.existsByEmail(identifier);
        };
    }

    private Optional<User> findUserById(long userId) {
        return userService.findById(userId);
    }

    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    /** Stores the refresh token JWT ID in Redis with a TTL matching the token's expiry. */
    private void storeRefreshToken(Long userId, TokenPair tokenPair) {
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());
        if (ttl.isNegative()) ttl = Duration.ZERO;
        refreshTokenStore.storeToken(userId, tokenPair.refreshTokenId(), ttl);
    }

    private String generateNickname() {
        return "知舟用户" + UUID.randomUUID().toString().substring(0, 8);
    }

    private AuthUserResponse mapUser(User user) {
        return new AuthUserResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getPhone(),
                user.getEmail(), user.getZgId(), user.getBirthday(), user.getSchool(),
                user.getBio(), user.getGender(), user.getTagsJson());
    }

    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());
    }

    /** Decodes a refresh token; throws on any failure (malformed, expired, bad signature). */
    private SignedJWT decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decode(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    /** Decodes a refresh token without throwing — used in logout where failure is non-fatal. */
    private Optional<SignedJWT> decodeRefreshTokenSafely(String refreshToken) {
        try {
            return Optional.of(jwtService.decode(refreshToken));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}

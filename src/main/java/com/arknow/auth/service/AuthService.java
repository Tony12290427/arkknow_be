package com.arknow.auth.service;

import com.arknow.auth.api.dto.AuthResponse;
import com.arknow.auth.api.dto.AuthUserResponse;
import com.arknow.auth.api.dto.LoginRequest;
import com.arknow.auth.api.dto.LogoutRequest;
import com.arknow.auth.api.dto.RegisterRequest;
import com.arknow.auth.api.dto.SendCodeRequest;
import com.arknow.auth.api.dto.SendCodeResponse;
import com.arknow.auth.api.dto.TokenRefreshRequest;
import com.arknow.auth.api.dto.TokenResponse;
import com.arknow.auth.audit.LoginLogService;
import com.arknow.auth.config.AuthProperties;
import com.arknow.auth.model.ClientInfo;
import com.arknow.auth.model.IdentifierType;
import com.arknow.auth.token.JwtService;
import com.arknow.auth.token.RefreshTokenStore;
import com.arknow.auth.token.TokenPair;
import com.arknow.auth.util.IdentifierValidator;
import com.arknow.auth.verification.SendCodeResult;
import com.arknow.auth.verification.VerificationCheckResult;
import com.arknow.auth.verification.VerificationCodeStatus;
import com.arknow.auth.verification.VerificationScene;
import com.arknow.auth.verification.VerificationService;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
        SendCodeResult result = verificationService.sendCode(request.scene(), normalized);
        return new SendCodeResponse(result.identifier(), result.scene(), result.expireSeconds());
    }

    // ==================== register ====================

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
                .avatar("https://static.arkknow.com/default-avatar.png")
                .bio(null)
                .tagsJson("[]")
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

    // ==================== me ====================

    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        return mapUser(user);
    }

    // ==================== login ====================

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

    // ==================== helpers ====================

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

    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");
        }
    }

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
                user.getZgId(), user.getBirthday(), user.getSchool(), user.getBio(),
                user.getGender(), user.getTagsJson());
    }

    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());
    }

    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);
            case EMAIL -> userService.findByEmail(identifier);
        };
    }

    private SignedJWT decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decode(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private Optional<SignedJWT> decodeRefreshTokenSafely(String refreshToken) {
        try {
            return Optional.of(jwtService.decode(refreshToken));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}

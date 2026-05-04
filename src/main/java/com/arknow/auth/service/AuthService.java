package com.arknow.auth.service;

import com.arknow.auth.api.dto.SendCodeRequest;
import com.arknow.auth.api.dto.SendCodeResponse;
import com.arknow.auth.model.IdentifierType;
import com.arknow.auth.util.IdentifierValidator;
import com.arknow.auth.verification.SendCodeResult;
import com.arknow.auth.verification.VerificationScene;
import com.arknow.auth.verification.VerificationService;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {
    private final UserService userService;
    private final VerificationService verificationService;

    public AuthService(UserService userService, VerificationService verificationService) {
        this.userService = userService;
        this.verificationService = verificationService;
    }

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

    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");
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
}

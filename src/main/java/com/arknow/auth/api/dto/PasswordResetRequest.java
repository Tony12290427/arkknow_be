package com.arknow.auth.api.dto;

import com.arknow.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for password reset via verification code.
 */
public record PasswordResetRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        @NotBlank(message = "新密码不能为空") String newPassword
) {}

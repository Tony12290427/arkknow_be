package com.arknow.admin.api;

import com.arknow.auth.api.dto.AuthResponse;
import com.arknow.auth.api.dto.AuthUserResponse;
import com.arknow.auth.api.dto.LoginRequest;
import com.arknow.auth.api.dto.LogoutRequest;
import com.arknow.auth.api.dto.TokenRefreshRequest;
import com.arknow.auth.api.dto.TokenResponse;
import com.arknow.auth.model.ClientInfo;
import com.arknow.auth.service.AuthService;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import com.arknow.user.domain.User;
import com.arknow.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AuthService authService;
    private final UserService userService;

    public AdminAuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, resolveClient(httpRequest));
        // Enforce admin role
        long userId = response.user().id();
        User user = userService.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
        if (!"ADMIN".equals(user.getRole())) {
            // Non-admin attempted admin login — immediately revoke issued tokens
            authService.logout(new LogoutRequest(response.token().refreshToken()));
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无管理员权限");
        }
        return response;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getClaimAsString("uid"));
        return authService.me(userId);
    }

    private ClientInfo resolveClient(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(ip, ua);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

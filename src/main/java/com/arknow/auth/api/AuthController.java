package com.arknow.auth.api;

import com.arknow.auth.api.dto.*;
import com.arknow.auth.model.ClientInfo;
import com.arknow.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST API controller.
 * <p>
 * Exposes endpoints for the complete auth lifecycle: send-code, register, login,
 * refresh, logout, and current-user. This controller is intentionally thin —
 * all business logic lives in {@link AuthService}.
 * <p>
 * Client information (IP and User-Agent) is extracted from the HTTP request for
 * audit logging of register and login events.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Sends a one-time verification code to a phone number or email address.
     * <p>
     * The code is not returned in the response — it is delivered out-of-band
     * via SMS or email (or logged to console in dev mode).
     */
    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }

    /**
     * Registers a new user using a verification code and optional password.
     * Returns signed JWT access and refresh tokens so the user is immediately logged in.
     */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, resolveClient(httpRequest));
    }

    /**
     * Logs in via password or verification code (dual-channel).
     * Returns a fresh token pair on success.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, resolveClient(httpRequest));
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * The old refresh token is revoked and a new pair is issued (token rotation).
     */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    /** Revokes the provided refresh token. Returns 204 on success. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /** Returns the current authenticated user's profile information. */
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

    /**
     * Extracts the real client IP, respecting common proxy headers.
     * Checks {@code X-Forwarded-For} first, then {@code X-Real-IP}, then falls back to
     * {@code getRemoteAddr()}. The first IP in {@code X-Forwarded-For} is the original client.
     */
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

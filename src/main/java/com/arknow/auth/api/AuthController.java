package com.arknow.auth.api;

import com.arknow.auth.api.dto.SendCodeRequest;
import com.arknow.auth.api.dto.SendCodeResponse;
import com.arknow.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/send-code")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);
    }
}

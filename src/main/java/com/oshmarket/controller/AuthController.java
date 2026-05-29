package com.oshmarket.controller;

import com.oshmarket.dto.auth.*;
import com.oshmarket.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        return ResponseEntity.ok(authService.login(request, ip));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ContactMaskedResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.initPasswordReset(request));
    }

    @PostMapping("/forgot-password/send-code")
    public ResponseEntity<Map<String, String>> sendCode(@Valid @RequestBody ChooseResetMethodRequest request) {
        authService.sendResetCode(request);
        return ResponseEntity.ok(Map.of("message", "Код отправлен"));
    }

    @PostMapping("/forgot-password/verify-code")
    public ResponseEntity<Map<String, String>> verifyCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        authService.verifyCode(request);
        return ResponseEntity.ok(Map.of("message", "Код подтверждён"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Пароль успешно изменён"));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}

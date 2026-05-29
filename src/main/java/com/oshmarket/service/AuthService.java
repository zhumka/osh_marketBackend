package com.oshmarket.service;

import com.oshmarket.dto.auth.*;
import com.oshmarket.entity.PasswordResetToken;
import com.oshmarket.entity.User;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.PasswordResetTokenRepository;
import com.oshmarket.repository.UserRepository;
import com.oshmarket.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimitService rateLimitService;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${app.password-reset.code-expiration-minutes}")
    private int codeExpirationMinutes;

    private static final String NEUTRAL_ERROR = "Неверный ИНН или пароль";

    public LoginResponse login(LoginRequest request, String clientIp) {
        if (rateLimitService.isBlocked(clientIp)) {
            throw ApiException.badRequest("Слишком много попыток входа. Попробуйте через 15 минут.");
        }

        User user = userRepository.findByInnAndDeletedFalse(request.getInn())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            rateLimitService.recordAttempt(clientIp);
            throw ApiException.badRequest(NEUTRAL_ERROR);
        }

        rateLimitService.clearAttempts(clientIp);

        String token = jwtService.generateToken(user.getId(), user.getInn(), user.getRole());
        return new LoginResponse(token, user.getRole().name(), user.getInn());
    }

    @Transactional
    public ContactMaskedResponse initPasswordReset(ForgotPasswordRequest request) {
        User user = userRepository.findByInnAndDeletedFalse(request.getInn())
                .orElseThrow(() -> ApiException.notFound("Пользователь с таким ИНН не найден"));

        tokenRepository.invalidateAllByUserId(user.getId());

        String resetToken = UUID.randomUUID().toString();
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setTokenHash(sha256(resetToken));
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(codeExpirationMinutes));
        tokenRepository.save(prt);

        return ContactMaskedResponse.builder()
                .resetToken(resetToken)
                .maskedEmail(user.getEmail() != null ? maskEmail(user.getEmail()) : null)
                .maskedPhone(user.getPhone() != null ? maskPhone(user.getPhone()) : null)
                .hasEmail(user.getEmail() != null)
                .hasPhone(user.getPhone() != null)
                .build();
    }

    @Transactional
    public void sendResetCode(ChooseResetMethodRequest request) {
        PasswordResetToken prt = findValidToken(request.getResetToken());
        User user = prt.getUser();

        String code = generateSixDigitCode();
        prt.setCode(code);
        prt.setMethod(request.getMethod());
        tokenRepository.save(prt);

        if ("email".equals(request.getMethod())) {
            if (user.getEmail() == null) throw ApiException.badRequest("Email не привязан к аккаунту");
            emailService.sendPasswordResetLink(user.getEmail(), "Код подтверждения: " + code);
        } else {
            if (user.getPhone() == null) throw ApiException.badRequest("Телефон не привязан к аккаунту");
            smsService.sendPasswordResetCode(user.getPhone(), code);
        }
    }

    @Transactional
    public void verifyCode(VerifyResetCodeRequest request) {
        PasswordResetToken prt = findValidToken(request.getResetToken());
        if (!request.getCode().equals(prt.getCode())) {
            throw ApiException.badRequest("Неверный код подтверждения");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw ApiException.badRequest("Пароли не совпадают");
        }

        PasswordResetToken prt = findValidToken(request.getResetToken());
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        prt.setUsed(true);
        tokenRepository.save(prt);
    }

    private PasswordResetToken findValidToken(String rawToken) {
        String hash = sha256(rawToken);
        PasswordResetToken prt = tokenRepository.findByTokenHashAndUsedFalse(hash)
                .orElseThrow(() -> ApiException.badRequest("Недействительный токен сброса пароля"));
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Срок действия токена истёк");
        }
        return prt;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateSixDigitCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex < 2) return "***@" + email.substring(atIndex + 1);
        return email.substring(0, 2) + "***@" + email.substring(atIndex + 1);
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return phone;
        return phone.substring(0, 5) + "** *** " + digits.substring(digits.length() - 3);
    }
}

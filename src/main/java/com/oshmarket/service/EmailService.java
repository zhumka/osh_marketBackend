package com.oshmarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.from-email}")
    private String fromEmail;

    @Value("${app.from-name}")
    private String fromName;

    public void sendPasswordResetLink(String toEmail, String resetLink) {
        send(toEmail, "Восстановление пароля — Ошский рынок",
                "Для сброса пароля перейдите по ссылке:\n\n" + resetLink +
                "\n\nСсылка действительна 15 минут. Если вы не запрашивали сброс — проигнорируйте это письмо.");
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        send(toEmail, "Код восстановления пароля — Ошский рынок",
                "Ваш код для сброса пароля: " + code +
                "\n\nКод действителен 15 минут. Если вы не запрашивали сброс — проигнорируйте это письмо.");
    }

    public void sendTenantCredentials(String toEmail, String inn, String tempPassword) {
        send(toEmail, "Ваши данные для входа — Ошский рынок",
                "Добро пожаловать!\n\nВаши данные для входа:\nИНН: " + inn +
                "\nПароль: " + tempPassword +
                "\n\nПожалуйста, смените пароль после первого входа.");
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

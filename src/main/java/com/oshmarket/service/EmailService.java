package com.oshmarket.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.from-email}")
    private String fromEmail;

    @Value("${app.from-name}")
    private String fromName;

    /** Аутентифицированный SMTP-аккаунт. Для Gmail отправитель должен совпадать с ним. */
    @Value("${spring.mail.username:}")
    private String smtpUsername;

    // ---- Брендовые цвета и шрифт шаблона ----
    private static final String FONT = "'Segoe UI', Roboto, Oxygen, Ubuntu, 'Helvetica Neue', Arial, sans-serif";
    private static final String BRAND = "#16a34a";        // основной зелёный
    private static final String BRAND_DARK = "#15803d";
    private static final String TEXT = "#1f2937";
    private static final String MUTED = "#6b7280";
    private static final String BG = "#f4f6f8";

    // ============================ Публичные письма ============================

    public void sendPasswordResetCode(String toEmail, String code) {
        String body = """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:%TEXT%;">
                  Здравствуйте! Вы запросили восстановление пароля в системе «Ошский рынок».
                  Введите код подтверждения ниже, чтобы продолжить:
                </p>
                %CODE_BOX%
                <p style="margin:16px 0 0;font-size:13px;line-height:1.6;color:%MUTED%;">
                  Код действителен <b>15 минут</b>. Если вы не запрашивали сброс пароля —
                  просто проигнорируйте это письмо.
                </p>
                """
                .replace("%CODE_BOX%", codeBox(code))
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED);

        sendHtml(toEmail, "Код восстановления пароля — Ошский рынок",
                render("Восстановление пароля", body));
    }

    public void sendTenantCredentials(String toEmail, String inn, String tempPassword) {
        String body = """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:%TEXT%;">
                  Добро пожаловать в систему управления «Ошский рынок»!
                  Для вас создан личный кабинет арендатора. Данные для входа:
                </p>
                %CREDS_BOX%
                <p style="margin:16px 0 0;font-size:13px;line-height:1.6;color:%MUTED%;">
                  В целях безопасности <b>смените пароль</b> сразу после первого входа.
                </p>
                """
                .replace("%CREDS_BOX%", credentialsBox(inn, tempPassword))
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED);

        sendHtml(toEmail, "Ваши данные для входа — Ошский рынок",
                render("Добро пожаловать!", body));
    }

    // ============================ Шаблон ============================

    /** Единый каркас письма: шапка с брендом, заголовок, контент, подвал. */
    private String render(String heading, String contentHtml) {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:%BG%;font-family:%FONT%;-webkit-font-smoothing:antialiased;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:%BG%;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                               style="max-width:600px;width:100%;background:#ffffff;border-radius:14px;overflow:hidden;
                                      box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                          <!-- Шапка -->
                          <tr>
                            <td style="background:%BRAND%;padding:24px 32px;">
                              <span style="color:#ffffff;font-family:%FONT%;font-size:20px;font-weight:700;letter-spacing:0.3px;">
                                🛒 Ошский рынок
                              </span>
                            </td>
                          </tr>
                          <!-- Контент -->
                          <tr>
                            <td style="padding:32px;">
                              <h1 style="margin:0 0 20px;font-family:%FONT%;font-size:22px;font-weight:700;color:%TEXT%;">
                                %HEADING%
                              </h1>
                              %CONTENT%
                            </td>
                          </tr>
                          <!-- Подвал -->
                          <tr>
                            <td style="padding:20px 32px;background:#f0f2f5;font-family:%FONT%;font-size:12px;line-height:1.6;color:%MUTED%;">
                              Это автоматическое письмо — отвечать на него не нужно.<br>
                              © Ошский рынок. Система управления арендой торговых мест.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("%HEADING%", heading)
                .replace("%CONTENT%", contentHtml)
                .replace("%FONT%", FONT)
                .replace("%BRAND%", BRAND)
                .replace("%BG%", BG)
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED);
    }

    /** Блок с кодом подтверждения. */
    private String codeBox(String code) {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                  <tr>
                    <td align="center" style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;padding:20px;">
                      <div style="font-family:%FONT%;font-size:13px;color:%BRAND_DARK%;margin-bottom:8px;">Код подтверждения</div>
                      <div style="font-family:'Courier New',monospace;font-size:34px;font-weight:700;letter-spacing:10px;color:%BRAND_DARK%;">%CODE%</div>
                    </td>
                  </tr>
                </table>
                """
                .replace("%CODE%", code)
                .replace("%FONT%", FONT)
                .replace("%BRAND_DARK%", BRAND_DARK);
    }

    /** Блок с реквизитами для входа (ИНН + пароль). */
    private String credentialsBox(String inn, String password) {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                       style="margin:24px 0;background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;">
                  <tr>
                    <td style="padding:16px 20px;border-bottom:1px solid #e5e7eb;font-family:%FONT%;">
                      <span style="display:inline-block;width:80px;font-size:13px;color:%MUTED%;">ИНН</span>
                      <span style="font-size:16px;font-weight:600;color:%TEXT%;">%INN%</span>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:16px 20px;font-family:%FONT%;">
                      <span style="display:inline-block;width:80px;font-size:13px;color:%MUTED%;">Пароль</span>
                      <span style="font-size:16px;font-weight:600;color:%TEXT%;font-family:'Courier New',monospace;">%PASSWORD%</span>
                    </td>
                  </tr>
                </table>
                """
                .replace("%INN%", inn)
                .replace("%PASSWORD%", password)
                .replace("%FONT%", FONT)
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED);
    }

    // ============================ Отправка ============================

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderAddress(), fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    /** Для Gmail отправитель должен быть аутентифицированным аккаунтом, иначе письмо отклоняется. */
    private String senderAddress() {
        return (smtpUsername != null && !smtpUsername.isBlank()) ? smtpUsername : fromEmail;
    }
}

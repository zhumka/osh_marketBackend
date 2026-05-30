package com.oshmarket.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${app.from-name}")
    private String fromName;

    @Value("${app.resend-from}")
    private String fromAddress;

    // ---- Дизайн-токены (совпадают со стилем веб-приложения) ----
    private static final String FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";
    private static final String ACCENT     = "#0a0a0a";
    private static final String TEXT       = "#0a0a0a";
    private static final String TEXT_SEC   = "#525252";
    private static final String MUTED      = "#737373";
    private static final String BORDER     = "#e5e5e5";
    private static final String BG         = "#fafafa";
    private static final String CARD_BG    = "#ffffff";
    private static final String SUBTLE_BG  = "#f5f5f5";

    // ============================ Публичные методы ============================

    @Async("mailExecutor")
    public void sendPasswordResetCode(String toEmail, String code) {
        String body = """
                <p style="margin:0 0 8px;font-size:15px;line-height:1.6;color:%TEXT%;">
                  Здравствуйте!
                </p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:%TEXT_SEC%;">
                  Вы запросили восстановление пароля в системе «Ошский рынок».
                  Используйте код подтверждения ниже, чтобы продолжить.
                </p>
                %CODE_BOX%
                <div style="margin:24px 0 0;padding:16px;background:%SUBTLE_BG%;border-radius:8px;">
                  <p style="margin:0;font-size:13px;line-height:1.6;color:%TEXT_SEC%;">
                    Код действителен <strong style="color:%TEXT%;">15 минут</strong>.
                    Если вы не запрашивали восстановление пароля — просто проигнорируйте это письмо.
                  </p>
                </div>
                """
                .replace("%CODE_BOX%", codeBox(code))
                .replace("%TEXT%", TEXT)
                .replace("%TEXT_SEC%", TEXT_SEC)
                .replace("%SUBTLE_BG%", SUBTLE_BG);

        sendHtml(toEmail, "Код восстановления пароля — Ошский рынок",
                render("Восстановление пароля", body));
    }

    @Async("mailExecutor")
    public void sendTenantCredentials(String toEmail, String inn, String tempPassword) {
        String body = """
                <p style="margin:0 0 8px;font-size:15px;line-height:1.6;color:%TEXT%;">
                  Добро пожаловать!
                </p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:%TEXT_SEC%;">
                  Для вас создан личный кабинет арендатора в системе управления «Ошский рынок».
                  Используйте данные ниже для входа в систему.
                </p>
                %CREDS_BOX%
                <div style="margin:24px 0 0;padding:16px;background:%SUBTLE_BG%;border-radius:8px;border-left:3px solid %ACCENT%;">
                  <p style="margin:0;font-size:13px;line-height:1.6;color:%TEXT_SEC%;">
                    В целях безопасности <strong style="color:%TEXT%;">смените пароль</strong> сразу после первого входа в систему.
                  </p>
                </div>
                """
                .replace("%CREDS_BOX%", credentialsBox(inn, tempPassword))
                .replace("%TEXT%", TEXT)
                .replace("%TEXT_SEC%", TEXT_SEC)
                .replace("%SUBTLE_BG%", SUBTLE_BG)
                .replace("%ACCENT%", ACCENT);

        sendHtml(toEmail, "Ваши данные для входа — Ошский рынок",
                render("Добро пожаловать", body));
    }

    // ============================ Шаблон ============================

    private String render(String heading, String contentHtml) {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Ошский рынок</title>
                </head>
                <body style="margin:0;padding:0;background:%BG%;font-family:%FONT%;-webkit-font-smoothing:antialiased;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%BG%;">
                    <tr>
                      <td align="center" style="padding:40px 16px;">
                        <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                               style="max-width:560px;width:100%%;background:%CARD_BG%;border-radius:12px;
                                      overflow:hidden;border:1px solid %BORDER%;">
                          <!-- Шапка -->
                          <tr>
                            <td style="padding:24px 32px;border-bottom:1px solid %BORDER%;">
                              <table role="presentation" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="background:%ACCENT%;width:36px;height:36px;border-radius:8px;
                                             text-align:center;vertical-align:middle;">
                                    <span style="color:#ffffff;font-family:%FONT%;font-size:18px;
                                                 font-weight:700;line-height:36px;">О</span>
                                  </td>
                                  <td style="padding-left:12px;vertical-align:middle;">
                                    <div style="font-family:%FONT%;font-size:15px;font-weight:600;
                                                color:%TEXT%;line-height:1.2;">Ошский рынок</div>
                                    <div style="font-family:%FONT%;font-size:12px;color:%MUTED%;
                                                line-height:1.4;margin-top:2px;">Система управления</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <!-- Контент -->
                          <tr>
                            <td style="padding:32px;">
                              <h1 style="margin:0 0 24px;font-family:%FONT%;font-size:24px;font-weight:700;
                                         color:%TEXT%;line-height:1.3;letter-spacing:-0.02em;">
                                %HEADING%
                              </h1>
                              %CONTENT%
                            </td>
                          </tr>
                          <!-- Подвал -->
                          <tr>
                            <td style="padding:20px 32px;background:%SUBTLE_BG%;border-top:1px solid %BORDER%;">
                              <p style="margin:0;font-family:%FONT%;font-size:12px;line-height:1.6;color:%MUTED%;">
                                Это автоматическое письмо — отвечать на него не нужно.
                              </p>
                              <p style="margin:6px 0 0;font-family:%FONT%;font-size:12px;line-height:1.6;color:%MUTED%;">
                                © Ошский рынок · Система управления арендой торговых мест
                              </p>
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
                .replace("%ACCENT%", ACCENT)
                .replace("%BG%", BG)
                .replace("%CARD_BG%", CARD_BG)
                .replace("%SUBTLE_BG%", SUBTLE_BG)
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED)
                .replace("%BORDER%", BORDER);
    }

    private String codeBox(String code) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0;">
                  <tr>
                    <td align="center" style="background:%SUBTLE_BG%;border:1px solid %BORDER%;
                                              border-radius:12px;padding:28px 20px;">
                      <div style="font-family:%FONT%;font-size:12px;font-weight:500;color:%MUTED%;
                                  text-transform:uppercase;letter-spacing:0.08em;margin-bottom:12px;">
                        Код подтверждения
                      </div>
                      <div style="font-family:'SF Mono','Monaco','Roboto Mono','Courier New',monospace;
                                  font-size:36px;font-weight:700;letter-spacing:8px;color:%TEXT%;line-height:1;">
                        %CODE%
                      </div>
                    </td>
                  </tr>
                </table>
                """
                .replace("%CODE%", code)
                .replace("%FONT%", FONT)
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED)
                .replace("%SUBTLE_BG%", SUBTLE_BG)
                .replace("%BORDER%", BORDER);
    }

    private String credentialsBox(String inn, String password) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="margin:0;background:%CARD_BG%;border:1px solid %BORDER%;
                              border-radius:12px;overflow:hidden;">
                  <tr>
                    <td style="padding:16px 20px;border-bottom:1px solid %BORDER%;font-family:%FONT%;">
                      <div style="font-size:12px;font-weight:500;color:%MUTED%;
                                  text-transform:uppercase;letter-spacing:0.06em;margin-bottom:6px;">ИНН</div>
                      <div style="font-size:16px;font-weight:600;color:%TEXT%;
                                  font-family:'SF Mono','Monaco','Roboto Mono','Courier New',monospace;
                                  letter-spacing:0.5px;">%INN%</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:16px 20px;font-family:%FONT%;">
                      <div style="font-size:12px;font-weight:500;color:%MUTED%;
                                  text-transform:uppercase;letter-spacing:0.06em;margin-bottom:6px;">Временный пароль</div>
                      <div style="font-size:16px;font-weight:600;color:%TEXT%;
                                  font-family:'SF Mono','Monaco','Roboto Mono','Courier New',monospace;
                                  letter-spacing:0.5px;">%PASSWORD%</div>
                    </td>
                  </tr>
                </table>
                """
                .replace("%INN%", inn)
                .replace("%PASSWORD%", password)
                .replace("%FONT%", FONT)
                .replace("%TEXT%", TEXT)
                .replace("%MUTED%", MUTED)
                .replace("%CARD_BG%", CARD_BG)
                .replace("%BORDER%", BORDER);
    }

    // ============================ Отправка через Resend API ============================

    private void sendHtml(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY не задан — письмо не отправлено (to={})", to);
            return;
        }
        try {
            Resend resend = new Resend(apiKey);
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromAddress + ">")
                    .to(to)
                    .subject(subject)
                    .html(html)
                    .build();
            resend.emails().send(params);
            log.info("Email sent via Resend to {}", to);
        } catch (ResendException e) {
            log.error("Failed to send email via Resend to {}: {}", to, e.getMessage());
        }
    }
}

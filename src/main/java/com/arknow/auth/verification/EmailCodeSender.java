package com.arknow.auth.verification;

import com.arknow.auth.model.IdentifierType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailCodeSender implements CodeSender {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);
    private final JavaMailSender mailSender;

    public EmailCodeSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public boolean supports(IdentifierType type) {
        return type == IdentifierType.EMAIL;
    }

    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setTo(identifier);
            helper.setSubject("知舟 - 验证码");
            helper.setText(String.format("""
                <div style="font-family:Arial,sans-serif;max-width:400px;margin:0 auto">
                  <h2 style="color:#1a1a1a">知舟 验证码</h2>
                  <p>您的验证码是：</p>
                  <div style="font-size:32px;font-weight:bold;color:#2563eb;padding:12px 0">%s</div>
                  <p>%d 分钟内有效，请勿泄露。</p>
                </div>
                """, code, expireMinutes), true);
            mailSender.send(msg);
            log.info("Email code sent to {}", identifier);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}

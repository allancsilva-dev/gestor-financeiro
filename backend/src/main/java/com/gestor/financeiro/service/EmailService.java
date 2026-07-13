package com.gestor.financeiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Deep link do app Expo (scheme "gestorfinanceiro" em mobile/app.json).
    // Abre a tela reset-password com o token preenchido.
    @Value("${app.reset-password-link-base:gestorfinanceiro://reset-password?token=}")
    private String resetPasswordLinkBase;

    @Value("${app.mail.from:}")
    private String mailFrom;

    // Perfis prod/vps definem spring.mail.host=${SMTP_HOST:} — com env ausente a
    // property existe vazia e o Spring ainda cria o JavaMailSender; o guard é aqui.
    @Value("${spring.mail.host:}")
    private String smtpHost;

    // JavaMailSender só é auto-configurado quando spring.mail.host está definido
    // (perfis prod/vps). Em dev não há SMTP: cai no fallback de log.
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void enviarEmailRecuperacaoSenha(String emailDestino, String token) {
        String maskedEmail = maskEmail(emailDestino);
        String linkRecuperacao = resetPasswordLinkBase + token;

        JavaMailSender mailSender = smtpHost.isBlank() ? null : mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("SMTP não configurado — email de recuperação NÃO enviado para {} (dev fallback)", maskedEmail);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (!mailFrom.isBlank()) {
            message.setFrom(mailFrom);
        }
        message.setTo(emailDestino);
        message.setSubject("Recuperação de senha — Gestor Financeiro");
        message.setText("Recebemos um pedido para redefinir sua senha.\n\n"
                + "Toque no link para redefinir: " + linkRecuperacao + "\n"
                + "Ou copie o código no app: " + token + "\n\n"
                + "O código expira em breve. Se você não pediu a redefinição, ignore este email.");

        // Falha de SMTP não pode virar 500: o endpoint responde igual para email
        // existente ou não, e um erro aqui denunciaria quais emails têm conta.
        try {
            mailSender.send(message);
            log.info("Email de recuperação enviado para {}", maskedEmail);
        } catch (org.springframework.mail.MailException e) {
            // Mensagem da excecao SMTP pode ecoar destinatario/comando; registrar so tipo.
            log.error("Falha ao enviar email de recuperação para {} (tipo={})",
                    maskedEmail, e.getClass().getSimpleName());
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domain;
    }
}

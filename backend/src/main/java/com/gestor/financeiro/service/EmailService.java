package com.gestor.financeiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Deep link do app Expo (scheme "gestorfinanceiro" em mobile/app.json).
    // Abre a tela reset-password com o token preenchido.
    @Value("${app.reset-password-link-base:gestorfinanceiro://reset-password?token=}")
    private String resetPasswordLinkBase;

    /**
     * VERSÃO SIMPLIFICADA - Imprime no console
     *
     * Para produção, você precisaria configurar:
     * - SMTP (Gmail, SendGrid, etc)
     * - JavaMailSender do Spring
     *
     * Mas para desenvolvimento, vamos apenas mostrar no console!
     */
    public void enviarEmailRecuperacaoSenha(String emailDestino, String token) {
        String maskedEmail = maskEmail(emailDestino);
        String linkRecuperacao = resetPasswordLinkBase + token;
        log.info("Email de recuperação solicitado para {}", maskedEmail);

        // TODO: Implementar envio real de email quando for para produção
        // Exemplo com JavaMailSender:
        /*
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailDestino);
        message.setSubject("Recuperação de Senha");
        message.setText("Toque para redefinir sua senha: " + linkRecuperacao
            + "\nOu copie o código no app: " + token);
        mailSender.send(message);
        */
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
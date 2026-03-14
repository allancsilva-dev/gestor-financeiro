package com.gestor.financeiro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
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
        String linkRecuperacao = "http://localhost:5173/reset-password?token=" + token;

        log.info("Email de recuperação solicitado para {}", emailDestino);
        log.debug("Link de recuperação gerado: {}", linkRecuperacao);
        
        // TODO: Implementar envio real de email quando for para produção
        // Exemplo com JavaMailSender:
        /*
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailDestino);
        message.setSubject("Recuperação de Senha");
        message.setText("Link: " + linkRecuperacao);
        mailSender.send(message);
        */
    }
}
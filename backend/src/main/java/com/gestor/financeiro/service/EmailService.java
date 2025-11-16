package com.gestor.financeiro.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {
    
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
        
        System.out.println("\n");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("📧 EMAIL DE RECUPERAÇÃO DE SENHA");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Para: " + emailDestino);
        System.out.println("Assunto: Recuperação de Senha - Financeiro");
        System.out.println("");
        System.out.println("Olá!");
        System.out.println("");
        System.out.println("Você solicitou a recuperação de senha.");
        System.out.println("Clique no link abaixo para redefinir sua senha:");
        System.out.println("");
        System.out.println("🔗 " + linkRecuperacao);
        System.out.println("");
        System.out.println("Este link expira em 1 hora.");
        System.out.println("");
        System.out.println("Se você não solicitou, ignore este email.");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("\n");
        
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
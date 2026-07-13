package com.gestor.financeiro;

import com.gestor.financeiro.service.EmailService;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class EmailServiceTest {
    @Test
    void enviaMensagemQuandoSmtpConfigurado() throws Exception {
        GreenMail smtp = new GreenMail(new ServerSetup(0, null, ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(smtp.getSmtp().getPort());
            EmailService service = service(sender, "127.0.0.1");
            service.enviarEmailRecuperacaoSenha("ana@example.com", "token-secreto");
            assertTrue(smtp.waitForIncomingEmail(3000, 1));
            String body = smtp.getReceivedMessages()[0].getContent().toString();
            assertTrue(body.contains("token-secreto"));
        } finally {
            smtp.stop();
        }
    }

    @Test
    void fallbackSemSmtpNaoFalha() {
        assertDoesNotThrow(() -> service(null, "").enviarEmailRecuperacaoSenha("ana@example.com", "token"));
    }

    @Test
    void falhaSmtpNaoEscapaParaEndpoint() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("indisponível")).when(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        assertDoesNotThrow(() -> service(sender, "smtp").enviarEmailRecuperacaoSenha("ana@example.com", "token"));
    }

    private EmailService service(JavaMailSender sender, String host) {
        StaticListableBeanFactory factory = sender == null
                ? new StaticListableBeanFactory()
                : new StaticListableBeanFactory(Map.of("mailSender", sender));
        EmailService service = new EmailService(factory.getBeanProvider(JavaMailSender.class));
        ReflectionTestUtils.setField(service, "smtpHost", host);
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@example.com");
        ReflectionTestUtils.setField(service, "resetPasswordLinkBase", "gestorfinanceiro://reset-password?token=");
        return service;
    }
}

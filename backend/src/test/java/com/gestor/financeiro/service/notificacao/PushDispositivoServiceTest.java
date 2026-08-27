package com.gestor.financeiro.service.notificacao;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.NotificacaoDispositivo;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.NotificacaoDispositivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registro de aparelho para push. O que precisa ficar travado: token é do aparelho (migra de dono),
 * revogação é do titular, e token fora do formato do Expo não entra.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushDispositivoServiceTest {

    private static final String TOKEN = "ExponentPushToken[abc-123_XYZ]";

    @Autowired PushDispositivoService service;
    @Autowired NotificacaoDispositivoRepository dispositivos;
    @Autowired UsuarioRepository usuarios;

    private Usuario titular;
    private Usuario outro;

    @BeforeEach
    void setup() {
        titular = usuarios.save(TestDataFactory.usuario("Push", "push-" + System.nanoTime() + "@test.local", "h"));
        outro = usuarios.save(TestDataFactory.usuario("Outro", "push-outro-" + System.nanoTime() + "@test.local", "h"));
    }

    @Test
    void registraAparelhoAtivoDoTitular() {
        NotificacaoDispositivo dispositivo = service.registrar(titular.getId(), TOKEN, "ios");

        assertTrue(dispositivo.isAtivo());
        assertEquals("IOS", dispositivo.getPlataforma());
        assertEquals(1, service.ativosDoTitular(titular.getId()).size());
    }

    @Test
    void registrarDeNovoNaoDuplicaOAparelho() {
        service.registrar(titular.getId(), TOKEN, "IOS");
        service.registrar(titular.getId(), TOKEN, "IOS");

        assertEquals(1, dispositivos.count());
    }

    @Test
    void aparelhoMigraDeTitularQuandoOutraContaEntraNele() {
        service.registrar(titular.getId(), TOKEN, "ANDROID");

        service.registrar(outro.getId(), TOKEN, "ANDROID");

        assertEquals(0, service.ativosDoTitular(titular.getId()).size(),
                "dono anterior não pode continuar recebendo aviso em aparelho que não é mais dele");
        assertEquals(1, service.ativosDoTitular(outro.getId()).size());
    }

    @Test
    void tokenForaDoFormatoDoExpoNaoEntra() {
        assertThrows(BusinessException.class, () -> service.registrar(titular.getId(), "token-qualquer", "IOS"));
        assertThrows(BusinessException.class, () -> service.registrar(titular.getId(), TOKEN, "WINDOWS"));
    }

    @Test
    void revogarDesativaSemApagarORegistro() {
        service.registrar(titular.getId(), TOKEN, "IOS");

        service.revogar(titular.getId(), TOKEN);

        assertEquals(0, service.ativosDoTitular(titular.getId()).size());
        assertFalse(dispositivos.findByPushToken(TOKEN).orElseThrow().isAtivo());
    }

    @Test
    void naoRevogaAparelhoDeOutroTitular() {
        service.registrar(titular.getId(), TOKEN, "IOS");

        assertThrows(ResourceNotFoundException.class, () -> service.revogar(outro.getId(), TOKEN));
        assertTrue(dispositivos.findByPushToken(TOKEN).orElseThrow().isAtivo());
    }

    @Test
    void tokenRecusadoPeloServicoDeEntregaSaiDeCirculacao() {
        service.registrar(titular.getId(), TOKEN, "IOS");

        service.desativarTokenNaoRegistrado(TOKEN);

        assertEquals(0, service.ativosDoTitular(titular.getId()).size());
    }
}

package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ContaFixaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V72 — frequencia de recorrencia (BACKLOG-0120). Ate aqui o motor so sabia
 * plusMonths(1). Cada CHECK do banco tem validacao equivalente no service em 4xx: o
 * CHECK e backstop, nunca a mensagem que o usuario ve.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContaFixaFrequenciaTest {

    @Autowired ContaFixaService service;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Carteira carteira;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Frequente", "frequencia-recorrencia@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corrente", new BigDecimal("5000.00")));
    }

    /** Retrocompatibilidade: cliente que nao manda frequencia continua mensal. */
    @Test
    void semFrequenciaNasceMensal() {
        ContaFixa salva = service.criar(nova("Aluguel", null, null), usuario.getId());

        assertEquals(FrequenciaRecorrencia.MENSAL, salva.getFrequencia());
        assertNull(salva.getDataAncora());
    }

    @Test
    void anualAvancaDozeMeses() {
        ContaFixa prime = service.criar(nova("Amazon Prime", FrequenciaRecorrencia.ANUAL, null), usuario.getId());
        LocalDate primeira = prime.getDataProximoVencimento();

        service.realizar(prime.getId(), null, carteira.getId(), usuario.getId(), false);

        ContaFixa depois = contaFixaRepository.findById(prime.getId()).orElseThrow();
        assertEquals(primeira.plusYears(1), depois.getDataProximoVencimento());
    }

    @Test
    void semanalAvancaSeteDias() {
        LocalDate ancora = LocalDate.now();
        ContaFixa semanal = service.criar(nova("Feira", FrequenciaRecorrencia.SEMANAL, ancora), usuario.getId());
        LocalDate primeira = semanal.getDataProximoVencimento();

        service.realizar(semanal.getId(), null, carteira.getId(), usuario.getId(), false);

        ContaFixa depois = contaFixaRepository.findById(semanal.getId()).orElseThrow();
        assertEquals(7, ChronoUnit.DAYS.between(primeira, depois.getDataProximoVencimento()));
        assertEquals(primeira.getDayOfWeek(), depois.getDataProximoVencimento().getDayOfWeek());
    }

    /**
     * "Dia do mes" nao existe em serie sub-mensal. O CHECK do banco recusaria; o service
     * recusa antes, com mensagem de negocio.
     */
    @Test
    void semanalSemAncoraEhRecusadaComMensagemDeNegocio() {
        BusinessException erro = assertThrows(BusinessException.class,
                () -> service.criar(nova("Feira", FrequenciaRecorrencia.SEMANAL, null), usuario.getId()));

        assertEquals("Informe a data da primeira cobrança para recorrência semanal ou quinzenal",
                erro.getMessage());
    }

    /** Sobra de formulario ao trocar a frequencia nao pode virar dado orfao. */
    @Test
    void ancoraEmFrequenciaMensalEhNormalizadaParaNulo() {
        ContaFixa mensal = service.criar(
                nova("Aluguel", FrequenciaRecorrencia.MENSAL, LocalDate.now()), usuario.getId());

        assertNull(mensal.getDataAncora());
    }

    /** Em sub-mensal o diaVencimento vira exibicao, derivado da ancora. */
    @Test
    void diaVencimentoDeSubMensalSaiDaAncora() {
        LocalDate ancora = LocalDate.now().withDayOfMonth(3);
        ContaFixa semanal = service.criar(nova("Feira", FrequenciaRecorrencia.QUINZENAL, ancora), usuario.getId());

        assertEquals(3, semanal.getDiaVencimento());
        assertNotNull(semanal.getDataAncora());
    }

    /**
     * Editar recalcula o vencimento do zero. Se o recalculo cair numa data ja realizada,
     * o proximo realizar bateria no unique de execucoes_recorrencia e a recorrencia
     * travaria em 400 para sempre.
     */
    @Test
    void trocarFrequenciaNaoTravaEmOcorrenciaJaRealizada() {
        ContaFixa mensal = service.criar(nova("Streaming", FrequenciaRecorrencia.MENSAL, null), usuario.getId());
        LocalDate jaRealizada = mensal.getDataProximoVencimento();
        service.realizar(mensal.getId(), null, carteira.getId(), usuario.getId(), false);

        ContaFixa edicao = nova("Streaming", FrequenciaRecorrencia.MENSAL, null);
        edicao.setCarteira(carteira);
        ContaFixa atualizada = service.atualizar(mensal.getId(), edicao, usuario.getId());

        // o recalculo pousaria de novo na data ja realizada; precisa ter avancado
        assertTrue(atualizada.getDataProximoVencimento().isAfter(jaRealizada),
                "ocorrencia ja realizada precisa ser pulada, senao o proximo realizar trava no unique");
        assertEquals(jaRealizada.getDayOfMonth(), atualizada.getDataProximoVencimento().getDayOfMonth());
    }

    /**
     * O caso que o dono do produto tentou e nao conseguiu: "Amazon Prime, todo 15 de
     * marco", cadastrado em outro mes. Antes da V73 a ancora era proibida em ANUAL e a
     * serie saia sempre do mes corrente.
     */
    @Test
    void anualPodeEscolherOMesDoAniversario() {
        LocalDate marco = LocalDate.now().withMonth(3).withDayOfMonth(15);

        ContaFixa prime = service.criar(nova("Amazon Prime", FrequenciaRecorrencia.ANUAL, marco), usuario.getId());

        assertEquals(3, prime.getDataProximoVencimento().getMonthValue(),
                "a cobranca precisa cair em marco, nao no mes do cadastro");
        assertEquals(15, prime.getDataProximoVencimento().getDayOfMonth());
        assertEquals(marco, prime.getDataAncora());
    }

    /**
     * Editar o valor nao pode mover o aniversario. Antes, atualizar recalculava a serie a
     * partir de hoje e uma anual de marco virava uma anual do mes da edicao.
     */
    @Test
    void editarValorNaoMoveOAniversarioDaAnual() {
        LocalDate marco = LocalDate.now().withMonth(3).withDayOfMonth(15);
        ContaFixa prime = service.criar(nova("Amazon Prime", FrequenciaRecorrencia.ANUAL, marco), usuario.getId());
        LocalDate vencimentoOriginal = prime.getDataProximoVencimento();

        ContaFixa edicao = nova("Amazon Prime", FrequenciaRecorrencia.ANUAL, marco);
        edicao.setValorPlanejado(new BigDecimal("99.00"));
        ContaFixa atualizada = service.atualizar(prime.getId(), edicao, usuario.getId());

        assertEquals(vencimentoOriginal, atualizada.getDataProximoVencimento(),
                "mudar so o valor nao pode mexer na data da cobranca");
        assertEquals(0, new BigDecimal("99.00").compareTo(atualizada.getValorPlanejado()));
    }

    /** Trocar a frequencia continua recalculando: aí a serie mudou de verdade. */
    @Test
    void trocarAFrequenciaRecalculaASerie() {
        LocalDate marco = LocalDate.now().withMonth(3).withDayOfMonth(15);
        ContaFixa prime = service.criar(nova("Prime", FrequenciaRecorrencia.ANUAL, marco), usuario.getId());

        ContaFixa edicao = nova("Prime", FrequenciaRecorrencia.MENSAL, null);
        ContaFixa atualizada = service.atualizar(prime.getId(), edicao, usuario.getId());

        assertEquals(FrequenciaRecorrencia.MENSAL, atualizada.getFrequencia());
        assertNull(atualizada.getDataAncora(), "mensal nao guarda ancora");
        assertFalse(atualizada.getDataProximoVencimento().isBefore(LocalDate.now()));
    }

    /** Sem ancora, BIMESTRAL..ANUAL continuam com o comportamento anterior a V73. */
    @Test
    void anualSemAncoraMantemComportamentoAnterior() {
        ContaFixa prime = service.criar(nova("Prime", FrequenciaRecorrencia.ANUAL, null), usuario.getId());

        assertNull(prime.getDataAncora());
        assertFalse(prime.getDataProximoVencimento().isBefore(LocalDate.now()));
        assertEquals(LocalDate.now().getDayOfMonth(), prime.getDataProximoVencimento().getDayOfMonth());
    }

    /**
     * O caso que o unique (conta_fixa_id, data_vencimento) nao pega: mudar a ancora move a
     * serie para tras, para um mes JA COBRADO mas com dia diferente. Sem o piso da ultima
     * execucao, isso vira cobranca dupla sem o banco reclamar.
     */
    @Test
    void mudarAncoraNaoVoltaParaMesJaCobrado() {
        ContaFixa mensal = service.criar(nova("Streaming", FrequenciaRecorrencia.MENSAL, null), usuario.getId());
        LocalDate cobrada = mensal.getDataProximoVencimento();
        service.realizar(mensal.getId(), null, carteira.getId(), usuario.getId(), false);

        // edicao que puxaria a serie para um dia anterior do mesmo mes ja cobrado
        ContaFixa edicao = nova("Streaming", FrequenciaRecorrencia.MENSAL, null);
        edicao.setDiaVencimento(Math.max(1, cobrada.getDayOfMonth() - 1));
        ContaFixa atualizada = service.atualizar(mensal.getId(), edicao, usuario.getId());

        assertTrue(atualizada.getDataProximoVencimento().isAfter(cobrada),
                "a serie nao pode voltar para um mes ja cobrado; ficou em "
                        + atualizada.getDataProximoVencimento() + " e ja cobrou " + cobrada);
    }

    /**
     * O mesmo perigo, agora no caminho que a V73 abriu: a ancora de uma BIMESTRAL sendo
     * puxada para um dia anterior do mes que ja foi cobrado. Aqui a serie sai da ancora,
     * entao sem o piso ela realmente pousaria no mes cobrado — com dia diferente, que e
     * exatamente o que o unique (conta_fixa_id, data_vencimento) nao pega.
     */
    @Test
    void mudarAncoraDeBimestralNaoVoltaParaMesJaCobrado() {
        LocalDate hoje = LocalDate.now();
        ContaFixa bimestral = service.criar(
                nova("Academia", FrequenciaRecorrencia.BIMESTRAL, hoje), usuario.getId());
        LocalDate cobrada = bimestral.getDataProximoVencimento();
        service.realizar(bimestral.getId(), null, carteira.getId(), usuario.getId(), false);

        LocalDate ancoraPuxada = hoje.withDayOfMonth(Math.max(1, hoje.getDayOfMonth() - 1));
        ContaFixa edicao = nova("Academia", FrequenciaRecorrencia.BIMESTRAL, ancoraPuxada);
        ContaFixa atualizada = service.atualizar(bimestral.getId(), edicao, usuario.getId());

        assertEquals(ancoraPuxada, atualizada.getDataAncora(), "a ancora escolhida e respeitada");
        assertTrue(atualizada.getDataProximoVencimento().isAfter(cobrada),
                "a serie nao pode voltar para um mes ja cobrado; ficou em "
                        + atualizada.getDataProximoVencimento() + " e ja cobrou " + cobrada);
    }

    private ContaFixa nova(String nome, FrequenciaRecorrencia frequencia, LocalDate ancora) {
        ContaFixa cf = new ContaFixa();
        cf.setNome(nome);
        cf.setValorPlanejado(new BigDecimal("60.00"));
        cf.setDiaVencimento(LocalDate.now().getDayOfMonth());
        cf.setTipo(TipoTransacao.SAIDA);
        cf.setStatus(StatusPagamento.PENDENTE);
        cf.setRecorrente(true);
        cf.setAtivo(true);
        cf.setCarteira(carteira);
        cf.setFrequencia(frequencia);
        cf.setDataAncora(ancora);
        return cf;
    }
}

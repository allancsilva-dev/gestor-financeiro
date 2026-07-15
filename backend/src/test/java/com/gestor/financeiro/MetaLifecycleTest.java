package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.MovimentoMetaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.MetaService;
import com.gestor.financeiro.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ciclo de vida de metas (ADR-0004 / PROB-0077): conclusão não oculta dinheiro reservado. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetaLifecycleTest {
    @Autowired MetaService metaService;
    @Autowired MetaRepository metaRepository;
    @Autowired MovimentoMetaRepository movimentoMetaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ExportService exportService;

    private Usuario usuario;
    private Carteira carteira;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Metas", "metas-lifecycle@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("10000.00")));
    }

    @Test
    void metaCriadaNasceAtivaEConcluiAoAtingirObjetivoSemSumirDaConsulta() {
        Meta meta = novaMeta("Viagem", "1000.00");

        metaService.adicionarValor(meta.getId(), new BigDecimal("1000.00"), carteira.getId(), usuario.getId());

        Meta concluida = metaRepository.findById(meta.getId()).orElseThrow();
        assertEquals(StatusMeta.CONCLUIDA, concluida.getStatus());
        assertFalse(concluida.getAtiva());
        assertNotNull(concluida.getDataConclusao());
        assertEquals(0, new BigDecimal("1000.00").compareTo(concluida.getValorReservado()));

        // some do filtro default (ATIVA), continua acessível em CONCLUIDA
        assertEquals(0, metaService.listarPorUsuario(usuario.getId(), null, Pageable.unpaged()).getTotalElements());
        assertEquals(1, metaService.listarPorUsuario(usuario.getId(), StatusMeta.CONCLUIDA, Pageable.unpaged()).getTotalElements());
    }

    @Test
    void resgateAbaixoDoObjetivoReativaMeta() {
        Meta meta = novaMeta("Notebook", "1000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("1000.00"), carteira.getId(), usuario.getId());

        metaService.removerValor(meta.getId(), new BigDecimal("300.00"), carteira.getId(), usuario.getId());

        Meta reativada = metaRepository.findById(meta.getId()).orElseThrow();
        assertEquals(StatusMeta.ATIVA, reativada.getStatus());
        assertTrue(reativada.getAtiva());
        assertNull(reativada.getDataConclusao());
    }

    @Test
    void concluirDuasVezesNaoDuplicaEfeitos() {
        Meta meta = novaMeta("Reserva", "1000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("1000.00"), carteira.getId(), usuario.getId());
        var dataConclusao = metaRepository.findById(meta.getId()).orElseThrow().getDataConclusao();

        // novo aporte acima do objetivo mantém CONCLUIDA e não reseta a data de conclusão
        metaService.adicionarValor(meta.getId(), new BigDecimal("50.00"), carteira.getId(), usuario.getId());

        Meta aposSegundoAporte = metaRepository.findById(meta.getId()).orElseThrow();
        assertEquals(StatusMeta.CONCLUIDA, aposSegundoAporte.getStatus());
        assertEquals(dataConclusao, aposSegundoAporte.getDataConclusao());
        assertEquals(0, new BigDecimal("1050.00").compareTo(aposSegundoAporte.getValorReservado()));
    }

    @Test
    void reduzirObjetivoConcluiMetaPelaPrimeiraVez() {
        Meta meta = novaMeta("Entrada", "2000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("1200.00"), carteira.getId(), usuario.getId());

        Meta atualizacao = novaMetaDetached("Entrada");
        atualizacao.setValorTotal(new BigDecimal("1000.00"));
        Meta concluida = metaService.atualizar(meta.getId(), atualizacao, usuario.getId());

        assertEquals(StatusMeta.CONCLUIDA, concluida.getStatus());
        assertFalse(concluida.getAtiva());
        assertNotNull(concluida.getDataConclusao());
    }

    @Test
    void aumentarObjetivoReabreMetaELimpaDataDeConclusao() {
        Meta meta = novaMeta("Carro", "1000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("1000.00"), carteira.getId(), usuario.getId());

        Meta atualizacao = novaMetaDetached("Carro");
        atualizacao.setValorTotal(new BigDecimal("1500.00"));
        Meta reaberta = metaService.atualizar(meta.getId(), atualizacao, usuario.getId());

        assertEquals(StatusMeta.ATIVA, reaberta.getStatus());
        assertTrue(reaberta.getAtiva());
        assertNull(reaberta.getDataConclusao());
    }

    @Test
    void editarMetaAindaConcluidaPreservaDataDeConclusao() {
        Meta meta = novaMeta("Reserva", "1000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("1200.00"), carteira.getId(), usuario.getId());
        var dataConclusao = metaRepository.findById(meta.getId()).orElseThrow().getDataConclusao();

        Meta atualizacao = novaMetaDetached("Reserva ajustada");
        atualizacao.setValorTotal(new BigDecimal("1100.00"));
        Meta concluida = metaService.atualizar(meta.getId(), atualizacao, usuario.getId());

        assertEquals(StatusMeta.CONCLUIDA, concluida.getStatus());
        assertEquals(dataConclusao, concluida.getDataConclusao());
    }

    @Test
    void exclusaoComReservaEBloqueadaSemAlterarEstado() {
        Meta meta = novaMeta("Emergência", "1000.00");
        metaService.adicionarValor(meta.getId(), new BigDecimal("400.00"), carteira.getId(), usuario.getId());
        long movimentosAntes = movimentoMetaRepository.count();
        BigDecimal saldoAntes = carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo();

        BusinessException erro = assertThrows(BusinessException.class,
                () -> metaService.deletar(meta.getId(), usuario.getId()));
        assertTrue(erro.getMessage().contains("Resgate o valor"));

        Meta intacta = metaRepository.findById(meta.getId()).orElseThrow();
        assertEquals(StatusMeta.ATIVA, intacta.getStatus());
        assertEquals(0, new BigDecimal("400.00").compareTo(intacta.getValorReservado()));
        assertEquals(movimentosAntes, movimentoMetaRepository.count());
        assertEquals(0, saldoAntes.compareTo(carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void exclusaoSemReservaArquivaEArquivadaNaoAceitaEdicaoNemMovimentacao() {
        Meta meta = novaMeta("Curso", "1000.00");

        metaService.deletar(meta.getId(), usuario.getId());
        Meta arquivada = metaRepository.findById(meta.getId()).orElseThrow();
        assertEquals(StatusMeta.ARQUIVADA, arquivada.getStatus());
        assertFalse(arquivada.getAtiva());

        // repetir arquivamento não duplica efeitos
        metaService.deletar(meta.getId(), usuario.getId());
        assertEquals(StatusMeta.ARQUIVADA, metaRepository.findById(meta.getId()).orElseThrow().getStatus());

        assertThrows(BusinessException.class,
                () -> metaService.adicionarValor(meta.getId(), new BigDecimal("10.00"), carteira.getId(), usuario.getId()));
        assertThrows(BusinessException.class,
                () -> metaService.atualizar(meta.getId(), novaMetaDetached("Curso 2"), usuario.getId()));

        assertEquals(1, metaService.listarPorUsuario(usuario.getId(), StatusMeta.ARQUIVADA, Pageable.unpaged()).getTotalElements());
    }

    @Test
    void exportacaoDistingueStatusConcluidaDeArquivadaEMantemAtivaLegada() {
        Meta concluida = novaMeta("Concluída CSV", "100.00");
        metaService.adicionarValor(concluida.getId(), new BigDecimal("100.00"), carteira.getId(), usuario.getId());
        Meta arquivada = novaMeta("Arquivada CSV", "100.00");
        metaService.deletar(arquivada.getId(), usuario.getId());

        String csv = exportService.exportarCompletoCsv(usuario.getId());
        assertTrue(csv.contains("Data Conclusão,Status,Ativa"));
        assertTrue(csv.lines().anyMatch(l -> l.contains("Concluída CSV") && l.contains(",CONCLUIDA,Não")));
        assertTrue(csv.lines().anyMatch(l -> l.contains("Arquivada CSV") && l.contains(",ARQUIVADA,Não")));
    }

    private Meta novaMeta(String nome, String valorTotal) {
        Meta meta = new Meta();
        meta.setNome(nome);
        meta.setValorTotal(new BigDecimal(valorTotal));
        return metaService.criar(meta, usuario.getId());
    }

    private Meta novaMetaDetached(String nome) {
        Meta meta = new Meta();
        meta.setNome(nome);
        meta.setValorTotal(new BigDecimal("1.00"));
        return meta;
    }
}

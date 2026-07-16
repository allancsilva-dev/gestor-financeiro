package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusOperacaoFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.TransferenciaService;
import com.gestor.financeiro.service.TransferirCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-04 — Transferencia interna atomica (ADR-0009): par de lancamentos
 * vinculados a uma operacao, idempotencia, origem != destino e exclusao de
 * receitas/despesas (nao cria Transacao).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferenciaServiceTest {

    @Autowired TransferenciaService transferenciaService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired MovimentoCarteiraRepository movimentoRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira corrente;
    private Carteira poupanca;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Transfer F2");
        usuario.setEmail("transfer-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        corrente = novaConta("Corrente", SubtipoContaFinanceira.CORRENTE, new BigDecimal("1000.00"));
        poupanca = novaConta("Poupanca", SubtipoContaFinanceira.POUPANCA, new BigDecimal("200.00"));
    }

    private Carteira novaConta(String nome, SubtipoContaFinanceira subtipo, BigDecimal saldo) {
        Carteira c = new Carteira();
        c.setNome(nome);
        c.setSubtipo(subtipo);
        c.setSaldo(saldo);
        c.setUsuario(usuario);
        return carteiraRepository.save(c);
    }

    private TransferirCommand comando(BigDecimal valor, String chave) {
        return new TransferirCommand(usuario.getId(), corrente.getId(), poupanca.getId(),
                valor, "Guardar dinheiro", chave, null);
    }

    @Test
    void transferenciaMoveSaldoComParDeLancamentosVinculados() {
        TransferenciaService.Resultado resultado =
                transferenciaService.transferir(comando(new BigDecimal("300.00"), "tr-1"));

        assertEquals(0, new BigDecimal("700.00").compareTo(
                carteiraRepository.findById(corrente.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("500.00").compareTo(
                carteiraRepository.findById(poupanca.getId()).orElseThrow().getSaldo()));

        assertNotNull(resultado.operacao().getId());
        assertEquals(StatusOperacaoFinanceira.CONFIRMADA, resultado.operacao().getStatus());
        assertEquals(TipoMovimentoCarteira.TRANSFERENCIA_SAIDA, resultado.saida().getTipo());
        assertEquals(TipoMovimentoCarteira.TRANSFERENCIA_ENTRADA, resultado.entrada().getTipo());
        assertEquals(resultado.operacao().getId(), resultado.saida().getOperacao().getId());
        assertEquals(resultado.operacao().getId(), resultado.entrada().getOperacao().getId());
        assertEquals(2, movimentoRepository.findByOperacaoIdOrderByValorAssinadoAsc(
                resultado.operacao().getId()).size());

        // transferencia nunca e receita/despesa: nenhuma Transacao criada
        assertEquals(0, transacaoRepository.count());
    }

    @Test
    void origemIgualDestinoEBloqueada() {
        assertThrows(BusinessException.class, () -> transferenciaService.transferir(
                new TransferirCommand(usuario.getId(), corrente.getId(), corrente.getId(),
                        BigDecimal.TEN, null, null, null)));
    }

    @Test
    void saldoInsuficienteBloqueiaSemEfeitoColateral() {
        assertThrows(BusinessException.class,
                () -> transferenciaService.transferir(comando(new BigDecimal("5000.00"), null)));
    }

    @Test
    void retryComMesmaChaveEPayloadRetornaMesmaOperacaoSemMoverDuasVezes() {
        TransferenciaService.Resultado primeira =
                transferenciaService.transferir(comando(new BigDecimal("100.00"), "tr-idem"));
        TransferenciaService.Resultado retry =
                transferenciaService.transferir(comando(new BigDecimal("100.00"), "tr-idem"));

        assertEquals(primeira.operacao().getId(), retry.operacao().getId());
        assertEquals(0, new BigDecimal("900.00").compareTo(
                carteiraRepository.findById(corrente.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("300.00").compareTo(
                carteiraRepository.findById(poupanca.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void mesmaChaveComPayloadDiferenteConflita() {
        transferenciaService.transferir(comando(new BigDecimal("100.00"), "tr-conf"));

        assertThrows(FinancialConflictException.class,
                () -> transferenciaService.transferir(comando(new BigDecimal("999.00"), "tr-conf")));
    }

    @Test
    void contaDeOutroUsuarioOuInexistenteNaoEEncontrada() {
        assertThrows(ResourceNotFoundException.class, () -> transferenciaService.transferir(
                new TransferirCommand(usuario.getId(), corrente.getId(), 999999L,
                        BigDecimal.TEN, null, null, null)));
    }

    @Test
    void custodiaNaoAceitaTransferenciaDireta() {
        Carteira custodia = new Carteira();
        custodia.setNome("Corretora");
        custodia.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        custodia.setSubtipo(SubtipoContaFinanceira.CUSTODIA);
        custodia.setSaldo(BigDecimal.ZERO);
        custodia.setUsuario(usuario);
        custodia = carteiraRepository.save(custodia);
        Long custodiaId = custodia.getId();

        assertThrows(BusinessException.class, () -> transferenciaService.transferir(
                new TransferirCommand(usuario.getId(), corrente.getId(), custodiaId,
                        BigDecimal.TEN, null, null, null)));
    }
}

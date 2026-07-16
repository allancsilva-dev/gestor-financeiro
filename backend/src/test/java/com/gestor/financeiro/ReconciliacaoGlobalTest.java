package com.gestor.financeiro;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.model.enums.EstadoConciliacaoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReconciliacaoGlobalTest {
    @Autowired ReconciliacaoGlobalService service;
    @Autowired TransacaoService transacaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired ContaRepository contaRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired MetaRepository metaRepository;
    @Autowired FaturaCartaoRepository faturaRepository;
    @Autowired MovimentoCarteiraRepository movimentoRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void ledgerConciliadoEDivergenteSemTolerancia() {
        Usuario user = user("ledger");
        Carteira conta = carteiraRepository.save(TestDataFactory.carteira(user, "Conta", BigDecimal.ZERO));
        assertEquals(ReconciliacaoGlobalResponse.Status.OK, service.reconciliarUsuario(user.getId()).status());

        conta.setSaldo(new BigDecimal("0.01"));
        carteiraRepository.saveAndFlush(conta);
        ReconciliacaoGlobalResponse report = service.reconciliarUsuario(user.getId());
        assertEquals(ReconciliacaoGlobalResponse.Status.DIVERGENTE, report.status());
        assertTrue(report.detalhes().stream().anyMatch(d ->
                d.invariante() == ReconciliacaoGlobalResponse.Invariante.SALDO_LEDGER));
    }

    @Test
    void compraEmCartaoInativoComLancamentoELedgerConcilia() {
        Usuario user = user("cartao");
        Categoria categoria = categoriaRepository.save(TestDataFactory.categoria(user, "Compras"));
        Carteira passivo = carteiraRepository.save(TestDataFactory.contaPassivaCartao(user, "Passivo"));
        Conta cartao = TestDataFactory.cartao(user, "Cartão", passivo);
        cartao.setAtivo(false);
        cartao = contaRepository.save(cartao);
        Transacao compra = TestDataFactory.transacao(user, categoria, "Compra", new BigDecimal("37.25"));
        compra.setConta(cartao);
        compra.setData(LocalDate.of(2026, 7, 1));
        transacaoService.criar(compra, user.getId());

        ReconciliacaoGlobalResponse report = service.reconciliarUsuario(user.getId());
        assertEquals(ReconciliacaoGlobalResponse.Status.OK, report.status());
        assertTrue(report.resumo().stream().filter(r ->
                r.invariante() == ReconciliacaoGlobalResponse.Invariante.PASSIVO_FATURAS)
                .allMatch(r -> r.verificacoes() == 1));
    }

    @Test
    void incompletaConciliadaDivergeMasPendenteEhAceitaEUsuarioEhIsolado() {
        Usuario alice = user("alice");
        Usuario bob = user("bob");
        Transacao incompleta = transacao(alice, EstadoConciliacaoTransacao.CONCILIADA);
        transacaoRepository.save(incompleta);
        transacaoRepository.save(transacao(alice, EstadoConciliacaoTransacao.PENDENTE_CONCILIACAO));

        ReconciliacaoGlobalResponse aliceReport = service.reconciliarUsuario(alice.getId());
        assertEquals(1, aliceReport.detalhes().stream().filter(d ->
                d.invariante() == ReconciliacaoGlobalResponse.Invariante.TRANSACAO_INCOMPLETA).count());
        assertEquals(ReconciliacaoGlobalResponse.Status.OK, service.reconciliarUsuario(bob.getId()).status());
        assertFalse(service.reconciliarUsuario(bob.getId()).detalhes().stream()
                .anyMatch(d -> d.recursoId().equals(incompleta.getId())));
    }

    @Test
    void metaZeradaSemCofreEhValidaEReservasInvalidasSaoDetalhadas() {
        Usuario alice = user("meta-alice");
        Usuario bob = user("meta-bob");
        metaRepository.save(meta(alice, "Zerada", BigDecimal.ZERO, null));
        metaRepository.save(meta(alice, "Sem cofre", BigDecimal.TEN, null));
        Carteira ownershipIncorreto = carteiraRepository.save(
                TestDataFactory.carteira(bob, "Conta alheia", BigDecimal.TEN));
        metaRepository.save(meta(alice, "Ownership", BigDecimal.TEN, ownershipIncorreto));
        Carteira saldoIncorreto = TestDataFactory.carteira(alice, "Cofre divergente", new BigDecimal("9.99"));
        saldoIncorreto.setSubtipo(com.gestor.financeiro.model.enums.SubtipoContaFinanceira.COFRE);
        saldoIncorreto = carteiraRepository.save(saldoIncorreto);
        metaRepository.save(meta(alice, "Saldo", BigDecimal.TEN, saldoIncorreto));

        ReconciliacaoGlobalResponse report = service.reconciliarUsuario(alice.getId());
        assertEquals(3, report.detalhes().stream().filter(d ->
                d.invariante() == ReconciliacaoGlobalResponse.Invariante.COFRE_META).count());
        assertEquals(3, report.resumo().stream().filter(r ->
                r.invariante() == ReconciliacaoGlobalResponse.Invariante.COFRE_META)
                .findFirst().orElseThrow().verificacoes());
    }

    @Test
    void passivoAssinadoAceitaPagamentoParcialEstornoECredito() {
        Usuario user = user("passivo-assinado");
        Carteira parcial = carteiraRepository.save(
                TestDataFactory.contaPassivaCartao(user, "Parcial"));
        parcial.setSaldo(new BigDecimal("70.00"));
        carteiraRepository.save(parcial);
        Conta cardParcial = contaRepository.save(TestDataFactory.cartao(user, "Parcial", parcial));
        faturaRepository.save(fatura(user, cardParcial, 7,
                new BigDecimal("100.00"), new BigDecimal("30.00")));
        movimentoRepository.save(movimento(user, parcial, new BigDecimal("70.00")));

        Carteira credito = carteiraRepository.save(
                TestDataFactory.contaPassivaCartao(user, "Crédito"));
        credito.setSaldo(new BigDecimal("-15.00"));
        carteiraRepository.save(credito);
        Conta cardCredito = contaRepository.save(TestDataFactory.cartao(user, "Crédito", credito));
        faturaRepository.save(fatura(user, cardCredito, 8,
                new BigDecimal("-15.00"), BigDecimal.ZERO));
        movimentoRepository.save(movimento(user, credito, new BigDecimal("-15.00")));

        assertEquals(ReconciliacaoGlobalResponse.Status.OK, service.reconciliarUsuario(user.getId()).status());
    }

    private Transacao transacao(Usuario user, EstadoConciliacaoTransacao estado) {
        Transacao value = new Transacao();
        value.setUsuario(user);
        value.setDescricao("Registro incompleto");
        value.setValorTotal(BigDecimal.ONE);
        value.setTipo(com.gestor.financeiro.model.enums.TipoTransacao.ENTRADA);
        value.setData(LocalDate.of(2026, 7, 1));
        value.setEstadoConciliacao(estado);
        return value;
    }

    private Usuario user(String suffix) {
        return usuarioRepository.save(TestDataFactory.usuario(suffix,
                suffix + "-reconciliation@teste.com", passwordEncoder.encode("123456")));
    }

    private Meta meta(Usuario user, String name, BigDecimal reserved, Carteira cofre) {
        Meta meta = new Meta();
        meta.setUsuario(user);
        meta.setNome(name);
        meta.setValorTotal(new BigDecimal("100.00"));
        meta.setValorReservado(reserved);
        meta.setModalidade(ModalidadeMeta.COFRE_REAL);
        meta.setCofre(cofre);
        return meta;
    }

    private FaturaCartao fatura(Usuario user, Conta card, int month,
                                BigDecimal total, BigDecimal paid) {
        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(user);
        fatura.setConta(card);
        fatura.setMes(month);
        fatura.setAno(2026);
        fatura.setValorTotal(total);
        fatura.setValorPago(paid);
        fatura.setStatus(FaturaStatus.FECHADA);
        return fatura;
    }

    private MovimentoCarteira movimento(Usuario user, Carteira account, BigDecimal signed) {
        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(user);
        movimento.setCarteira(account);
        movimento.setTipo(signed.signum() >= 0 ? TipoMovimentoCarteira.ENTRADA : TipoMovimentoCarteira.SAIDA);
        movimento.setValor(signed.abs());
        movimento.setValorAssinado(signed);
        movimento.setOrigem(OrigemMovimentoCarteira.FATURA_CARTAO);
        movimento.setSaldoResultante(signed);
        return movimento;
    }
}

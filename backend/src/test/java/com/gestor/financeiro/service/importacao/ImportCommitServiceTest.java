package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.ImportBalanceReconciliation;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.service.LedgerService;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;
import com.gestor.financeiro.service.RegistrarMovimentoCommand;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.OperacaoFinanceiraRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lançamento do lote no ledger. O que estes testes protegem, em ordem de gravidade:
 * reexecutar o commit não pode duplicar saldo, linha ruim não pode derrubar o lote, e lote já
 * lançado não pode voltar a ser editado.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImportCommitServiceTest {

    @Autowired ImportCommitService commitService;
    @Autowired ImportBatchService batches;
    @Autowired ImportBatchRepository batchRepository;
    @Autowired ImportRecordRepository records;
    @Autowired CarteiraRepository carteiras;
    @Autowired ContaRepository cartoes;
    @Autowired FaturaLancamentoRepository faturaLancamentos;
    @Autowired FaturaCartaoRepository faturas;
    @Autowired CategoriaRepository categorias;
    @Autowired TransacaoRepository transacoes;
    @Autowired MovimentoCarteiraRepository movimentos;
    @Autowired OperacaoFinanceiraRepository operacoes;
    @Autowired UsuarioRepository usuarios;
    @Autowired ImportReversalService reversalService;
    @Autowired ReconciliacaoGlobalService reconciliacao;
    @Autowired LedgerService ledgerService;

    private Usuario usuario;
    private Carteira conta;
    private Categoria categoria;

    @BeforeEach
    void setup() {
        limpar();
        usuario = usuarios.save(TestDataFactory.usuario("Commit", "commit-" + System.nanoTime() + "@test.local", "h"));
        // Saldo entra pelo ledger, não por atribuição direta: saldo materializado sem lançamento
        // já nasceria divergente na reconciliação, e é justamente isso que os testes conferem.
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta corrente", BigDecimal.ZERO));
        ledgerService.registrarMovimento(new RegistrarMovimentoCommand(
                usuario.getId(), conta.getId(), TipoMovimentoCarteira.ENTRADA, new BigDecimal("1000.00"),
                RegistrarMovimentoCommand.Direcao.ENTRADA, OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "SALDO_INICIAL", conta.getId(), "Saldo inicial do teste", "teste:saldo:" + conta.getId(),
                java.time.LocalDateTime.now(), false));
        categoria = categorias.save(TestDataFactory.categoria(usuario, "Mercado"));
    }

    @AfterEach
    void limparDepois() {
        limpar();
        if (usuario == null) return;
        Long dono = usuario.getId();
        categorias.deleteAll(categorias.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(dono)).toList());
        carteiras.deleteAll(carteiras.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(dono)).toList());
        usuarios.deleteById(dono);
    }

    private void limpar() {
        // Ordem das FKs: lançamento de fatura aponta para transação, fatura aponta para o cartão,
        // e o cartão aponta para a conta passiva.
        records.deleteAll();
        batchRepository.deleteAll();
        faturaLancamentos.deleteAll();
        faturas.deleteAll();
        movimentos.deleteAll();
        transacoes.deleteAll();
        operacoes.deleteAll();
        cartoes.deleteAll();
    }

    private ImportBatch loteComRegistros(ImportRecordStatus... status) {
        ImportBatch batch = new ImportBatch();
        batch.setUsuario(usuario);
        batch.setFormat(ImportFormat.CSV);
        batch.setFileSha256("a".repeat(64));
        batch.setStatus(ImportBatchStatus.PARSED);
        batch.setTotalRecords(status.length);
        ImportBatch salvo = batchRepository.save(batch);

        int linha = 2;
        for (ImportRecordStatus estado : status) {
            ImportRecord record = new ImportRecord();
            record.setBatch(salvo);
            record.setSourceLine(linha++);
            record.setRecordFingerprint(String.format("%064x", linha));
            record.setStatus(estado);
            record.setOccurredOn(LocalDate.of(2026, 8, 20));
            record.setNormalizedDescription("Compra " + linha);
            record.setAmount(new BigDecimal("10.00"));
            record.setCurrency("BRL");
            record.setDirection(TipoTransacao.SAIDA);
            records.save(record);
        }
        return salvo;
    }

    private BigDecimal saldo() {
        return carteiras.findById(conta.getId()).orElseThrow().getSaldo();
    }

    private void lancar(ImportBatch batch) {
        commitService.preparar(usuario.getId(), batch.getId(), conta.getId());
        batches.transition(usuario.getId(), batch.getId(), ImportBatchStatus.COMMITTING, null);
        commitService.executar(usuario.getId(), batch.getId());
    }

    @Test
    void lancaRegistrosValidosEVinculaTransacao() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);

        lancar(batch);

        assertEquals(ImportBatchStatus.COMMITTED,
                batchRepository.findById(batch.getId()).orElseThrow().getStatus());
        assertEquals(2, transacoes.count());
        assertEquals(new BigDecimal("980.00"), saldo());
        records.findAll().forEach(record -> {
            assertEquals(ImportRecordStatus.COMMITTED, record.getStatus());
            assertNotNull(record.getTransacao(), "registro lançado precisa apontar para a transação");
        });
    }

    @Test
    void reexecutarOCommitNaoDuplicaSaldo() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);
        lancar(batch);

        // Retentativa do job (lease vencido, processo morto no meio) chega exatamente assim.
        commitService.executar(usuario.getId(), batch.getId());

        assertEquals(2, transacoes.count());
        assertEquals(new BigDecimal("980.00"), saldo());
    }

    @Test
    void naoLancaRegistroEmRevisaoNemDuplicado() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.PENDING_REVIEW, ImportRecordStatus.DUPLICATE,
                ImportRecordStatus.INVALID);

        lancar(batch);

        assertEquals(0, transacoes.count());
        assertEquals(new BigDecimal("1000.00"), saldo());
    }

    @Test
    void aprovarTrazRegistroEmRevisaoComCategoria() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.PENDING_REVIEW);
        ImportRecord registro = records.findAll().get(0);

        commitService.aprovar(usuario.getId(), batch.getId(), registro.getId(), categoria.getId());
        lancar(batch);

        assertEquals(1, transacoes.count());
        assertEquals(categoria.getId(), transacoes.findAll().get(0).getCategoria().getId());
    }

    @Test
    void divergenciaDeSaldoExigeReconhecimentoExplicitoAntesDePreparar() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);
        batch.setDeclaredOpeningBalance(new BigDecimal("100.00"));
        batch.setDeclaredMovementTotal(new BigDecimal("-20.00"));
        batch.setDeclaredClosingBalance(new BigDecimal("70.00"));
        batch.setBalanceReconciliation(ImportBalanceReconciliation.MISMATCH);
        batchRepository.saveAndFlush(batch);

        assertThrows(BusinessException.class,
                () -> commitService.preparar(usuario.getId(), batch.getId(), conta.getId(), null, false));
        ImportBatch acknowledged = commitService.preparar(
                usuario.getId(), batch.getId(), conta.getId(), null, true);

        assertTrue(acknowledged.isBalanceMismatchAcknowledged());
        assertEquals(ImportBalanceReconciliation.MISMATCH, acknowledged.getBalanceReconciliation());
    }

    @Test
    void moedaNaoSuportadaViraFalhaDeRegistroSemDerrubarOLote() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);
        ImportRecord estrangeiro = records.findAll().get(0);
        estrangeiro.setCurrency("USD");
        records.save(estrangeiro);

        lancar(batch);

        assertEquals(1, transacoes.count(), "o registro em BRL continua sendo lançado");
        ImportRecord recusado = records.findById(estrangeiro.getId()).orElseThrow();
        assertEquals(ImportRecordStatus.INVALID, recusado.getStatus());
        assertEquals("CURRENCY_UNSUPPORTED", recusado.getReasonCode());
        assertEquals(ImportBatchStatus.COMMITTED,
                batchRepository.findById(batch.getId()).orElseThrow().getStatus());
    }

    @Test
    void reversaoEstornaSaldoCategoriaEMantemTrilha() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.PENDING_REVIEW, ImportRecordStatus.VALID);
        ImportRecord emRevisao = records.findAll().stream()
                .filter(r -> r.getStatus() == ImportRecordStatus.PENDING_REVIEW).findFirst().orElseThrow();
        commitService.aprovar(usuario.getId(), batch.getId(), emRevisao.getId(), categoria.getId());
        lancar(batch);
        assertEquals(new BigDecimal("980.00"), saldo());

        reversalService.executar(usuario.getId(), batch.getId());

        assertEquals(ImportBatchStatus.REVERSED,
                batchRepository.findById(batch.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("1000.00"), saldo());
        assertEquals(BigDecimal.ZERO.setScale(2),
                categorias.findById(categoria.getId()).orElseThrow().getValorGasto().setScale(2));
        records.findAll().forEach(record -> {
            assertEquals(ImportRecordStatus.REVERSED, record.getStatus());
            assertNotNull(record.getTransacao(), "reversão preserva o vínculo para auditoria");
        });
        assertEquals(2, transacoes.count(), "transação revertida continua no histórico, inativa");
        assertTrue(transacoes.findAll().stream().noneMatch(t -> Boolean.TRUE.equals(t.getAtiva())));
    }

    @Test
    void reexecutarAReversaoNaoEstornaDuasVezes() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);
        lancar(batch);
        reversalService.executar(usuario.getId(), batch.getId());

        // Reexecução do job de reversão: precisa ser inócua.
        reversalService.executar(usuario.getId(), batch.getId());

        assertEquals(new BigDecimal("1000.00"), saldo());
    }

    @Test
    void reconciliacaoNaoAcusaDivergenciaDepoisDeLancarEReverter() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);
        records.findAll().forEach(record -> {
            record.setCategoria(categoria);
            records.save(record);
        });

        lancar(batch);
        ReconciliacaoGlobalResponse aposCommit = reconciliacao.reconciliarUsuario(usuario.getId());
        reversalService.executar(usuario.getId(), batch.getId());
        ReconciliacaoGlobalResponse aposReversao = reconciliacao.reconciliarUsuario(usuario.getId());

        assertEquals(0, aposCommit.divergencias(),
                () -> "commit não pode deixar verdade materializada torta: " + aposCommit.detalhes());
        assertEquals(0, aposReversao.divergencias(),
                () -> "reversão não pode deixar verdade materializada torta: " + aposReversao.detalhes());
    }

    @Test
    void loteJaLancadoNaoAceitaMaisRevisao() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);
        lancar(batch);
        ImportRecord registro = records.findAll().get(0);

        BusinessException erro = assertThrows(BusinessException.class,
                () -> commitService.aprovar(usuario.getId(), batch.getId(), registro.getId(), categoria.getId()));
        assertTrue(erro.getMessage().contains("já lançado"));
    }

    @Test
    void faturaDeCartaoEntraPelaFaturaENaoPeloCaixa() {
        Carteira passiva = carteiras.save(TestDataFactory.contaPassivaCartao(usuario, "Cartão"));
        Conta cartao = cartoes.save(TestDataFactory.cartao(usuario, "Cartão Nubank", passiva));
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID, ImportRecordStatus.VALID);

        commitService.preparar(usuario.getId(), batch.getId(), null, cartao.getId());
        batches.transition(usuario.getId(), batch.getId(), ImportBatchStatus.COMMITTING, null);
        commitService.executar(usuario.getId(), batch.getId());

        assertEquals(2, transacoes.count());
        assertEquals(2, faturaLancamentos.count(), "cada linha vira lançamento de fatura");
        assertEquals(new BigDecimal("1000.00"), saldo(),
                "compra no cartão não sai do caixa: o dinheiro só sai no pagamento da fatura");
    }

    @Test
    void entradaEmLoteDeFaturaNaoEhLancada() {
        Carteira passiva = carteiras.save(TestDataFactory.contaPassivaCartao(usuario, "Cartão 2"));
        Conta cartao = cartoes.save(TestDataFactory.cartao(usuario, "Cartão Itaú", passiva));
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);
        ImportRecord entrada = records.findAll().get(0);
        entrada.setDirection(TipoTransacao.ENTRADA);
        records.save(entrada);

        commitService.preparar(usuario.getId(), batch.getId(), null, cartao.getId());
        batches.transition(usuario.getId(), batch.getId(), ImportBatchStatus.COMMITTING, null);
        commitService.executar(usuario.getId(), batch.getId());

        assertEquals(0, transacoes.count(), "estorno nasce do cancelamento da compra, não de uma linha solta");
        assertEquals(ImportRecordStatus.INVALID,
                records.findById(entrada.getId()).orElseThrow().getStatus());
    }

    @Test
    void destinoPrecisaSerUmSo() {
        Carteira passiva = carteiras.save(TestDataFactory.contaPassivaCartao(usuario, "Cartão 3"));
        Conta cartao = cartoes.save(TestDataFactory.cartao(usuario, "Cartão Ambíguo", passiva));
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);

        assertThrows(BusinessException.class,
                () -> commitService.preparar(usuario.getId(), batch.getId(), conta.getId(), cartao.getId()));
        assertThrows(BusinessException.class,
                () -> commitService.preparar(usuario.getId(), batch.getId(), null, null));
    }

    @Test
    void contaDeCartaoNaoRecebeImportacaoDeExtrato() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);
        Carteira cartao = carteiras.save(TestDataFactory.contaPassivaCartao(usuario, "Cartão"));

        assertThrows(BusinessException.class,
                () -> commitService.preparar(usuario.getId(), batch.getId(), cartao.getId()));
    }

    @Test
    void commitSemContaDeDestinoEhRecusado() {
        ImportBatch batch = loteComRegistros(ImportRecordStatus.VALID);

        assertThrows(BusinessException.class,
                () -> commitService.solicitarCommit(usuario.getId(), batch.getId()));
    }
}

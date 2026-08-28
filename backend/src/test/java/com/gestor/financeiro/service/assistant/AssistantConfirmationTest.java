package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.dto.AssistantDtos.ConfirmDraftRequest;
import com.gestor.financeiro.dto.AssistantDtos.MessageRequest;
import com.gestor.financeiro.dto.AssistantDtos.PatchDraftRequest;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;
import com.gestor.financeiro.exception.AssistantException;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;
import com.gestor.financeiro.service.LedgerService;
import com.gestor.financeiro.service.RegistrarMovimentoCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantConfirmationTest {
    @Autowired AssistantService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired CarteiraRepository carteiras;
    @Autowired CategoriaRepository categorias;
    @Autowired TransacaoRepository transacoes;
    @Autowired OperacaoFinanceiraRepository operacoes;
    @Autowired AssistantConfirmationRepository confirmacoes;
    @Autowired AssistantMessageRepository assistantMessages;
    @Autowired ReconciliacaoGlobalService reconciliacao;
    @Autowired LedgerService ledger;

    @Test
    void umaPerguntaCompletaMesmoRascunhoESegundaAmbiguidadeAbreFormulario() {
        Usuario usuario = new Usuario(); usuario.setNome("Gabi"); usuario.setEmail("assistant-clarification@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO); carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO); categorias.save(categoria);

        var question = service.receive(usuario.getId(), new MessageRequest(null, "gasolina no Nubank hoje"));
        assertThat(question.outcome()).isEqualTo(ParseOutcome.NEEDS_ONE_FIELD);
        assertThat(question.reply()).isEqualTo("Qual foi o valor?");
        var completed = service.receive(usuario.getId(), new MessageRequest(question.conversationId(), "85"));

        assertThat(completed.outcome()).isEqualTo(ParseOutcome.COMPLETE);
        assertThat(completed.draft().id()).isEqualTo(question.draft().id());
        assertThat(completed.draft().valor()).isEqualByComparingTo("85.00");

        var anotherQuestion = service.receive(usuario.getId(), new MessageRequest(null, "gasolina no Nubank hoje"));
        var form = service.receive(usuario.getId(), new MessageRequest(anotherQuestion.conversationId(), "não sei"));
        assertThat(form.outcome()).isEqualTo(ParseOutcome.NEEDS_FORM);
        assertThat(form.draft().id()).isEqualTo(anotherQuestion.draft().id());
    }

    @Test
    void chaveDeConfirmacaoNaoPodeSerReutilizadaEmOutroRascunho() {
        Usuario usuario = new Usuario(); usuario.setNome("Fabi"); usuario.setEmail("assistant-confirm-key@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO); carteira = carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO); categorias.save(categoria);
        registrarSaldoInicial(usuario.getId(), carteira.getId());
        var first = service.receive(usuario.getId(), new MessageRequest(null, "gasolina 85 no Nubank hoje"));
        var second = service.receive(usuario.getId(), new MessageRequest(null, "gasolina 40 no Nubank hoje"));

        var confirmed = service.confirm(usuario.getId(), first.draft().id(),
                new ConfirmDraftRequest(first.draft().version()), "assistant:confirm:key-test");
        var replay = service.confirm(usuario.getId(), first.draft().id(),
                new ConfirmDraftRequest(first.draft().version()), "assistant:confirm:key-test");
        assertThat(replay).isEqualTo(confirmed);

        Long usuarioId = usuario.getId();
        assertThatThrownBy(() -> service.confirm(usuarioId, second.draft().id(),
                new ConfirmDraftRequest(second.draft().version()), "assistant:confirm:key-test"))
                .isInstanceOfSatisfying(AssistantException.class, error -> {
                    assertThat(error.code()).isEqualTo("DRAFT_CONFLICT");
                    assertThat(error.status().value()).isEqualTo(409);
                });
        assertThat(transacoes.findByUsuarioId(usuarioId)).hasSize(1);
    }

    @Test
    void replayDoCancelamentoEhNoOpEChaveNaoPodeMudarDeRascunho() {
        Usuario usuario = new Usuario(); usuario.setNome("Eva"); usuario.setEmail("assistant-cancel-replay@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        var first = service.receive(usuario.getId(), new MessageRequest(null, "mercado 50 hoje"));
        var second = service.receive(usuario.getId(), new MessageRequest(null, "padaria 20 hoje"));

        service.cancel(usuario.getId(), first.draft().id(), "assistant:cancel:replay-test");
        service.cancel(usuario.getId(), first.draft().id(), "assistant:cancel:replay-test");

        Long usuarioId = usuario.getId();
        assertThatThrownBy(() -> service.cancel(usuarioId, second.draft().id(), "assistant:cancel:replay-test"))
                .isInstanceOfSatisfying(AssistantException.class, error -> {
                    assertThat(error.code()).isEqualTo("DRAFT_CONFLICT");
                    assertThat(error.status().value()).isEqualTo(409);
                });
    }

    @Test
    void replayDoPatchRetornaMesmaVersaoERecusaPayloadDiferente() {
        Usuario usuario = new Usuario(); usuario.setNome("Dora"); usuario.setEmail("assistant-patch-replay@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO); carteira = carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO); categoria = categorias.save(categoria);
        Long carteiraId = carteira.getId(); Long categoriaId = categoria.getId();
        var message = service.receive(usuario.getId(), new MessageRequest(null, "gasolina 85 no Nubank hoje"));
        var patch = new PatchDraftRequest(message.draft().version(), message.draft().tipo(), new BigDecimal("90.00"),
                "Gasolina", message.draft().data(), carteiraId, categoriaId);

        var first = service.patch(usuario.getId(), message.draft().id(), patch, "assistant:draft:replay-test");
        var replay = service.patch(usuario.getId(), message.draft().id(), patch, "assistant:draft:replay-test");

        assertThat(replay).isEqualTo(first);
        Long usuarioId = usuario.getId();
        assertThatThrownBy(() -> service.patch(usuarioId, message.draft().id(),
                new PatchDraftRequest(message.draft().version(), message.draft().tipo(), new BigDecimal("91.00"),
                        "Gasolina", message.draft().data(), carteiraId, categoriaId),
                "assistant:draft:replay-test"))
                .isInstanceOfSatisfying(AssistantException.class, error -> {
                    assertThat(error.code()).isEqualTo("DRAFT_CONFLICT");
                    assertThat(error.status().value()).isEqualTo(409);
                });
    }

    @Test
    void replayDaMensagemComMesmaChaveNaoDuplicaConversaRascunhoOuMensagens() {
        Usuario usuario = new Usuario(); usuario.setNome("Caio"); usuario.setEmail("assistant-message-replay@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO); carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO); categorias.save(categoria);

        var request = new MessageRequest(null, "gasolina 85 no Nubank hoje");
        var first = service.receive(usuario.getId(), request, "assistant:message:replay-test");
        var replay = service.receive(usuario.getId(), request, "assistant:message:replay-test");

        assertThat(replay).isEqualTo(first);
        assertThat(assistantMessages.findByConversationIdAndUsuarioIdOrderByCreatedAt(
                first.conversationId(), usuario.getId())).hasSize(2);
        Long usuarioId = usuario.getId();
        assertThatThrownBy(() -> service.receive(usuarioId,
                new MessageRequest(null, "mercado 10 hoje"), "assistant:message:replay-test"))
                .isInstanceOfSatisfying(AssistantException.class, error -> {
                    assertThat(error.code()).isEqualTo("DRAFT_CONFLICT");
                    assertThat(error.status().value()).isEqualTo(409);
                });
    }

    @Test
    void replayConfirmaExatamenteUmaOperacaoTransacaoEMovimento() {
        Usuario usuario = new Usuario(); usuario.setNome("Ana"); usuario.setEmail("assistant-confirm@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO);
        carteira = carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO);
        categoria = categorias.save(categoria);
        registrarSaldoInicial(usuario.getId(), carteira.getId());

        var message = service.receive(usuario.getId(), new MessageRequest(null, "gasolina 85 no Nubank hoje"));
        assertThat(reconciliacao.reconciliarUsuario(usuario.getId()).status())
                .isEqualTo(ReconciliacaoGlobalResponse.Status.OK);
        var first = service.confirm(usuario.getId(), message.draft().id(), new ConfirmDraftRequest(message.draft().version()));
        var replay = service.confirm(usuario.getId(), message.draft().id(), new ConfirmDraftRequest(message.draft().version()));

        assertThat(replay).isEqualTo(first);
        assertThat(confirmacoes.countByUsuarioId(usuario.getId())).isEqualTo(1);
        assertThat(transacoes.findByUsuarioId(usuario.getId())).hasSize(1);
        assertThat(reconciliacao.reconciliarUsuario(usuario.getId()).status())
                .isEqualTo(ReconciliacaoGlobalResponse.Status.OK);
        assertThat(operacoes.findByUsuarioIdAndIdempotencyKey(usuario.getId(), "ASSISTANT:" + message.draft().id())).isPresent();
        assertThat(carteiras.findById(carteira.getId()).orElseThrow().getSaldo()).isEqualByComparingTo("415.00");

        Long confirmedUserId = usuario.getId();
        assertThatThrownBy(() -> service.confirm(confirmedUserId, message.draft().id(),
                new ConfirmDraftRequest(message.draft().version() + 1)))
                .isInstanceOfSatisfying(AssistantException.class, error -> {
                    assertThat(error.code()).isEqualTo("DRAFT_CONFLICT");
                    assertThat(error.status().value()).isEqualTo(409);
                });
        assertThat(transacoes.findByUsuarioId(usuario.getId())).hasSize(1);
    }

    @Test
    void duasConfirmacoesConcorrentesGeramUmaUnicaTransacao() throws Exception {
        Usuario usuario = new Usuario(); usuario.setNome("Bia"); usuario.setEmail("assistant-race@test.local"); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira(); carteira.setUsuario(usuario); carteira.setNome("Nubank");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(BigDecimal.ZERO); carteiras.save(carteira);
        Categoria categoria = new Categoria(); categoria.setUsuario(usuario); categoria.setNome("Gasolina");
        categoria.setValorGasto(BigDecimal.ZERO); categorias.save(categoria);
        registrarSaldoInicial(usuario.getId(), carteira.getId());
        var message = service.receive(usuario.getId(), new MessageRequest(null, "gasolina 85 no Nubank hoje"));
        assertThat(reconciliacao.reconciliarUsuario(usuario.getId()).status())
                .isEqualTo(ReconciliacaoGlobalResponse.Status.OK);
        Long usuarioId = usuario.getId(); Long draftId = message.draft().id(); Long version = message.draft().version();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return service.confirm(usuarioId, draftId, new ConfirmDraftRequest(version)); });
            var second = pool.submit(() -> { start.await(); return service.confirm(usuarioId, draftId, new ConfirmDraftRequest(version)); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
        } finally { pool.shutdownNow(); }
        assertThat(confirmacoes.countByUsuarioId(usuarioId)).isEqualTo(1);
        assertThat(transacoes.findByUsuarioId(usuarioId)).hasSize(1);
        assertThat(carteiras.findById(carteira.getId()).orElseThrow().getSaldo()).isEqualByComparingTo("415.00");
        assertThat(reconciliacao.reconciliarUsuario(usuarioId).status())
                .isEqualTo(ReconciliacaoGlobalResponse.Status.OK);
    }

    private void registrarSaldoInicial(Long usuarioId, Long carteiraId) {
        ledger.registrarMovimento(new RegistrarMovimentoCommand(
                usuarioId, carteiraId, TipoMovimentoCarteira.ENTRADA, new BigDecimal("500.00"),
                RegistrarMovimentoCommand.Direcao.ENTRADA, OrigemMovimentoCarteira.CARTEIRA_AJUSTE,
                "SALDO_INICIAL", carteiraId, "Saldo inicial do teste", "assistant:test:saldo:" + carteiraId,
                java.time.LocalDateTime.now(), false));
    }
}

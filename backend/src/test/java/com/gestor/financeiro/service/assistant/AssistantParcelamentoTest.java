package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.dto.AssistantDtos.ConfirmDraftRequest;
import com.gestor.financeiro.dto.AssistantDtos.MessageRequest;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.TestDataFactory;
import com.gestor.financeiro.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parcelar é privilégio do cartão: a fatura guarda o cronograma, a carteira não é tocada. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantParcelamentoTest {
    @Autowired AssistantService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired CarteiraRepository carteiras;
    @Autowired CategoriaRepository categorias;
    @Autowired ContaRepository contas;
    @Autowired TransacaoRepository transacoes;
    @Autowired FaturaLancamentoRepository lancamentos;

    private Usuario fixture(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Gabi"); usuario.setEmail(email); usuario.setSenha("hash");
        usuario = usuarios.save(usuario);
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario); carteira.setNome("Conta Principal");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE); carteira.setSaldo(new BigDecimal("1000.00"));
        carteiras.save(carteira);
        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario); categoria.setNome("Mercado"); categoria.setValorGasto(BigDecimal.ZERO);
        categorias.save(categoria);
        // Contract V41: cartão só existe pareado com sua conta financeira PASSIVO.
        Carteira passivo = carteiras.save(TestDataFactory.contaPassivaCartao(usuario, "Cartao Nubank (passivo)"));
        contas.save(TestDataFactory.cartao(usuario, "Cartao Nubank", passivo));
        return usuario;
    }

    @Test
    void compraParceladaNoCartaoGeraLancamentosDeFaturaSemMoverCarteira() {
        Usuario usuario = fixture("assistant-parcelamento@test.local");

        var message = service.receive(usuario.getId(),
                new MessageRequest(null, "comprei 300,00 no mercado hoje no Cartao Nubank em 3x"));

        assertThat(message.outcome()).isEqualTo(ParseOutcome.COMPLETE);
        assertThat(message.draft().parcelas()).isEqualTo(3);
        assertThat(message.draft().cartaoId()).isNotNull();
        assertThat(message.draft().carteiraId()).isNull();

        var confirmation = service.confirm(usuario.getId(), message.draft().id(),
                new ConfirmDraftRequest(message.draft().version()));

        var transacao = transacoes.findById(confirmation.transactionId()).orElseThrow();
        assertThat(transacao.getParcelado()).isTrue();
        assertThat(transacao.getTotalParcelas()).isEqualTo(3);
        assertThat(transacao.getCarteira()).isNull();
        assertThat(transacao.getConta()).isNotNull();
        assertThat(lancamentos.findByTransacaoId(transacao.getId())).hasSize(3);
    }

    @Test
    void compraAVistaNaContaSegueSemParcelas() {
        Usuario usuario = fixture("assistant-avista@test.local");

        var message = service.receive(usuario.getId(),
                new MessageRequest(null, "comprei 80,00 no mercado hoje na Conta Principal"));
        var confirmation = service.confirm(usuario.getId(), message.draft().id(),
                new ConfirmDraftRequest(message.draft().version()));

        var transacao = transacoes.findById(confirmation.transactionId()).orElseThrow();
        assertThat(transacao.getParcelado()).isFalse();
        assertThat(transacao.getCarteira()).isNotNull();
        assertThat(transacao.getConta()).isNull();
        assertThat(lancamentos.findByTransacaoId(transacao.getId())).isEmpty();
    }

    @Test
    void parcelarSemCartaoNaoFechaORascunho() {
        Usuario usuario = fixture("assistant-parcela-sem-cartao@test.local");

        var message = service.receive(usuario.getId(),
                new MessageRequest(null, "comprei 300,00 no mercado hoje na Conta Principal em 3x"));

        assertThat(message.outcome()).isNotEqualTo(ParseOutcome.COMPLETE);
        // O "3x" fica registrado no rascunho e é justamente o que cobra o cartão.
        assertThat(message.draft().parcelas()).isEqualTo(3);
        assertThat(message.draft().cartaoId()).isNull();
        assertThat(message.draft().missingFields()).contains("cartaoNome");
        Long draftId = message.draft().id();
        Long usuarioId = usuario.getId();
        Long version = message.draft().version();
        assertThatThrownBy(() -> service.confirm(usuarioId, draftId, new ConfirmDraftRequest(version)))
                .isInstanceOf(BusinessException.class);
    }
}

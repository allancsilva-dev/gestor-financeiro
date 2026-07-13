package com.gestor.financeiro;

import com.gestor.financeiro.exception.CardParcelDeprecatedException;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.service.LedgerService;
import com.gestor.financeiro.service.ParcelaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelaServiceTest {
    private ParcelaRepository repository;
    private ParcelaService service;
    private Parcela parcela;
    private LedgerService ledger;
    private CarteiraRepository carteiraRepository;

    @BeforeEach
    void setup() {
        repository = mock(ParcelaRepository.class);
        ledger = mock(LedgerService.class);
        carteiraRepository = mock(CarteiraRepository.class);
        service = new ParcelaService(repository, ledger, carteiraRepository);
        Usuario usuario = new Usuario(); usuario.setId(7L);
        Transacao transacao = new Transacao(); transacao.setId(11L); transacao.setUsuario(usuario);
        transacao.setTipo(TipoTransacao.SAIDA);
        parcela = new Parcela(); parcela.setId(3L); parcela.setTransacao(transacao);
        parcela.setStatus(StatusPagamento.PENDENTE); parcela.setValor(new BigDecimal("10.00"));
        parcela.setNumeroParcela(1); parcela.setTotalParcelas(2);
        when(repository.findById(3L)).thenReturn(Optional.of(parcela));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void pagaEDespagaParcelaSemCarteira() {
        assertEquals(StatusPagamento.PAGO, service.marcarComoPaga(3L, 7L).getStatus());
        assertNotNull(parcela.getDataPagamento());
        assertEquals(StatusPagamento.PENDENTE, service.marcarComoPendente(3L, 7L).getStatus());
        assertNull(parcela.getDataPagamento());
        verify(repository, times(2)).save(parcela);
    }

    @Test
    void despagarPendenteEhNoOp() {
        assertSame(parcela, service.marcarComoPendente(3L, 7L));
        verify(repository, never()).save(any());
    }

    @Test
    void rejeitaMutacaoDeParcelaLegadaDeCartao() {
        Conta conta = new Conta(); conta.setTipo(TipoConta.CREDITO);
        parcela.getTransacao().setConta(conta);
        assertThrows(CardParcelDeprecatedException.class, () -> service.marcarComoPaga(3L, 7L));
    }

    @Test
    void pagamentoComCarteiraRegistraMovimento() {
        Carteira carteira = new Carteira(); carteira.setId(9L);
        parcela.getTransacao().setCarteira(carteira);
        when(carteiraRepository.findByIdAndUsuarioIdForUpdate(9L, 7L)).thenReturn(Optional.of(carteira));
        service.marcarComoPaga(3L, 7L);
        verify(ledger).registrarMovimento(any());
    }

    @Test
    void listaAtualizaAtrasadasEProtegeOwnership() {
        var pageable = PageRequest.of(0, 20);
        when(repository.findByTransacaoIdAndTransacaoUsuarioId(11L, 7L, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(parcela)));
        assertEquals(1, service.listarPorTransacao(11L, 7L, pageable).getTotalElements());
        service.atualizarParcelasAtrasadas();
        verify(repository).atualizarStatusParcelasAtrasadas(
                StatusPagamento.PENDENTE, StatusPagamento.ATRASADO, LocalDate.now());
        assertThrows(com.gestor.financeiro.exception.UnauthorizedAccessException.class,
                () -> service.buscarPorId(3L, 99L));
        assertThrows(com.gestor.financeiro.exception.ResourceNotFoundException.class,
                () -> service.buscarPorId(404L, 7L));
    }
}

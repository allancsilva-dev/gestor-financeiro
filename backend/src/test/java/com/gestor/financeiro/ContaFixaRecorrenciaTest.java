package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContaFixaRecorrenciaTest {
    @Autowired ContaFixaService service;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired ExecucaoRecorrenciaRepository execucaoRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Carteira carteira;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Recorrente", "recorrencia@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("1000.00")));
    }

    @Test
    void entradaManualCreditaCarteiraEUmaSegundaExecucaoNaoDuplica() {
        ContaFixa salario = salvar("Salário", "2500.00", TipoTransacao.ENTRADA, false, LocalDate.now());

        service.realizar(salario.getId(), salario.getValorPlanejado(), carteira.getId(), usuario.getId(), false);

        assertEquals(0, new BigDecimal("3500.00").compareTo(carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
        assertThrows(BusinessException.class, () -> service.realizar(
                salario.getId(), salario.getValorPlanejado(), carteira.getId(), usuario.getId(), false));
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
    }

    @Test
    void saidaAutomaticaDebitaSemPermitirSaldoNegativo() {
        ContaFixa aluguel = salvar("Aluguel", "400.00", TipoTransacao.SAIDA, true, LocalDate.now());
        service.realizarAutomatica(aluguel.getId());
        assertEquals(0, new BigDecimal("600.00").compareTo(carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));

        ContaFixa cara = salvar("Conta cara", "900.00", TipoTransacao.SAIDA, true, LocalDate.now());
        service.realizarAutomatica(cara.getId());

        assertEquals(0, new BigDecimal("600.00").compareTo(carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        var falha = execucaoRepository.findByContaFixaIdAndDataVencimento(cara.getId(), LocalDate.now()).orElseThrow();
        assertEquals(StatusExecucaoRecorrencia.FALHA_SALDO, falha.getStatus());
        assertEquals(LocalDate.now(), contaFixaRepository.findById(cara.getId()).orElseThrow().getDataProximoVencimento());
    }

    @Test
    void pularAntesDoVencimentoImpedeLancamento() {
        LocalDate vencimento = LocalDate.now().plusDays(3);
        ContaFixa conta = salvar("Internet", "100.00", TipoTransacao.SAIDA, true, vencimento);
        service.pularMes(conta.getId(), usuario.getId());

        var execucao = execucaoRepository.findByContaFixaIdAndDataVencimento(conta.getId(), vencimento).orElseThrow();
        assertEquals(StatusExecucaoRecorrencia.PULADA, execucao.getStatus());
        assertTrue(contaFixaRepository.findById(conta.getId()).orElseThrow().getDataProximoVencimento().isAfter(vencimento));
        assertTrue(transacaoRepository.findByUsuarioId(usuario.getId()).isEmpty());
    }

    private ContaFixa salvar(String nome, String valor, TipoTransacao tipo, boolean automatica, LocalDate vencimento) {
        ContaFixa conta = new ContaFixa();
        conta.setUsuario(usuario);
        conta.setNome(nome);
        conta.setValorPlanejado(new BigDecimal(valor));
        conta.setDiaVencimento(vencimento.getDayOfMonth());
        conta.setDataProximoVencimento(vencimento);
        conta.setStatus(StatusPagamento.PENDENTE);
        conta.setRecorrente(true);
        conta.setAtivo(true);
        conta.setTipo(tipo);
        conta.setExecucaoAutomatica(automatica);
        if (automatica) conta.setCarteira(carteira);
        return contaFixaRepository.save(conta);
    }
}

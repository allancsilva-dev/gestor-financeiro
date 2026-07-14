package com.gestor.financeiro;

import com.gestor.financeiro.dto.ProjecaoMensalDto;
import com.gestor.financeiro.dto.ProjecaoResponse;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ProjecaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjecaoServiceTest {

    @Autowired
    private ProjecaoService projecaoService;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private ContaFixaRepository contaFixaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Projetor", "projecao-service@teste.com", passwordEncoder.encode("123456")));
        carteiraRepository.save(TestDataFactory.carteira(usuario, "Conta", new BigDecimal("1000.00")));
    }

    @Test
    void projecaoSomaContaFixaPendenteDoMesEIgnoraPaga() {
        LocalDate venceEsteMes = LocalDate.now().withDayOfMonth(15);
        // pendente vencendo este mes -> entra na projecao do mes 0
        contaFixaRepository.save(contaFixa("Aluguel", "800.00", venceEsteMes, StatusPagamento.PENDENTE));
        // paga -> excluida
        contaFixaRepository.save(contaFixa("Internet", "100.00", venceEsteMes, StatusPagamento.PAGO));

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 3);

        assertEquals(0, new BigDecimal("1000.00").compareTo(r.saldoAtual()));
        ProjecaoMensalDto mes0 = r.meses().get(0);
        assertEquals(0, new BigDecimal("800.00").compareTo(mes0.totalContasFixas()),
                "so a conta fixa pendente do mes deve somar");
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalParcelas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalFaturas()));
        // saldo final = 1000 - 800
        assertEquals(0, new BigDecimal("200.00").compareTo(mes0.saldoFinal()));
    }

    @Test
    void projecaoSemLancamentosMantemSaldo() {
        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 2);
        ProjecaoMensalDto mes0 = r.meses().get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(mes0.totalSaidas()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(mes0.saldoFinal()));
    }

    @Test
    void salarioRecorrenteSomaEmTodosOsMeses() {
        ContaFixa salario = contaFixa("Salário", "2500.00", LocalDate.now().withDayOfMonth(15), StatusPagamento.PENDENTE);
        salario.setTipo(TipoTransacao.ENTRADA);
        salario.setRecorrente(true);
        contaFixaRepository.save(salario);

        ProjecaoResponse r = projecaoService.projetar(usuario.getId(), 3);

        assertEquals(0, new BigDecimal("2500.00").compareTo(r.meses().get(0).totalEntradas()));
        assertEquals(0, new BigDecimal("3500.00").compareTo(r.meses().get(0).saldoFinal()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(r.meses().get(1).saldoFinal()));
        assertEquals(0, new BigDecimal("8500.00").compareTo(r.meses().get(2).saldoFinal()));
    }

    private ContaFixa contaFixa(String nome, String valor, LocalDate vencimento, StatusPagamento status) {
        ContaFixa cf = new ContaFixa();
        cf.setUsuario(usuario);
        cf.setNome(nome);
        cf.setValorPlanejado(new BigDecimal(valor));
        cf.setDiaVencimento(vencimento.getDayOfMonth());
        cf.setDataProximoVencimento(vencimento);
        cf.setStatus(status);
        cf.setAtivo(true);
        return cf;
    }
}

package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.service.CartaoService;
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

/**
 * V67 — assinatura cobrada no cartao. A recorrencia passa a ter dois destinos
 * mutuamente exclusivos: caixa (carteira) ou cartao (conta). No cartao nao ha
 * saldo a validar, nao ha Parcela e o caixa nunca e tocado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContaFixaCartaoTest {

    @Autowired ContaFixaService service;
    @Autowired CartaoService cartaoService;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired ExecucaoRecorrenciaRepository execucaoRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired FaturaLancamentoRepository faturaLancamentoRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Carteira carteira;
    private Conta cartao;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Assinante", "assinatura-cartao@teste.com", passwordEncoder.encode("123456")));
        carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Principal", new BigDecimal("1000.00")));

        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        cartao = cartaoService.criar(novo, usuario.getId());
    }

    @Test
    void assinaturaDeCartaoLancaNaFaturaSemTocarCaixaESemParcela() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);

        ContaFixa depois = service.realizar(
                netflix.getId(), null, null, usuario.getId(), false);

        var transacoes = transacaoRepository.findByUsuarioId(usuario.getId());
        assertEquals(1, transacoes.size());
        var transacao = transacoes.get(0);
        // Na fatura o nome da assinatura aparece limpo, sem prefixo "Pagamento:"
        assertEquals("Netflix", transacao.getDescricao());
        assertNotNull(transacao.getConta());
        assertNull(transacao.getCarteira());

        var lancamentos = faturaLancamentoRepository.findByTransacaoId(transacao.getId());
        assertEquals(1, lancamentos.size());
        assertEquals(0, new BigDecimal("60.00").compareTo(lancamentos.get(0).getValor()));

        // Cartao tem cronograma proprio: Parcela nunca e criada
        assertTrue(parcelaRepository.findByTransacaoId(transacao.getId()).isEmpty());
        // Caixa intacto: assinatura de cartao nao debita conta corrente
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        // Passivo do cartao absorve a divida (invariante do espelho)
        assertEquals(0, new BigDecimal("60.00").compareTo(
                carteiraRepository.findById(cartao.getContaFinanceira().getId())
                        .orElseThrow().getSaldo().abs()));

        assertTrue(depois.getDataProximoVencimento().isAfter(LocalDate.now()));
    }

    @Test
    void carteiraIdNoCorpoNaoDebitaCaixaEmRecorrenciaDeCartao() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);

        // Cliente desatualizado manda carteiraId; a assinatura continua indo para a fatura
        service.realizar(netflix.getId(), null, carteira.getId(), usuario.getId(), false);

        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(1, faturaLancamentoRepository
                .findByTransacaoId(transacaoRepository.findByUsuarioId(usuario.getId()).get(0).getId()).size());
    }

    @Test
    void segundaExecucaoDaMesmaOcorrenciaNaoDuplicaLancamento() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now(), cartao, null);
        service.realizarAutomatica(netflix.getId());

        // Volta o vencimento para simular a corrida do scheduler em duas instancias
        ContaFixa recarregada = contaFixaRepository.findById(netflix.getId()).orElseThrow();
        recarregada.setDataProximoVencimento(LocalDate.now());
        contaFixaRepository.saveAndFlush(recarregada);

        assertThrows(BusinessException.class, () -> service.realizarAutomatica(netflix.getId()));
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
        assertEquals(StatusExecucaoRecorrencia.REALIZADA, execucaoRepository
                .findByContaFixaIdAndDataVencimento(netflix.getId(), LocalDate.now())
                .orElseThrow().getStatus());
    }

    @Test
    void execucaoAutomaticaComVencimentoFuturoNaoLanca() {
        // Ocorrencia ja avancada por outra instancia: a segunda nao pode cobrar de novo
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);

        service.realizarAutomatica(netflix.getId());

        assertTrue(transacaoRepository.findByUsuarioId(usuario.getId()).isEmpty());
        assertEquals(LocalDate.now().plusDays(5),
                contaFixaRepository.findById(netflix.getId()).orElseThrow().getDataProximoVencimento());
    }

    @Test
    void desativarCartaoDesativaAsAssinaturasQueCobravamNele() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);
        ContaFixa aluguel = criar("Aluguel", "900.00", true, LocalDate.now().plusDays(5), null, carteira);

        int desativadas = cartaoService.deletarCartao(cartao.getId(), usuario.getId());

        assertEquals(1, desativadas);
        assertFalse(contaFixaRepository.findById(netflix.getId()).orElseThrow().getAtivo());
        // Recorrencia de caixa nao e afetada
        assertTrue(contaFixaRepository.findById(aluguel.getId()).orElseThrow().getAtivo());
    }

    @Test
    void primeiraOcorrenciaVencidaExecutaNaCriacao() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now(), cartao, null);

        // criar() ja disparou a primeira cobranca: quem cadastra hoje ve hoje
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
        assertTrue(contaFixaRepository.findById(netflix.getId()).orElseThrow()
                .getDataProximoVencimento().isAfter(LocalDate.now()));
    }

    @Test
    void primeiraOcorrenciaSemSaldoCadastraERegistraFalha() {
        // Caixa sem saldo não derruba o cadastro: a ocorrência fica visível como falha
        ContaFixa cara = criar("Aluguel", "5000.00", true, LocalDate.now(), null, carteira);

        assertTrue(contaFixaRepository.findById(cara.getId()).orElseThrow().getAtivo());
        assertTrue(transacaoRepository.findByUsuarioId(usuario.getId()).isEmpty());
        assertEquals(StatusExecucaoRecorrencia.FALHA_SALDO, execucaoRepository
                .findByContaFixaIdAndDataVencimento(cara.getId(), LocalDate.now())
                .orElseThrow().getStatus());
    }

    @Test
    void destinoDuplicadoEhRecusado() {
        assertThrows(BusinessException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, carteira));
    }

    @Test
    void entradaComCartaoEhRecusada() {
        ContaFixa salario = base("Salário", "5000.00", true, LocalDate.now().plusDays(5));
        salario.setTipo(TipoTransacao.ENTRADA);
        salario.setConta(cartao);
        assertThrows(BusinessException.class, () -> service.criar(salario, usuario.getId()));
    }

    @Test
    void cartaoInativoEhRecusado() {
        cartaoService.deletarCartao(cartao.getId(), usuario.getId());
        assertThrows(BusinessException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null));
    }

    @Test
    void cartaoDeOutroUsuarioEhRecusado() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "outro-assinante@teste.com", passwordEncoder.encode("123456")));
        Conta novo = new Conta();
        novo.setNome("Cartao alheio");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        Conta alheio = cartaoService.criar(novo, outro.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), alheio, null));
    }

    /**
     * Reativar sem revalidar o destino criava recorrencia zumbi: o cartao removido faz
     * toda execucao automatica estourar, o scheduler engole a excecao, o vencimento nunca
     * avanca e nada aparece em /falhas-pendentes, que so cobre FALHA_SALDO.
     */
    @Test
    void reativarAssinaturaDeCartaoRemovidoRecusaEmVezDeVirarZumbi() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);
        cartaoService.deletarCartao(cartao.getId(), usuario.getId());
        assertFalse(contaFixaRepository.findById(netflix.getId()).orElseThrow().getAtivo());

        BusinessException erro = assertThrows(BusinessException.class,
                () -> service.reativar(netflix.getId(), usuario.getId()));

        assertTrue(erro.getMessage().contains("cartão desta assinatura foi removido"),
                "a mensagem precisa dizer o que fazer; veio: " + erro.getMessage());
        assertFalse(contaFixaRepository.findById(netflix.getId()).orElseThrow().getAtivo(),
                "recusar nao pode deixar a recorrencia meio reativada");
    }

    /**
     * Cobrar hoje, cancelar e reativar no mesmo dia fazia o recalculo pousar na ocorrencia
     * ja REALIZADA. Sem avancar, todo realizar/pular seguinte batia no unique de
     * execucoes_recorrencia e a recorrencia travava em 400 para sempre.
     */
    @Test
    void reativarNoMesmoDiaDeUmaOcorrenciaJaRealizadaNaoTrava() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);
        service.realizar(netflix.getId(), null, null, usuario.getId(), false);
        service.deletar(netflix.getId(), usuario.getId());

        ContaFixa reativada = service.reativar(netflix.getId(), usuario.getId());

        assertTrue(reativada.getAtivo());
        assertTrue(reativada.getDataProximoVencimento().isAfter(LocalDate.now()),
                "a ocorrencia ja realizada precisa ter sido pulada");
        // e o proximo realizar precisa funcionar, nao estourar no unique
        service.realizar(reativada.getId(), null, null, usuario.getId(), true);
    }

    /** Cancelar em janeiro e reativar em junho nao pode cobrar os meses parados de uma vez. */
    @Test
    void reativarNaoCobraRetroativo() {
        ContaFixa netflix = criar("Netflix", "60.00", true, LocalDate.now().plusDays(5), cartao, null);
        service.deletar(netflix.getId(), usuario.getId());
        int lancamentosAntes = faturaLancamentoRepository.findAll().size();

        ContaFixa reativada = service.reativar(netflix.getId(), usuario.getId());

        assertFalse(reativada.getDataProximoVencimento().isBefore(LocalDate.now()),
                "vencimento reativado nunca pode ficar no passado");
        assertEquals(lancamentosAntes, faturaLancamentoRepository.findAll().size(),
                "reativar nao lanca cobranca por conta propria");
    }

    /**
     * Conta de um mes so que cumpriu o ciclo nao volta a cobrar.
     *
     * avancarOcorrencia encerra a nao-recorrente com ativo=false + PAGO, o mesmo
     * ativo=false de um cancelamento. A tela distingue os dois e nao oferece "Reativar"
     * na concluida, mas quem decide o que e cobranca valida e o servidor: sem este guard,
     * um PUT direto em /reativar ressuscitaria uma serie que ja terminou.
     */
    @Test
    void reativarRecusaContaQueJaCumpriuOCiclo() {
        ContaFixa avulsa = base("IPTU 2026", "300.00", false, LocalDate.now());
        avulsa.setRecorrente(false);
        avulsa.setConta(cartao);
        ContaFixa criada = service.criar(avulsa, usuario.getId());
        service.realizar(criada.getId(), null, null, usuario.getId(), false);

        ContaFixa encerrada = contaFixaRepository.findById(criada.getId()).orElseThrow();
        assertFalse(encerrada.getAtivo(), "fim de ciclo desativa a conta");
        assertEquals(StatusPagamento.PAGO, encerrada.getStatus());

        BusinessException erro = assertThrows(BusinessException.class,
                () -> service.reativar(criada.getId(), usuario.getId()));
        assertTrue(erro.getMessage().contains("concluída"), erro.getMessage());
    }

    /** Cancelar nao apaga o que ja foi cobrado: a fatura e historico. */
    @Test
    void cancelarMantemAsCobrancasJaLancadasNaFatura() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);
        service.realizar(netflix.getId(), null, null, usuario.getId(), false);
        int lancamentos = faturaLancamentoRepository.findAll().size();
        assertEquals(1, lancamentos);

        service.deletar(netflix.getId(), usuario.getId());

        assertFalse(contaFixaRepository.findById(netflix.getId()).orElseThrow().getAtivo());
        assertEquals(lancamentos, faturaLancamentoRepository.findAll().size(),
                "cancelar assinatura nao pode estornar o que ja entrou na fatura");
        assertEquals(1, transacaoRepository.findByUsuarioId(usuario.getId()).size());
    }

    private ContaFixa base(String nome, String valor, boolean automatica, LocalDate vencimento) {
        ContaFixa conta = new ContaFixa();
        conta.setUsuario(usuario);
        conta.setNome(nome);
        conta.setValorPlanejado(new BigDecimal(valor));
        conta.setDiaVencimento(vencimento.getDayOfMonth());
        conta.setStatus(StatusPagamento.PENDENTE);
        conta.setRecorrente(true);
        conta.setAtivo(true);
        conta.setTipo(TipoTransacao.SAIDA);
        conta.setExecucaoAutomatica(automatica);
        return conta;
    }

    /**
     * Editar uma assinatura de cartao devolvia 500.
     *
     * O controller monta o destino como um stub — um {@link Conta} so com o id — e
     * {@code atualizar} o pendurava direto na entidade ja gerenciada. A primeira consulta
     * do metodo (a da categoria) dispara auto-flush, o Hibernate tenta cascatear o stub
     * destacado e estoura "Detached entity with generated id ... has an uninitialized
     * version value". Os testes antigos nao pegavam porque passavam a entidade gerenciada
     * do proprio contexto de persistencia, e nao o stub que chega do JSON.
     */
    @Test
    void editarAssinaturaDeCartaoNaoEstouraComDestinoVindoDoJson() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);

        ContaFixa edicao = base("Netflix BR", "60.00", false, LocalDate.now());
        edicao.setConta(stubDoCartao());

        ContaFixa depois = service.atualizar(netflix.getId(), edicao, usuario.getId());
        contaFixaRepository.flush();

        assertEquals("Netflix BR", depois.getNome());
        assertEquals(cartao.getId(), depois.getConta().getId());
        assertNull(depois.getCarteira());
    }

    /**
     * Mudar so o dia de vencimento tem de mover a serie. O piso de comparacao era lido
     * depois do setter, entao "dia anterior" era o dia novo e a condicao nunca era
     * verdadeira: a cobranca continuava marcada para o dia velho.
     */
    @Test
    void mudarODiaDeVencimentoMoveAProximaCobranca() {
        ContaFixa netflix = criar("Netflix", "60.00", false, LocalDate.now(), cartao, null);
        int diaNovo = netflix.getDiaVencimento() == 28 ? 27 : 28;

        ContaFixa edicao = base("Netflix", "60.00", false, LocalDate.now());
        edicao.setDiaVencimento(diaNovo);
        edicao.setConta(stubDoCartao());

        ContaFixa depois = service.atualizar(netflix.getId(), edicao, usuario.getId());

        assertEquals(diaNovo, depois.getDiaVencimento());
        assertEquals(diaNovo, depois.getDataProximoVencimento().getDayOfMonth());
    }

    /** O destino como o JSON entrega: id e mais nada. */
    private Conta stubDoCartao() {
        Conta stub = new Conta();
        stub.setId(cartao.getId());
        return stub;
    }

    private ContaFixa criar(String nome, String valor, boolean automatica, LocalDate vencimento,
                            Conta cartaoDestino, Carteira carteiraDestino) {
        ContaFixa conta = base(nome, valor, automatica, vencimento);
        conta.setConta(cartaoDestino);
        conta.setCarteira(carteiraDestino);
        return service.criar(conta, usuario.getId());
    }
}

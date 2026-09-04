package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.ExecucaoRecorrencia;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.ExecucaoRecorrenciaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.util.CalendarioRecorrencia;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContaFixaService {
    private final java.time.Clock clock;
    private final ContaFixaRepository contaFixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoService transacaoService;
    private final CarteiraRepository carteiraRepository;
    private final ExecucaoRecorrenciaRepository execucaoRepository;
    private final ContaRepository contaRepository;

    
    // Lista contas fixas ativas do usuário
    public Page<ContaFixa> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return listarPorUsuario(usuarioId, pageable, true);
    }

    /**
     * Lista as recorrencias do titular. {@code ativo=false} traz as canceladas, que a UI
     * precisa para oferecer "Reativar" — sem isso o endpoint de reativar e inalcancavel,
     * porque a cancelada some da unica listagem que existe.
     */
    public Page<ContaFixa> listarPorUsuario(Long usuarioId, Pageable pageable, boolean ativo) {
        return ativo
                ? contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId, pageable)
                : contaFixaRepository.findByUsuarioIdAndAtivoFalse(usuarioId, pageable);
    }
    
    // Cria nova conta fixa
    @Transactional
    public ContaFixa criar(ContaFixa contaFixa, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        contaFixa.setUsuario(usuario);

        // Valida ownership da categoria, se informada
        if (contaFixa.getCategoria() != null && contaFixa.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    contaFixa.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            contaFixa.setCategoria(categoria);
        }

        // Valores padrão
        if (contaFixa.getAtivo() == null) contaFixa.setAtivo(true);
        if (contaFixa.getRecorrente() == null) contaFixa.setRecorrente(true);
        if (contaFixa.getStatus() == null) contaFixa.setStatus(StatusPagamento.PENDENTE);
        if (contaFixa.getTipo() == null) {
            // Compatibilidade com clientes antigos que nao enviam tipo; medir uso antes de exigir @NotNull
            log.warn("ContaFixa criada sem tipo explicito (usuarioId={}); aplicando fallback SAIDA", usuarioId);
            contaFixa.setTipo(TipoTransacao.SAIDA);
        }
        if (contaFixa.getExecucaoAutomatica() == null) contaFixa.setExecucaoAutomatica(false);
        resolverDestino(contaFixa, idDe(contaFixa.getCarteira()), idDe(contaFixa.getConta()), usuarioId);
        resolverFrequencia(contaFixa);
        
        // Calcula próximo vencimento
        calcularProximoVencimento(contaFixa);
        
        ContaFixa salva = contaFixaRepository.save(contaFixa);

        // Primeira ocorrencia vencida hoje nao pode esperar o scheduler da madrugada:
        // quem cadastra uma assinatura que ja venceu espera ve-la no mes corrente.
        //
        // Roda na mesma transacao do cadastro de proposito: ou a recorrencia existe com a
        // primeira cobranca feita, ou nao existe. Capturar aqui seria enganoso — a
        // transacao ja estaria marcada rollback-only e o cadastro morreria no commit.
        // Saldo insuficiente nao passa por aqui: vira FALHA_SALDO sem lancar.
        if (Boolean.TRUE.equals(salva.getExecucaoAutomatica())
                && !salva.getDataProximoVencimento().isAfter(LocalDate.now(clock))) {
            return realizarAutomatica(salva.getId());
        }
        return salva;
    }
    
    // Calcula data do próximo vencimento
    private void calcularProximoVencimento(ContaFixa contaFixa) {
        contaFixa.setDataProximoVencimento(CalendarioRecorrencia.primeiraAPartirDe(
                LocalDate.now(clock),
                contaFixa.getFrequencia(),
                contaFixa.getDiaVencimento(),
                contaFixa.getDataAncora()));
    }

    /**
     * Normaliza e valida a frequencia (V72). Cada CHECK do banco tem aqui o equivalente
     * em 4xx: o CHECK e backstop, nunca a mensagem que o usuario ve.
     */
    private void resolverFrequencia(ContaFixa conta) {
        if (conta.getFrequencia() == null) conta.setFrequencia(FrequenciaRecorrencia.MENSAL);

        if (conta.getFrequencia().isSubMensal()) {
            if (conta.getDataAncora() == null) {
                throw new BusinessException(
                        "Informe a data da primeira cobrança para recorrência semanal ou quinzenal");
            }
            // "Dia do mes" nao existe em serie sub-mensal; o campo continua NOT NULL
            // (V1) e vira exibicao, derivado da ancora.
            conta.setDiaVencimento(conta.getDataAncora().getDayOfMonth());
        } else if (conta.getFrequencia() == FrequenciaRecorrencia.MENSAL) {
            // Todo mes tem ocorrencia, entao o mes de partida e irrelevante: guardar
            // ancora aqui so criaria dado redundante (e violaria o CHECK da V73).
            conta.setDataAncora(null);
        } else if (conta.getDataAncora() != null) {
            // De BIMESTRAL a ANUAL a ancora e opcional e define o mes do aniversario.
            // O dia exibido acompanha a ancora, senao "todo dia 10" contradiz uma ancora
            // que cai no dia 15.
            conta.setDiaVencimento(conta.getDataAncora().getDayOfMonth());
        }
    }
    
    // ✅ CORRIGIDO: Mantém como PAGO e só avança o vencimento
    @Transactional
    public ContaFixa marcarComoPaga(Long id, BigDecimal valorPago, Long carteiraId, Long usuarioId) {
        return realizar(id, valorPago, carteiraId, usuarioId, false);
    }

    @Transactional
    public ContaFixa realizar(Long id, BigDecimal valor, Long carteiraId, Long usuarioId, boolean automatico) {
        ContaFixa conta = contaFixaRepository.findByIdAndUsuarioIdForUpdate(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Recorrência não encontrada"));
        LocalDate vencimento = conta.getDataProximoVencimento();
        if (!automatico && YearMonth.from(vencimento).isAfter(YearMonth.now(clock))) {
            throw new BusinessException("A próxima ocorrência ainda não está disponível");
        }
        // Os ids chegam ao scheduler antes do lock: sem revalidar aqui, uma segunda
        // instancia executaria a ocorrencia que a primeira ja avancou. No caixa o saldo
        // freia; no cartao nada freiaria.
        if (automatico && vencimento.isAfter(LocalDate.now(clock))) {
            return conta;
        }
        ExecucaoRecorrencia execucao = execucaoRepository
                .findByContaFixaIdAndDataVencimento(id, vencimento).orElse(null);
        if (execucao != null && (execucao.getStatus() == StatusExecucaoRecorrencia.REALIZADA
                || execucao.getStatus() == StatusExecucaoRecorrencia.PULADA)) {
            throw new BusinessException("Esta recorrência já foi realizada ou pulada");
        }

        // Assinatura de cartao nunca debita caixa: o carteiraId do corpo da requisicao
        // e ignorado quando a recorrencia tem cartao (R5).
        Conta cartao = conta.getConta();
        Carteira carteira = null;
        BigDecimal valorEfetivo = valor == null ? conta.getValorPlanejado() : valor;

        if (cartao == null) {
            Long carteiraEfetiva = carteiraId != null ? carteiraId
                    : conta.getCarteira() == null ? null : conta.getCarteira().getId();
            if (carteiraEfetiva == null) throw new BusinessException("Informe a carteira");
            carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(carteiraEfetiva, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

            if (conta.getTipo() == TipoTransacao.SAIDA && carteira.getSaldo().compareTo(valorEfetivo) < 0) {
                if (automatico) registrarFalhaSaldo(conta, execucao, vencimento);
                if (!automatico) throw new BusinessException("Saldo insuficiente");
                return conta;
            }
        } else if (!Boolean.TRUE.equals(cartao.getAtivo())) {
            // Cartao removido nao recebe cobranca nova; a recorrencia fica parada, visivel.
            throw new BusinessException("Cartão da recorrência está inativo");
        }

        String chave = "RECORRENCIA:" + conta.getId() + ":" + vencimento;
        Transacao transacao = new Transacao();
        // Na fatura, "Pagamento: X" se confunde com pagamento de fatura; a assinatura
        // aparece pelo proprio nome.
        transacao.setDescricao(cartao != null ? conta.getNome()
                : (conta.getTipo() == TipoTransacao.ENTRADA ? "Recebimento: " : "Pagamento: ") + conta.getNome());
        transacao.setData(automatico ? vencimento : LocalDate.now(clock));
        transacao.setTipo(conta.getTipo());
        transacao.setValorTotal(valorEfetivo);
        transacao.setParcelado(false);
        transacao.setCategoria(conta.getCategoria());
        transacao.setContaFixa(conta);
        transacao.setObservacoes(automatico ? "Execução automática de recorrência" : "Execução manual de recorrência");
        transacao.setCarteira(carteira);
        transacao.setConta(cartao);
        Transacao salva = transacaoService.criar(transacao, usuarioId, chave);

        if (execucao == null) {
            execucao = novaExecucao(conta, vencimento);
        }
        execucao.setStatus(StatusExecucaoRecorrencia.REALIZADA);
        execucao.setTentadoEm(LocalDateTime.now(clock));
        execucao.setMensagemFalha(null);
        execucao.setTransacao(salva);
        gravarExecucao(execucao);

        conta.setValorReal(valorEfetivo);
        avancarOcorrencia(conta);
        return contaFixaRepository.save(conta);
    }

    @Transactional
    public ContaFixa realizarAutomatica(Long id) {
        ContaFixa conta = contaFixaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recorrência não encontrada"));
        return realizar(id, conta.getValorPlanejado(),
                conta.getConta() != null || conta.getCarteira() == null
                        ? null : conta.getCarteira().getId(),
                conta.getUsuario().getId(), true);
    }

    private void registrarFalhaSaldo(ContaFixa conta, ExecucaoRecorrencia execucao, LocalDate vencimento) {
        if (execucao == null) execucao = novaExecucao(conta, vencimento);
        execucao.setStatus(StatusExecucaoRecorrencia.FALHA_SALDO);
        execucao.setTentadoEm(LocalDateTime.now(clock));
        execucao.setMensagemFalha("Saldo insuficiente na carteira selecionada");
        execucaoRepository.save(execucao);
    }

    /**
     * A unicidade (conta_fixa_id, data_vencimento) e a barreira final contra ocorrencia
     * duplicada. Numa corrida ela estoura no flush; traduzimos para o mesmo 400 do
     * caminho sequencial em vez de devolver 500.
     */
    private void gravarExecucao(ExecucaoRecorrencia execucao) {
        try {
            execucaoRepository.saveAndFlush(execucao);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Esta recorrência já foi realizada ou pulada");
        }
    }

    private ExecucaoRecorrencia novaExecucao(ContaFixa conta, LocalDate vencimento) {
        ExecucaoRecorrencia e = new ExecucaoRecorrencia();
        e.setContaFixa(conta);
        e.setUsuario(conta.getUsuario());
        e.setDataVencimento(vencimento);
        return e;
    }

    private void avancarOcorrencia(ContaFixa conta) {
        if (Boolean.TRUE.equals(conta.getRecorrente())) {
            conta.setDataProximoVencimento(CalendarioRecorrencia.proxima(
                    conta.getDataProximoVencimento(),
                    conta.getFrequencia(),
                    conta.getDiaVencimento()));
            conta.setStatus(StatusPagamento.PENDENTE);
        } else {
            conta.setStatus(StatusPagamento.PAGO);
            conta.setAtivo(false);
        }
    }

    @Transactional
    public ContaFixa pularMes(Long id, Long usuarioId) {
        ContaFixa conta = contaFixaRepository.findByIdAndUsuarioIdForUpdate(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Recorrência não encontrada"));

        if (!conta.getRecorrente()) {
            throw new BusinessException("Apenas contas recorrentes podem pular mês");
        }

        if (conta.getAtivo() == null || !conta.getAtivo()) {
            throw new BusinessException("Conta fixa está inativa");
        }

        LocalDate vencimento = conta.getDataProximoVencimento();
        if (LocalDate.now(clock).isAfter(vencimento)) throw new BusinessException("O vencimento já passou");
        if (execucaoRepository.findByContaFixaIdAndDataVencimento(id, vencimento).isPresent())
            throw new BusinessException("Esta ocorrência já foi processada");
        ExecucaoRecorrencia execucao = novaExecucao(conta, vencimento);
        execucao.setStatus(StatusExecucaoRecorrencia.PULADA);
        execucao.setTentadoEm(LocalDateTime.now(clock));
        gravarExecucao(execucao);
        avancarOcorrencia(conta);

        return contaFixaRepository.save(conta);
    }

    /**
     * Volta a cobrar uma recorrencia cancelada.
     *
     * <p>Nao cobra retroativo: o proximo vencimento e recalculado a partir de hoje, entao
     * o scheduler nao tem meses parados para recuperar. Quem cancelou em janeiro e
     * reativou em junho nao leva cinco cobrancas de uma vez.</p>
     */
    @Transactional
    public ContaFixa reativar(Long id, Long usuarioId) {
        // Lock pessimista como em realizar/pularMes: reativar mexe na serie, e duas
        // chamadas simultaneas deixariam dataProximoVencimento inconsistente.
        ContaFixa conta = contaFixaRepository.findByIdAndUsuarioIdForUpdate(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Recorrência não encontrada"));

        if (conta.getAtivo() != null && conta.getAtivo()) {
            throw new BusinessException("Conta fixa já está ativa");
        }

        // "Concluida" nao e "cancelada". avancarOcorrencia encerra uma conta de um mes so
        // com ativo=false + PAGO quando ela cumpre o ciclo, e as duas caem no mesmo
        // ativo=false. A UI ja esconde o botao Reativar nesse caso, mas quem decide o que
        // e uma cobranca valida e o servidor: sem este guard, um PUT direto ressuscitaria
        // uma serie que ja terminou.
        if (Boolean.FALSE.equals(conta.getRecorrente()) && conta.getStatus() == StatusPagamento.PAGO) {
            throw new BusinessException(
                    "Esta conta já foi concluída. Crie uma nova recorrência para cobrar de novo");
        }

        // Sem revalidar o destino, reativar uma assinatura cujo cartao foi excluido cria
        // uma recorrencia zumbi: toda execucao automatica estoura "Cartão da recorrência
        // está inativo", o scheduler engole a excecao, o vencimento nunca avanca e nada
        // aparece em /falhas-pendentes (que so cobre FALHA_SALDO). Melhor recusar aqui,
        // com mensagem que diz o que fazer.
        if (conta.getConta() != null && !Boolean.TRUE.equals(conta.getConta().getAtivo())) {
            throw new BusinessException(
                    "O cartão desta assinatura foi removido. Edite a recorrência e escolha outro destino antes de reativar");
        }

        conta.setAtivo(true);
        conta.setStatus(StatusPagamento.PENDENTE);
        calcularProximoVencimento(conta);
        // A data recalculada pode cair numa ocorrencia ja REALIZADA/PULADA (cobrou hoje,
        // cancelou, reativou no mesmo dia). Sem avancar, todo realizar/pular seguinte bate
        // no unique de execucoes_recorrencia e a recorrencia trava em 400 para sempre.
        pularOcorrenciasJaExecutadas(conta);

        return contaFixaRepository.save(conta);
    }
    
    // Atualiza conta fixa
    @Transactional
    public ContaFixa atualizar(Long id, ContaFixa contaAtualizada, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);

        // Estado da serie ANTES de qualquer setter. E o que decide, la embaixo, se o
        // aniversario se move: ler diaVencimento depois do setter compararia o valor novo
        // com ele mesmo, e mudar so o dia de uma mensal nunca recalcularia a serie.
        FrequenciaRecorrencia frequenciaAnterior = conta.getFrequencia();
        LocalDate ancoraAnterior = conta.getDataAncora();
        Integer diaAnterior = conta.getDiaVencimento();

        conta.setNome(contaAtualizada.getNome());
        conta.setValorPlanejado(contaAtualizada.getValorPlanejado());
        conta.setDiaVencimento(contaAtualizada.getDiaVencimento());
        conta.setTipo(contaAtualizada.getTipo() == null ? TipoTransacao.SAIDA : contaAtualizada.getTipo());
        conta.setExecucaoAutomatica(Boolean.TRUE.equals(contaAtualizada.getExecucaoAutomatica()));
        // O destino sai dos ids do payload, nunca dos stubs: ver resolverDestino.
        resolverDestino(conta, idDe(contaAtualizada.getCarteira()),
                idDe(contaAtualizada.getConta()), usuarioId);

        if (contaAtualizada.getCategoria() != null && contaAtualizada.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    contaAtualizada.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            conta.setCategoria(categoria);
        }
        
        conta.setObservacoes(contaAtualizada.getObservacoes());

        // A serie so e recalculada quando algo que a define muda. Recalcular sempre
        // antecipava o aniversario: numa anual de marco, editar o valor em setembro movia
        // a cobranca para setembro, porque calcularProximoVencimento parte de hoje.
        conta.setFrequencia(contaAtualizada.getFrequencia());
        conta.setDataAncora(contaAtualizada.getDataAncora());
        resolverFrequencia(conta);

        boolean serieMudou = !java.util.Objects.equals(frequenciaAnterior, conta.getFrequencia())
                || !java.util.Objects.equals(ancoraAnterior, conta.getDataAncora())
                || !java.util.Objects.equals(diaAnterior, conta.getDiaVencimento())
                || conta.getDataProximoVencimento() == null;

        if (serieMudou) {
            calcularProximoVencimento(conta);
            pularOcorrenciasJaExecutadas(conta);
        }
        
        return contaFixaRepository.save(conta);
    }
    
    /**
     * Editar recalcula o proximo vencimento do zero, e o recalculo pode cair numa data
     * que ja tem execucao REALIZADA ou PULADA — trocar a frequencia de uma recorrencia
     * viva e o caminho mais facil para isso. Sem avancar aqui, o proximo realizar bate
     * no unique de execucoes_recorrencia e a recorrencia trava em 400 para sempre.
     */
    private void pularOcorrenciasJaExecutadas(ContaFixa conta) {
        if (conta.getId() == null) return;
        if (!Boolean.TRUE.equals(conta.getRecorrente())) return;

        // Piso da serie: a ultima ocorrencia ja REALIZADA ou PULADA. Comparar so a data
        // exata (como antes) deixava passar o caso perigoso — mudar a ancora move a serie
        // para tras dentro de um mes ja cobrado, com dia diferente, e o unique
        // (conta_fixa_id, data_vencimento) nao pega isso: viraria cobranca dupla.
        LocalDate piso = execucaoRepository
                .findTopByContaFixaIdAndStatusInOrderByDataVencimentoDesc(conta.getId(),
                        List.of(StatusExecucaoRecorrencia.REALIZADA, StatusExecucaoRecorrencia.PULADA))
                .map(ExecucaoRecorrencia::getDataVencimento)
                .orElse(null);
        if (piso == null) return;

        int limite = 400; // teto defensivo: nunca virar laco infinito
        while (!conta.getDataProximoVencimento().isAfter(piso) && limite-- > 0) {
            conta.setDataProximoVencimento(CalendarioRecorrencia.proxima(
                    conta.getDataProximoVencimento(), conta.getFrequencia(), conta.getDiaVencimento()));
        }
    }

    // Desativa conta fixa
    @Transactional
    public void deletar(Long id, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setAtivo(false);
        contaFixaRepository.save(conta);
    }
    
    // Busca por ID
    public ContaFixa buscarPorId(Long id) {
        return contaFixaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta fixa não encontrada"));
    }

    // Valida ownership para evitar IDOR em operações por ID.
    public ContaFixa buscarPorIdDoUsuario(Long id, Long usuarioId) {
        ContaFixa conta = contaFixaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Conta fixa não encontrada"));

        if (!conta.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta conta fixa");
        }

        return conta;
    }
    
    @Transactional
    public void atualizarContasAtrasadas() {
        LocalDate hoje = LocalDate.now(clock);
        contaFixaRepository.resetarContasPagasVencidas(StatusPagamento.PAGO, StatusPagamento.PENDENTE, hoje);
        contaFixaRepository.atualizarStatusContasAtrasadas(StatusPagamento.PENDENTE, StatusPagamento.ATRASADO, hoje);
    }

    public List<ExecucaoRecorrencia> listarFalhasPendentes(Long usuarioId) {
        return execucaoRepository.findByUsuarioIdAndStatusAndContaFixaAtivoTrueOrderByDataVencimentoAsc(
                usuarioId, StatusExecucaoRecorrencia.FALHA_SALDO);
    }

    /**
     * Um destino, nunca dois: a cobranca sai do caixa (carteira) ou do cartao (V67).
     * Valida em 400 o que os CHECKs da V67 so pegariam como 500.
     */
    /**
     * Aponta a recorrencia para o destino pedido, resolvendo os ids em entidades
     * gerenciadas.
     *
     * <p>Os ids chegam por parametro de proposito. Pendurar o stub do JSON (um
     * {@code Conta} so com id, versao nula) na entidade gerenciada e depois consultar
     * fazia o auto-flush do Hibernate tentar cascatear um objeto destacado — 500 em
     * "Detached entity ... has an uninitialized version value" ao editar assinatura de
     * cartao. Aqui a entidade so recebe o que ja veio do banco.</p>
     */
    private static Long idDe(Carteira carteira) {
        return carteira == null ? null : carteira.getId();
    }

    private static Long idDe(Conta conta) {
        return conta == null ? null : conta.getId();
    }

    private void resolverDestino(ContaFixa conta, Long carteiraId, Long cartaoId, Long usuarioId) {

        if (carteiraId != null && cartaoId != null)
            throw new BusinessException("Informe apenas um destino: conta ou cartão");
        if (Boolean.TRUE.equals(conta.getExecucaoAutomatica()) && carteiraId == null && cartaoId == null)
            throw new BusinessException("Destino é obrigatório para execução automática");
        if (cartaoId != null && conta.getTipo() != TipoTransacao.SAIDA)
            throw new BusinessException("Cartão só aceita recorrência de saída");

        if (carteiraId != null) conta.setCarteira(carteiraRepository.findByIdAndUsuarioId(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada")));
        else conta.setCarteira(null);

        if (cartaoId != null) {
            Conta cartao = contaRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
            if (!Boolean.TRUE.equals(cartao.getAtivo()))
                throw new BusinessException("Cartão está inativo");
            conta.setConta(cartao);
        } else conta.setConta(null);
    }
}

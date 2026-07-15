package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.ExecucaoRecorrencia;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.ExecucaoRecorrenciaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
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

    
    // Lista contas fixas ativas do usuário
    public Page<ContaFixa> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId, pageable);
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
        resolverCarteira(contaFixa, usuarioId);
        
        // Calcula próximo vencimento
        calcularProximoVencimento(contaFixa);
        
        return contaFixaRepository.save(contaFixa);
    }
    
    // Calcula data do próximo vencimento
    private void calcularProximoVencimento(ContaFixa contaFixa) {
        LocalDate hoje = LocalDate.now(clock);
        int diaVencimento = contaFixa.getDiaVencimento();
        
        // Próximo vencimento no mês atual
        LocalDate proximoVencimento = LocalDate.of(
            hoje.getYear(), 
            hoje.getMonthValue(), 
            Math.min(diaVencimento, hoje.lengthOfMonth())
        );
        
        // Se já passou, joga pro próximo mês
        if (proximoVencimento.isBefore(hoje)) {
            proximoVencimento = proximoVencimento.plusMonths(1);
            proximoVencimento = proximoVencimento.withDayOfMonth(
                Math.min(diaVencimento, proximoVencimento.lengthOfMonth())
            );
        }
        
        contaFixa.setDataProximoVencimento(proximoVencimento);
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
        ExecucaoRecorrencia execucao = execucaoRepository
                .findByContaFixaIdAndDataVencimento(id, vencimento).orElse(null);
        if (execucao != null && (execucao.getStatus() == StatusExecucaoRecorrencia.REALIZADA
                || execucao.getStatus() == StatusExecucaoRecorrencia.PULADA)) {
            throw new BusinessException("Esta recorrência já foi realizada ou pulada");
        }

        Long carteiraEfetiva = carteiraId != null ? carteiraId
                : conta.getCarteira() == null ? null : conta.getCarteira().getId();
        if (carteiraEfetiva == null) throw new BusinessException("Informe a carteira");
        Carteira carteira = carteiraRepository.findByIdAndUsuarioIdForUpdate(carteiraEfetiva, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        BigDecimal valorEfetivo = valor == null ? conta.getValorPlanejado() : valor;
        if (conta.getTipo() == TipoTransacao.SAIDA && carteira.getSaldo().compareTo(valorEfetivo) < 0) {
            if (automatico) registrarFalhaSaldo(conta, execucao, vencimento);
            if (!automatico) throw new BusinessException("Saldo insuficiente");
            return conta;
        }

        String chave = "RECORRENCIA:" + conta.getId() + ":" + vencimento;
        Transacao transacao = new Transacao();
        transacao.setDescricao((conta.getTipo() == TipoTransacao.ENTRADA ? "Recebimento: " : "Pagamento: ") + conta.getNome());
        transacao.setData(automatico ? vencimento : LocalDate.now(clock));
        transacao.setTipo(conta.getTipo());
        transacao.setValorTotal(valorEfetivo);
        transacao.setParcelado(false);
        transacao.setCategoria(conta.getCategoria());
        transacao.setContaFixa(conta);
        transacao.setObservacoes(automatico ? "Execução automática de recorrência" : "Execução manual de recorrência");
        transacao.setCarteira(carteira);
        Transacao salva = transacaoService.criar(transacao, usuarioId, chave);

        if (execucao == null) {
            execucao = novaExecucao(conta, vencimento);
        }
        execucao.setStatus(StatusExecucaoRecorrencia.REALIZADA);
        execucao.setTentadoEm(LocalDateTime.now(clock));
        execucao.setMensagemFalha(null);
        execucao.setTransacao(salva);
        execucaoRepository.save(execucao);

        conta.setValorReal(valorEfetivo);
        avancarOcorrencia(conta);
        return contaFixaRepository.save(conta);
    }

    @Transactional
    public ContaFixa realizarAutomatica(Long id) {
        ContaFixa conta = contaFixaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recorrência não encontrada"));
        return realizar(id, conta.getValorPlanejado(),
                conta.getCarteira() == null ? null : conta.getCarteira().getId(),
                conta.getUsuario().getId(), true);
    }

    private void registrarFalhaSaldo(ContaFixa conta, ExecucaoRecorrencia execucao, LocalDate vencimento) {
        if (execucao == null) execucao = novaExecucao(conta, vencimento);
        execucao.setStatus(StatusExecucaoRecorrencia.FALHA_SALDO);
        execucao.setTentadoEm(LocalDateTime.now(clock));
        execucao.setMensagemFalha("Saldo insuficiente na carteira selecionada");
        execucaoRepository.save(execucao);
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
            LocalDate proxima = conta.getDataProximoVencimento().plusMonths(1);
            conta.setDataProximoVencimento(proxima.withDayOfMonth(Math.min(conta.getDiaVencimento(), proxima.lengthOfMonth())));
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
        execucaoRepository.save(execucao);
        avancarOcorrencia(conta);

        return contaFixaRepository.save(conta);
    }

    @Transactional
    public ContaFixa reativar(Long id, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);

        if (conta.getAtivo() != null && conta.getAtivo()) {
            throw new BusinessException("Conta fixa já está ativa");
        }

        conta.setAtivo(true);
        conta.setStatus(StatusPagamento.PENDENTE);
        calcularProximoVencimento(conta);

        return contaFixaRepository.save(conta);
    }
    
    // Atualiza conta fixa
    @Transactional
    public ContaFixa atualizar(Long id, ContaFixa contaAtualizada, Long usuarioId) {
        ContaFixa conta = buscarPorIdDoUsuario(id, usuarioId);
        
        conta.setNome(contaAtualizada.getNome());
        conta.setValorPlanejado(contaAtualizada.getValorPlanejado());
        conta.setDiaVencimento(contaAtualizada.getDiaVencimento());
        conta.setTipo(contaAtualizada.getTipo() == null ? TipoTransacao.SAIDA : contaAtualizada.getTipo());
        conta.setExecucaoAutomatica(Boolean.TRUE.equals(contaAtualizada.getExecucaoAutomatica()));
        conta.setCarteira(contaAtualizada.getCarteira());
        
        if (contaAtualizada.getCategoria() != null && contaAtualizada.getCategoria().getId() != null) {
            Categoria categoria = categoriaRepository.findByIdAndUsuarioId(
                    contaAtualizada.getCategoria().getId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
            conta.setCategoria(categoria);
        }
        
        conta.setObservacoes(contaAtualizada.getObservacoes());
        resolverCarteira(conta, usuarioId);
        
        // Recalcula próximo vencimento
        calcularProximoVencimento(conta);
        
        return contaFixaRepository.save(conta);
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

    private void resolverCarteira(ContaFixa conta, Long usuarioId) {
        Long carteiraId = conta.getCarteira() == null ? null : conta.getCarteira().getId();
        if (Boolean.TRUE.equals(conta.getExecucaoAutomatica()) && carteiraId == null)
            throw new BusinessException("Carteira é obrigatória para execução automática");
        if (carteiraId != null) conta.setCarteira(carteiraRepository.findByIdAndUsuarioId(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada")));
        else conta.setCarteira(null);
    }
}

package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.AlertaDto;
import com.gestor.financeiro.dto.TransacaoRequest;
import com.gestor.financeiro.dto.TransacaoResponseDto;
import com.gestor.financeiro.dto.CronogramaItemResponse;
import com.gestor.financeiro.dto.SugestaoCategoriaResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.CronogramaService;
import com.gestor.financeiro.service.SugestaoCategoriaService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transacoes")
@Tag(name = "Transações", description = "Gestão de receitas e despesas do usuário autenticado")
@RequiredArgsConstructor
public class TransacaoController {
    private final TransacaoService transacaoService;
    private final CronogramaService cronogramaService;
    private final SugestaoCategoriaService sugestaoCategoriaService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CartaoService cartaoService;
    
    // GET /api/transacoes/minhas - Lista transações do usuário autenticado
    @GetMapping("/minhas")
    @Operation(summary = "Listar transações", description = "Retorna transações paginadas do usuário autenticado")
    public ResponseEntity<Page<TransacaoResponseDto>> listar(
        @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Transacao> transacoes = transacaoService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(transacoes.map(TransacaoResponseDto::fromEntity));
    }

    // GET /api/transacoes/periodo - Lista transações por período
    @GetMapping("/periodo")
    @Operation(summary = "Listar por período", description = "Retorna transações paginadas filtradas por intervalo de datas")
    public ResponseEntity<Page<TransacaoResponseDto>> listarPorPeriodo(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        @RequestParam(required = false) TipoTransacao tipo,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long categoriaId,
        @RequestParam(required = false) Long carteiraId,
        @RequestParam(required = false) Long cartaoId,
        @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Transacao> transacoes = transacaoService.listarPorPeriodo(
                usuarioId, inicio, fim, tipo, q, categoriaId, carteiraId, cartaoId, cappedPageable);
        return ResponseEntity.ok(transacoes.map(TransacaoResponseDto::fromEntity));
    }
    
    // GET /api/v1/transacoes/sugestao-categoria - Sugestão determinística (PR-F3-02)
    @GetMapping("/sugestao-categoria")
    @Operation(summary = "Sugerir categoria", description = "Sugestão determinística: última transação com descrição normalizada igual; senão, categoria mais usada nos últimos 90 dias para o mesmo tipo. Sem resultado: criterio NENHUMA e categoria nula.")
    public ResponseEntity<SugestaoCategoriaResponse> sugerirCategoria(
        @RequestParam String descricao,
        @RequestParam TipoTransacao tipo
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(sugestaoCategoriaService.sugerir(usuarioId, descricao, tipo));
    }

    // GET /api/transacoes/{id} - Busca transação por ID
    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID", description = "Retorna uma transação específica validando ownership")
    public ResponseEntity<TransacaoResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = transacaoService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(transacao));
    }

    @GetMapping("/{id}/cronograma")
    @Operation(summary = "Listar cronograma canônico da transação")
    public ResponseEntity<List<CronogramaItemResponse>> cronograma(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(cronogramaService.listar(id, usuarioId));
    }
    
    // POST /api/transacoes - Cria nova transação
    @PostMapping
    @Operation(summary = "Criar transação", description = "Cria uma nova transação para o usuário autenticado")
    public ResponseEntity<TransacaoResponseDto> criar(@Valid @RequestBody TransacaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoCriada = transacaoService.criar(transacao, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(
                transacaoCriada, alertasDeLimite(transacaoCriada, usuarioId)));
    }
    
    // PUT /api/transacoes/{id} - Atualiza transação
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Atualiza uma transação existente validando ownership")
    public ResponseEntity<TransacaoResponseDto> atualizar(
        @PathVariable Long id, 
        @Valid @RequestBody TransacaoRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoAtualizada = transacaoService.atualizar(id, transacao, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(
                transacaoAtualizada, alertasDeLimite(transacaoAtualizada, usuarioId)));
    }
    
    // DELETE /api/transacoes/{id} - Deleta transação
    @DeleteMapping("/{id}")
    @Operation(summary = "Remover transação", description = "Exclui uma transação do usuário autenticado")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        transacaoService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Aviso de limite estourado (BACKLOG-0125): nunca bloqueia a operacao, so informa.
     * Lido depois do commit do service, entao ja enxerga o passivo atualizado pela
     * compra que acabou de entrar na fatura.
     */
    private List<AlertaDto> alertasDeLimite(Transacao transacao, Long usuarioId) {
        if (transacao.getConta() == null) return List.of();
        return cartaoService.alertasDeLimite(usuarioId, transacao.getConta().getId());
    }

    private Transacao toEntity(TransacaoRequest request) {
        Transacao transacao = new Transacao();
        transacao.setDescricao(request.getDescricao());
        transacao.setValorTotal(request.getValor());
        transacao.setData(request.getData());
        transacao.setTipo(request.getTipo());
        transacao.setObservacoes(request.getObservacoes());
        transacao.setParcelado(request.getParcelado() != null ? request.getParcelado() : false);
        transacao.setTotalParcelas(request.getTotalParcelas());
        transacao.setRecorrente(request.getRecorrente() != null ? request.getRecorrente() : false);

        Categoria categoria = new Categoria();
        categoria.setId(request.getCategoriaIdNormalizada());
        transacao.setCategoria(categoria);

        if (request.getCartaoId() != null) {
            Conta cartao = new Conta();
            cartao.setId(request.getCartaoId());
            transacao.setConta(cartao);
        }

        if (request.getCarteiraId() != null) {
            Carteira carteira = new Carteira();
            carteira.setId(request.getCarteiraId());
            transacao.setCarteira(carteira);
        }

        return transacao;
    }
}

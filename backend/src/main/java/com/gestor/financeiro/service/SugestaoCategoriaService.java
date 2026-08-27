package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.CategoriaResumoDto;
import com.gestor.financeiro.dto.SugestaoCategoriaResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Sugestao deterministica de categoria (PR-F3-02, ampliada na Fase 4). Prioridade: regra do
 * titular; depois ultima
 * transacao do mesmo tipo com descricao normalizada igual; depois categoria
 * mais usada nos ultimos 90 dias para o mesmo tipo, empate por menor ID.
 * Read-only: nao cria categoria nem altera lancamento.
 */
@Service
public class SugestaoCategoriaService {

    /**
     * Janela de transacoes recentes varridas pelo criterio de descricao —
     * normalizacao com acentos/espacos nao e expressavel em JPQL portavel,
     * entao a comparacao acontece em memoria sobre projecao limitada.
     */
    static final int JANELA_DESCRICAO = 300;
    static final int DIAS_FREQUENCIA = 90;

    public static final String CRITERIO_REGRA_DO_TITULAR = "REGRA_DO_TITULAR";
    public static final String CRITERIO_DESCRICAO_IGUAL = "DESCRICAO_IGUAL";
    public static final String CRITERIO_MAIS_USADA_90_DIAS = "MAIS_USADA_90_DIAS";
    public static final String CRITERIO_NENHUMA = "NENHUMA";

    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final RegraCategoriaService regras;
    private final Clock clock;

    public SugestaoCategoriaService(TransacaoRepository transacaoRepository,
                                    CategoriaRepository categoriaRepository,
                                    RegraCategoriaService regras,
                                    Clock clock) {
        this.transacaoRepository = transacaoRepository;
        this.categoriaRepository = categoriaRepository;
        this.regras = regras;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SugestaoCategoriaResponse sugerir(Long usuarioId, String descricao, TipoTransacao tipo) {
        if (descricao == null || descricao.isBlank()) {
            throw new BusinessException("Descrição é obrigatória");
        }
        if (tipo == null) {
            throw new BusinessException("Tipo é obrigatório");
        }

        // Regra escrita pelo titular ganha da heuristica: ele ja disse o que quer.
        var porRegra = regras.aplicar(usuarioId, descricao, tipo);
        if (porRegra.isPresent()) {
            return new SugestaoCategoriaResponse(CRITERIO_REGRA_DO_TITULAR,
                    CategoriaResumoDto.fromEntity(porRegra.get()));
        }

        String alvo = normalizar(descricao);
        for (Object[] linha : transacaoRepository.findDescricoesRecentesComCategoria(
                usuarioId, tipo, PageRequest.of(0, JANELA_DESCRICAO))) {
            if (alvo.equals(normalizar((String) linha[0]))) {
                return resposta(CRITERIO_DESCRICAO_IGUAL, (Long) linha[1], usuarioId);
            }
        }

        LocalDate hoje = LocalDate.now(clock);
        List<Object[]> maisUsadas = transacaoRepository.contarCategoriasMaisUsadasNoPeriodo(
                usuarioId, tipo, hoje.minusDays(DIAS_FREQUENCIA), hoje, PageRequest.of(0, 1));
        if (!maisUsadas.isEmpty()) {
            return resposta(CRITERIO_MAIS_USADA_90_DIAS, (Long) maisUsadas.get(0)[0], usuarioId);
        }

        return new SugestaoCategoriaResponse(CRITERIO_NENHUMA, null);
    }

    private SugestaoCategoriaResponse resposta(String criterio, Long categoriaId, Long usuarioId) {
        return categoriaRepository.findByIdAndUsuarioId(categoriaId, usuarioId)
                .map(c -> new SugestaoCategoriaResponse(criterio, CategoriaResumoDto.fromEntity(c)))
                .orElseGet(() -> new SugestaoCategoriaResponse(CRITERIO_NENHUMA, null));
    }

    /** Trim, minusculas, espacos condensados e remocao de acentos. */
    public static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

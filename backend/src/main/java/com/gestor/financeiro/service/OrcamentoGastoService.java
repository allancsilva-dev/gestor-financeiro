package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gasto do mês por categoria, na visão de competência (ADR-0010/ADR-0014).
 *
 * <p>Existe como serviço próprio porque agora tem dois leitores — a tela de orçamento e o
 * fechamento mensal — e a regra de competência não pode ter duas implementações: no dia em que
 * divergissem, o número da tela deixaria de bater com o carry do mês seguinte.</p>
 */
@Service
public class OrcamentoGastoService {

    private final TransacaoRepository transacoes;
    private final CategoriaRepository categorias;

    public OrcamentoGastoService(TransacaoRepository transacoes, CategoriaRepository categorias) {
        this.transacoes = transacoes;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> porCategoria(Long usuarioId, YearMonth competencia) {
        LocalDate inicio = competencia.atDay(1);
        LocalDate fim = competencia.atEndOfMonth();

        List<Object[]> linhas = transacoes.sumValorEfetivoAgrupadoPorCategoria(
                usuarioId, TipoTransacao.SAIDA, inicio, fim);

        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (Object[] linha : linhas) {
            String categoriaNome = (String) linha[0];
            BigDecimal valor = (BigDecimal) linha[1];
            categorias.findByUsuarioIdAndNomeIgnoreCase(usuarioId, categoriaNome)
                    .ifPresent(categoria -> mapa.merge(categoria.getId(), valor, BigDecimal::add));
        }
        return mapa;
    }
}

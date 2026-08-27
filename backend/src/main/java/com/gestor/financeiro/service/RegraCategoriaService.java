package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.RegraCategoria;
import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.RegraCategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Regras de categorização do titular.
 *
 * <p>Vêm antes das heurísticas de {@link SugestaoCategoriaService}: quem já disse que "mercado da
 * esquina" é Alimentação não deve precisar repetir a cada lançamento.</p>
 *
 * <p>Limites deliberados:</p>
 * <ul>
 *   <li><b>Sem expressão regular.</b> Casamento é por texto normalizado (igual, começa com, contém).
 *       Regex do usuário roda no request e no worker, e Java não tem engine com garantia linear.</li>
 *   <li><b>Teto de regras por titular.</b> A avaliação é linear e acontece a cada lançamento e a
 *       cada linha importada; sem teto, um lote de 50 mil linhas multiplicaria o custo.</li>
 *   <li><b>Padrão mínimo de dois caracteres.</b> Padrão de um caractere casaria com quase tudo e
 *       transformaria a regra em ruído.</li>
 * </ul>
 */
@Service
public class RegraCategoriaService {

    private final RegraCategoriaRepository regras;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final int limitePorTitular;

    public RegraCategoriaService(RegraCategoriaRepository regras, CategoriaRepository categorias,
                                 UsuarioRepository usuarios,
                                 @Value("${app.categorizacao.max-regras:100}") int limitePorTitular) {
        this.regras = regras;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.limitePorTitular = Math.max(1, limitePorTitular);
    }

    @Transactional(readOnly = true)
    public List<RegraCategoria> listar(Long usuarioId) {
        return regras.ativasDoTitular(usuarioId);
    }

    /**
     * Primeira regra que casa, na ordem de prioridade. Nada casou: devolve vazio, e quem chama
     * segue para a heurística.
     */
    @Transactional(readOnly = true)
    public Optional<Categoria> aplicar(Long usuarioId, String descricao, TipoTransacao tipo) {
        return aplicar(regras.ativasDoTitular(usuarioId), descricao, tipo);
    }

    /**
     * Mesma decisão com as regras já em mãos. Importação de lote avalia milhares de linhas: buscar
     * as regras uma vez por linha transformaria uma consulta em dezenas de milhares.
     */
    public Optional<Categoria> aplicar(List<RegraCategoria> ativas, String descricao, TipoTransacao tipo) {
        if (descricao == null || descricao.isBlank()) return Optional.empty();
        String alvo = SugestaoCategoriaService.normalizar(descricao);

        for (RegraCategoria regra : ativas) {
            boolean tipoCompativel = regra.getTipoTransacao() == null || regra.getTipoTransacao() == tipo;
            if (tipoCompativel && regra.getTipoCasamento().casa(alvo, regra.getPadrao())) {
                return Optional.of(regra.getCategoria());
            }
        }
        return Optional.empty();
    }

    @Transactional
    public RegraCategoria criar(Long usuarioId, String padrao, TipoCasamentoRegra casamento,
                                TipoTransacao tipo, Long categoriaId, Integer prioridade) {
        String normalizado = normalizarPadrao(padrao);
        TipoCasamentoRegra tipoCasamento = casamento == null ? TipoCasamentoRegra.CONTEM : casamento;

        Categoria categoria = categorias.findByIdAndUsuarioId(categoriaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

        Optional<RegraCategoria> existente = regras
                .findByUsuarioIdAndPadraoAndTipoCasamentoAndTipoTransacao(usuarioId, normalizado, tipoCasamento, tipo);
        if (existente.isPresent()) {
            // Mesmo padrão e mesmo escopo: atualiza o destino em vez de acumular regra duplicada.
            RegraCategoria regra = existente.get();
            regra.setCategoria(categoria);
            regra.setAtiva(true);
            if (prioridade != null) regra.setPrioridade(validarPrioridade(prioridade));
            return regras.save(regra);
        }

        if (regras.countByUsuarioIdAndAtivaTrue(usuarioId) >= limitePorTitular) {
            throw new BusinessException("Limite de regras de categorização atingido");
        }

        RegraCategoria regra = new RegraCategoria();
        regra.setUsuario(usuarios.getReferenceById(usuarioId));
        regra.setCategoria(categoria);
        regra.setPadrao(normalizado);
        regra.setTipoCasamento(tipoCasamento);
        regra.setTipoTransacao(tipo);
        regra.setPrioridade(prioridade == null ? 100 : validarPrioridade(prioridade));
        regra.setAtiva(true);
        return regras.save(regra);
    }

    @Transactional
    public void remover(Long usuarioId, Long regraId) {
        RegraCategoria regra = regras.findByIdAndUsuarioId(regraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Regra não encontrada"));
        regras.delete(regra);
    }

    private String normalizarPadrao(String padrao) {
        if (padrao == null || padrao.isBlank()) {
            throw new BusinessException("Padrão é obrigatório");
        }
        String normalizado = SugestaoCategoriaService.normalizar(padrao);
        if (normalizado.length() < 2) {
            throw new BusinessException("Padrão precisa de pelo menos dois caracteres");
        }
        if (normalizado.length() > 120) {
            throw new BusinessException("Padrão excede o tamanho permitido");
        }
        return normalizado;
    }

    private short validarPrioridade(Integer prioridade) {
        if (prioridade < 1 || prioridade > 1000) {
            throw new BusinessException("Prioridade fora do intervalo permitido");
        }
        return prioridade.shortValue();
    }
}

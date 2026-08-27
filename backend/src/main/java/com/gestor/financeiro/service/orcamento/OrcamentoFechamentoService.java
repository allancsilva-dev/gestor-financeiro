package com.gestor.financeiro.service.orcamento;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.OrcamentoCategoria;
import com.gestor.financeiro.model.OrcamentoFechamento;
import com.gestor.financeiro.model.OrcamentoMensal;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.PoliticaRollover;
import com.gestor.financeiro.repository.OrcamentoCategoriaRepository;
import com.gestor.financeiro.repository.OrcamentoFechamentoRepository;
import com.gestor.financeiro.repository.OrcamentoMensalRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.OrcamentoGastoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fechamento mensal do orçamento.
 *
 * <p>Regras que este serviço existe para garantir (ADR-0010, ADR-0014):</p>
 * <ul>
 *   <li><b>Fechar é idempotente.</b> A competência tem índice único por titular e categoria;
 *       reexecutar o job não recalcula nem sobrescreve.</li>
 *   <li><b>Mês fechado não é reescrito.</b> Mudar a política hoje vale a partir da competência
 *       seguinte — o registro guarda a política e a versão da regra que valiam na hora.</li>
 *   <li><b>Nada é inventado.</b> O que passa adiante é o resultado (ou um dos lados dele) da conta
 *       {@code base + carryIn - gasto}, e o banco confere a aritmética.</li>
 * </ul>
 */
@Service
public class OrcamentoFechamentoService {

    private static final Logger log = LoggerFactory.getLogger(OrcamentoFechamentoService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final OrcamentoMensalRepository orcamentos;
    private final OrcamentoCategoriaRepository categorias;
    private final OrcamentoFechamentoRepository fechamentos;
    private final OrcamentoGastoService gastos;
    private final UsuarioRepository usuarios;

    public OrcamentoFechamentoService(OrcamentoMensalRepository orcamentos,
                                      OrcamentoCategoriaRepository categorias,
                                      OrcamentoFechamentoRepository fechamentos,
                                      OrcamentoGastoService gastos,
                                      UsuarioRepository usuarios) {
        this.orcamentos = orcamentos;
        this.categorias = categorias;
        this.fechamentos = fechamentos;
        this.gastos = gastos;
        this.usuarios = usuarios;
    }

    /**
     * O que a categoria leva para a competência informada, vindo do fechamento anterior.
     * Sem fechamento anterior, não há o que carregar — e não se inventa saldo.
     */
    @Transactional(readOnly = true)
    public BigDecimal carryIn(Long usuarioId, Long categoriaId, YearMonth competencia) {
        YearMonth anterior = competencia.minusMonths(1);
        return fechamentos.findByUsuarioIdAndCategoriaIdAndAnoAndMes(usuarioId, categoriaId,
                        (short) anterior.getYear(), (short) anterior.getMonthValue())
                .map(OrcamentoFechamento::getCarryOut)
                .orElse(ZERO);
    }

    /** Fecha a competência do titular. Devolve quantas categorias foram fechadas agora. */
    @Transactional
    public int fechar(Long usuarioId, YearMonth competencia) {
        short ano = (short) competencia.getYear();
        short mes = (short) competencia.getMonthValue();

        if (fechamentos.existsByUsuarioIdAndAnoAndMes(usuarioId, ano, mes)) {
            // Competência já fechada: reexecução de job não reabre mês nem recalcula carry.
            return 0;
        }

        Optional<OrcamentoMensal> orcamento = orcamentos.findByUsuarioIdAndMesAndAno(
                usuarioId, (int) mes, (int) ano);
        if (orcamento.isEmpty()) return 0;

        List<OrcamentoCategoria> limites = categorias.findByOrcamentoIdAndAtivoTrue(orcamento.get().getId());
        if (limites.isEmpty()) return 0;

        Usuario usuario = usuarios.getReferenceById(usuarioId);
        Map<Long, BigDecimal> gastoPorCategoria = gastos.porCategoria(usuarioId, competencia);
        int fechadas = 0;

        for (OrcamentoCategoria limite : limites) {
            Categoria categoria = limite.getCategoria();
            BigDecimal base = escala(limite.getValorLimite());
            BigDecimal carryIn = escala(carryIn(usuarioId, categoria.getId(), competencia));
            BigDecimal gasto = escala(gastoPorCategoria.getOrDefault(categoria.getId(), ZERO));
            BigDecimal resultado = base.add(carryIn).subtract(gasto);
            PoliticaRollover politica = limite.getPoliticaRollover() == null
                    ? PoliticaRollover.NONE : limite.getPoliticaRollover();

            OrcamentoFechamento fechamento = new OrcamentoFechamento();
            fechamento.setUsuario(usuario);
            fechamento.setCategoria(categoria);
            fechamento.setAno(ano);
            fechamento.setMes(mes);
            fechamento.setBase(base);
            fechamento.setCarryIn(carryIn);
            fechamento.setGasto(gasto);
            fechamento.setResultado(resultado);
            fechamento.setCarryOut(escala(politica.carregar(resultado)));
            fechamento.setPolitica(politica);
            fechamento.setRegraVersao(OrcamentoFechamento.REGRA_VERSAO_ATUAL);
            fechamentos.save(fechamento);
            fechadas++;
        }

        log.info("orcamento_fechado usuarioId={} competencia={} categorias={}", usuarioId, competencia, fechadas);
        return fechadas;
    }

    private BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}

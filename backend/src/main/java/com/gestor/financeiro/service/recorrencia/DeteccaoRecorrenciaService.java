package com.gestor.financeiro.service.recorrencia;

import com.gestor.financeiro.model.RecorrenciaCandidata;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusRecorrenciaCandidata;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.RecorrenciaCandidataRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.SugestaoCategoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Encontra o que se repete no histórico — assinatura, mensalidade, aluguel.
 *
 * <p>Como decide, e por quê cada limite existe:</p>
 * <ul>
 *   <li><b>Mesma descrição normalizada e mesmo tipo.</b> É o agrupamento que o usuário reconhece;
 *       "NETFLIX.COM" e "Netflix.com " são o mesmo serviço.</li>
 *   <li><b>Pelo menos três meses distintos.</b> Duas ocorrências podem ser coincidência; três em
 *       meses diferentes é padrão. Contar ocorrências em vez de meses acusaria compra parcelada no
 *       mesmo mês como recorrência.</li>
 *   <li><b>Valor estável.</b> Variação acima do teto derruba o grupo: conta de luz que triplica não
 *       é assinatura, e sugerir valor fixo ali seria mentira.</li>
 *   <li><b>Dia típico é a mediana.</b> Média puxaria o vencimento para o meio do mês quando uma
 *       cobrança atrasa.</li>
 *   <li><b>Nunca cria compromisso.</b> O resultado é sugestão; virar recorrência exige confirmação.
 *       Descartado não volta a ser sugerido.</li>
 * </ul>
 */
@Service
public class DeteccaoRecorrenciaService {

    private static final Logger log = LoggerFactory.getLogger(DeteccaoRecorrenciaService.class);

    private final TransacaoRepository transacoes;
    private final RecorrenciaCandidataRepository candidatas;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final int mesesDeJanela;
    private final int minimoDeMeses;
    private final BigDecimal variacaoMaxima;
    private final int maximoDeTransacoes;

    public DeteccaoRecorrenciaService(TransacaoRepository transacoes,
                                      RecorrenciaCandidataRepository candidatas,
                                      UsuarioRepository usuarios,
                                      Clock clock,
                                      @Value("${app.recorrencia.janela-meses:6}") int mesesDeJanela,
                                      @Value("${app.recorrencia.minimo-meses:3}") int minimoDeMeses,
                                      @Value("${app.recorrencia.variacao-maxima:0.20}") String variacaoMaxima,
                                      @Value("${app.recorrencia.max-transacoes:5000}") int maximoDeTransacoes) {
        this.transacoes = transacoes;
        this.candidatas = candidatas;
        this.usuarios = usuarios;
        this.clock = clock;
        this.mesesDeJanela = Math.max(2, mesesDeJanela);
        this.minimoDeMeses = Math.max(2, minimoDeMeses);
        this.variacaoMaxima = new BigDecimal(variacaoMaxima);
        this.maximoDeTransacoes = Math.max(100, maximoDeTransacoes);
    }

    /** Varre a janela recente e devolve quantos padrões novos foram sugeridos. */
    @Transactional
    public int detectar(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = hoje.minusMonths(mesesDeJanela).withDayOfMonth(1);

        List<Transacao> historico = transacoes.findByUsuarioIdAndDataBetween(usuarioId, inicio, hoje);
        if (historico.size() > maximoDeTransacoes) {
            // Janela grande demais: em vez de varrer sem limite, corta pelo mais recente.
            historico = historico.stream()
                    .sorted((a, b) -> b.getData().compareTo(a.getData()))
                    .limit(maximoDeTransacoes)
                    .toList();
        }

        Map<String, List<Transacao>> grupos = agrupar(historico);
        Usuario usuario = usuarios.getReferenceById(usuarioId);
        int novas = 0;

        for (List<Transacao> grupo : grupos.values()) {
            Padrao padrao = avaliar(grupo);
            if (padrao == null) continue;

            Transacao referencia = grupo.get(0);
            var existente = candidatas.findByUsuarioIdAndDescricaoNormalizadaAndTipo(
                    usuarioId, padrao.descricaoNormalizada(), referencia.getTipo());

            if (existente.isPresent()) {
                RecorrenciaCandidata candidata = existente.get();
                // Decisão do titular é definitiva: descartado não reaparece, confirmado não é mexido.
                if (candidata.getStatus() != StatusRecorrenciaCandidata.SUGERIDA) continue;
                aplicar(candidata, padrao, referencia);
                candidatas.save(candidata);
                continue;
            }

            RecorrenciaCandidata candidata = new RecorrenciaCandidata();
            candidata.setUsuario(usuario);
            candidata.setDescricaoNormalizada(padrao.descricaoNormalizada());
            candidata.setTipo(referencia.getTipo());
            aplicar(candidata, padrao, referencia);
            candidatas.save(candidata);
            novas++;
        }

        if (novas > 0) {
            log.info("recorrencia_detectada usuarioId={} novas={}", usuarioId, novas);
        }
        return novas;
    }

    private void aplicar(RecorrenciaCandidata candidata, Padrao padrao, Transacao referencia) {
        candidata.setDescricaoExibicao(padrao.descricaoExibicao());
        candidata.setCategoria(referencia.getCategoria());
        candidata.setValorMedio(padrao.valorMedio());
        candidata.setDiaTipico((short) padrao.diaTipico());
        candidata.setOcorrencias((short) padrao.ocorrencias());
        candidata.setPrimeiraData(padrao.primeira());
        candidata.setUltimaData(padrao.ultima());
    }

    private Map<String, List<Transacao>> agrupar(List<Transacao> historico) {
        Map<String, List<Transacao>> grupos = new LinkedHashMap<>();
        for (Transacao transacao : historico) {
            if (!Boolean.TRUE.equals(transacao.getAtiva())) continue;
            if (transacao.getDescricao() == null || transacao.getDescricao().isBlank()) continue;
            if (transacao.getValorTotal() == null || transacao.getValorTotal().signum() <= 0) continue;
            // Lançamento que já nasceu de recorrência não vira sugestão de recorrência.
            if (transacao.getContaFixa() != null) continue;

            String chave = SugestaoCategoriaService.normalizar(transacao.getDescricao())
                    + "|" + transacao.getTipo();
            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(transacao);
        }
        return grupos;
    }

    /** Devolve o padrão quando o grupo se qualifica, ou {@code null} quando não é repetição. */
    private Padrao avaliar(List<Transacao> grupo) {
        if (grupo.size() < minimoDeMeses) return null;

        Set<YearMonth> meses = new TreeSet<>();
        for (Transacao transacao : grupo) {
            meses.add(YearMonth.from(transacao.getData()));
        }
        if (meses.size() < minimoDeMeses) return null;

        List<BigDecimal> valores = grupo.stream().map(Transacao::getValorTotal).sorted().toList();
        BigDecimal menor = valores.get(0);
        BigDecimal maior = valores.get(valores.size() - 1);
        BigDecimal mediana = valores.get(valores.size() / 2);
        if (mediana.signum() <= 0) return null;

        BigDecimal variacao = maior.subtract(menor).divide(mediana, 4, RoundingMode.HALF_UP);
        if (variacao.compareTo(variacaoMaxima) > 0) return null;

        List<Integer> dias = grupo.stream().map(t -> t.getData().getDayOfMonth()).sorted().toList();
        int diaTipico = dias.get(dias.size() / 2);

        LocalDate primeira = grupo.stream().map(Transacao::getData).min(LocalDate::compareTo).orElseThrow();
        LocalDate ultima = grupo.stream().map(Transacao::getData).max(LocalDate::compareTo).orElseThrow();
        Transacao referencia = grupo.get(0);

        return new Padrao(
                SugestaoCategoriaService.normalizar(referencia.getDescricao()),
                referencia.getDescricao().trim(),
                mediana.setScale(2, RoundingMode.HALF_UP),
                diaTipico,
                meses.size(),
                primeira,
                ultima);
    }

    private record Padrao(String descricaoNormalizada, String descricaoExibicao, BigDecimal valorMedio,
                          int diaTipico, int ocorrencias, LocalDate primeira, LocalDate ultima) {
    }
}

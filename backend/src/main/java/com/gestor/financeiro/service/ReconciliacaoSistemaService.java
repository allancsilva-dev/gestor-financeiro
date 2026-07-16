package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse.Invariante;
import com.gestor.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliacaoSistemaService {
    private final UsuarioRepository usuarioRepository;
    private final ReconciliacaoGlobalService reconciliacaoGlobalService;
    private final ReconciliacaoObservabilidade observabilidade;
    private final Clock clock;

    @Value("${app.reconciliation.batch-size:200}")
    private int batchSize;

    public ReconciliacaoSistemaResultado executar() {
        Instant inicio = clock.instant();
        long startedNanos = System.nanoTime();
        log.info("reconciliacao_global_inicio");
        List<ReconciliacaoSistemaResultado.ResultadoUsuario> resultados = new ArrayList<>();
        Map<Invariante, long[]> totais = new EnumMap<>(Invariante.class);
        for (Invariante invariante : Invariante.values()) totais.put(invariante, new long[2]);

        long cursor = 0;
        long usuarios = 0;
        long checks = 0;
        long divergencias = 0;
        long erros = 0;
        while (true) {
            List<Long> ids;
            try {
                ids = usuarioRepository.findIdsAfter(cursor, PageRequest.of(0, Math.max(1, batchSize)));
            } catch (RuntimeException ex) {
                erros++;
                log.error("reconciliacao_global_erro_enumeracao erro={}", ex.getClass().getSimpleName());
                break;
            }
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                usuarios++;
                try {
                    ReconciliacaoGlobalResponse report = reconciliacaoGlobalService.reconciliarUsuario(usuarioId);
                    checks += report.verificacoes();
                    divergencias += report.divergencias();
                    for (ReconciliacaoGlobalResponse.ResumoInvariante resumo : report.resumo()) {
                        long[] total = totais.get(resumo.invariante());
                        total[0] += resumo.verificacoes();
                        total[1] += resumo.divergencias();
                    }
                    if (report.divergencias() > 0) {
                        resultados.add(new ReconciliacaoSistemaResultado.ResultadoUsuario(
                                usuarioId, report, false));
                    }
                } catch (RuntimeException ex) {
                    erros++;
                    resultados.add(new ReconciliacaoSistemaResultado.ResultadoUsuario(usuarioId, null, true));
                    log.error("reconciliacao_global_erro_usuario usuarioId={} erro={}",
                            usuarioId, ex.getClass().getSimpleName());
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < batchSize) break;
        }

        long duracaoMs = (System.nanoTime() - startedNanos) / 1_000_000;
        Map<Invariante, ReconciliacaoSistemaResultado.TotaisInvariante> porInvariante =
                new EnumMap<>(Invariante.class);
        totais.forEach((key, value) -> porInvariante.put(key,
                new ReconciliacaoSistemaResultado.TotaisInvariante(value[0], value[1])));
        ReconciliacaoSistemaResultado result = new ReconciliacaoSistemaResultado(inicio, duracaoMs,
                usuarios, checks, divergencias, erros, Map.copyOf(porInvariante), List.copyOf(resultados));
        observabilidade.registrar(result);
        if (divergencias > 0) {
            log.warn("reconciliacao_global_divergencias total={}", divergencias);
        }
        log.info("reconciliacao_global_fim duracaoMs={} usuarios={} verificacoes={} divergencias={} erros={}",
                duracaoMs, usuarios, checks, divergencias, erros);
        return result;
    }
}

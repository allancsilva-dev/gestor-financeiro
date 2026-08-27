package com.gestor.financeiro.service.recorrencia;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.RecorrenciaCandidata;
import com.gestor.financeiro.model.enums.StatusRecorrenciaCandidata;
import com.gestor.financeiro.repository.RecorrenciaCandidataRepository;
import com.gestor.financeiro.service.ContaFixaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Decisão do titular sobre os padrões detectados.
 *
 * <p>Confirmar cria a recorrência de verdade — sem execução automática: o padrão detectado diz que
 * algo <b>aconteceu</b> três vezes, não que o titular autoriza o app a lançar sozinho daqui em
 * diante. Ligar a automação continua sendo escolha explícita na tela de recorrências.</p>
 */
@Service
public class RecorrenciaCandidataService {

    private final RecorrenciaCandidataRepository candidatas;
    private final ContaFixaService contasFixas;
    private final Clock clock;

    public RecorrenciaCandidataService(RecorrenciaCandidataRepository candidatas,
                                       ContaFixaService contasFixas, Clock clock) {
        this.candidatas = candidatas;
        this.contasFixas = contasFixas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecorrenciaCandidata> sugeridas(Long usuarioId) {
        return candidatas.findByUsuarioIdAndStatusOrderByUltimaDataDesc(
                usuarioId, StatusRecorrenciaCandidata.SUGERIDA);
    }

    @Transactional
    public ContaFixa confirmar(Long usuarioId, Long candidataId) {
        RecorrenciaCandidata candidata = buscar(usuarioId, candidataId);
        if (candidata.getStatus() == StatusRecorrenciaCandidata.CONFIRMADA) {
            return candidata.getContaFixa();
        }
        if (candidata.getStatus() == StatusRecorrenciaCandidata.DESCARTADA) {
            throw new BusinessException("Sugestão já descartada");
        }

        ContaFixa contaFixa = new ContaFixa();
        contaFixa.setNome(candidata.getDescricaoExibicao());
        contaFixa.setValorPlanejado(candidata.getValorMedio());
        contaFixa.setDiaVencimento((int) candidata.getDiaTipico());
        contaFixa.setTipo(candidata.getTipo());
        contaFixa.setCategoria(candidata.getCategoria());
        contaFixa.setRecorrente(true);
        contaFixa.setAtivo(true);
        // Execução automática nasce desligada: detectar repetição não é autorização para lançar.
        contaFixa.setExecucaoAutomatica(false);
        contaFixa.setDataProximoVencimento(proximoVencimento(candidata.getDiaTipico()));

        ContaFixa criada = contasFixas.criar(contaFixa, usuarioId);

        candidata.setStatus(StatusRecorrenciaCandidata.CONFIRMADA);
        candidata.setContaFixa(criada);
        candidata.setDecididaEm(clock.instant());
        candidatas.save(candidata);
        return criada;
    }

    @Transactional
    public void descartar(Long usuarioId, Long candidataId) {
        RecorrenciaCandidata candidata = buscar(usuarioId, candidataId);
        if (candidata.getStatus() == StatusRecorrenciaCandidata.CONFIRMADA) {
            throw new BusinessException("Sugestão já virou recorrência");
        }
        candidata.setStatus(StatusRecorrenciaCandidata.DESCARTADA);
        candidata.setDecididaEm(clock.instant());
        candidatas.save(candidata);
    }

    /** Próxima ocorrência a partir de hoje, respeitando mês curto (dia 31 em fevereiro). */
    private LocalDate proximoVencimento(short diaTipico) {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mes = YearMonth.from(hoje);
        LocalDate desteMes = mes.atDay(Math.min(diaTipico, mes.lengthOfMonth()));
        if (!desteMes.isBefore(hoje)) return desteMes;
        YearMonth proximo = mes.plusMonths(1);
        return proximo.atDay(Math.min(diaTipico, proximo.lengthOfMonth()));
    }

    private RecorrenciaCandidata buscar(Long usuarioId, Long candidataId) {
        return candidatas.findByIdAndUsuarioId(candidataId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Sugestão não encontrada"));
    }
}

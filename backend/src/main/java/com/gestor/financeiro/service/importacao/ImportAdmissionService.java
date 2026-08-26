package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.exception.RateLimitExceededException;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.service.RateLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.Semaphore;

/**
 * Controle de admissão do upload de importação.
 *
 * <p>Três travas, cada uma para um risco diferente:</p>
 * <ul>
 *   <li><b>Rate limit por titular</b> — abuso de envio repetido;</li>
 *   <li><b>Lotes em voo por titular</b> — usuário disparando várias importações ao mesmo tempo;</li>
 *   <li><b>Parses simultâneos por instância</b> — o teto de memória. O buffer do parser é
 *       proporcional a {@code record-chars × csv-columns}, e o heap de produção é da ordem de
 *       500 MB: sem esta trava, requisições concorrentes derrubam o processo inteiro
 *       ({@code ExitOnOutOfMemoryError}), não só a importação.</li>
 * </ul>
 */
@Service
public class ImportAdmissionService {

    private static final EnumSet<ImportBatchStatus> EM_PROCESSAMENTO = EnumSet.of(ImportBatchStatus.RECEIVED);

    private final RateLimitService rateLimitService;
    private final ImportBatchRepository batchRepository;
    private final int limitePorJanela;
    private final Duration janela;
    private final int maximoEmVooPorUsuario;
    private final Duration janelaEmVoo;
    private final long esperaSugeridaSegundos;
    private final Semaphore parsesSimultaneos;

    public ImportAdmissionService(
            RateLimitService rateLimitService,
            ImportBatchRepository batchRepository,
            @Value("${app.import.admission.per-user-limit:10}") int limitePorJanela,
            @Value("${app.import.admission.window-seconds:60}") int janelaSegundos,
            @Value("${app.import.admission.max-in-flight-per-user:2}") int maximoEmVooPorUsuario,
            @Value("${app.import.admission.in-flight-window-minutes:15}") int janelaEmVooMinutos,
            @Value("${app.import.admission.max-concurrent-parses:2}") int maximoParsesSimultaneos) {
        this.rateLimitService = rateLimitService;
        this.batchRepository = batchRepository;
        this.limitePorJanela = limitePorJanela;
        this.janela = Duration.ofSeconds(janelaSegundos);
        this.maximoEmVooPorUsuario = maximoEmVooPorUsuario;
        this.janelaEmVoo = Duration.ofMinutes(janelaEmVooMinutos);
        this.esperaSugeridaSegundos = Math.max(1, janelaSegundos);
        this.parsesSimultaneos = new Semaphore(Math.max(1, maximoParsesSimultaneos), true);
    }

    /**
     * Admite a importação ou rejeita com 429. O retorno é um passe que precisa ser fechado pelo
     * chamador (try-with-resources) para devolver a vaga de parse.
     */
    public Passe admitir(Long usuarioId) {
        RateLimitService.RateLimitDecision decisao = rateLimitService.consume(
                "POST:/api/v1/importacoes|u:" + usuarioId, limitePorJanela, janela);
        if (!decisao.allowed()) {
            throw new RateLimitExceededException("Muitas importações em sequência; tente novamente em instantes",
                    decisao.retryAfterSeconds());
        }

        long emVoo = batchRepository.countByUsuarioIdAndStatusInAndCreatedAtAfter(
                usuarioId, EM_PROCESSAMENTO, Instant.now().minus(janelaEmVoo));
        if (emVoo >= maximoEmVooPorUsuario) {
            throw new RateLimitExceededException("Importação anterior ainda em processamento",
                    esperaSugeridaSegundos);
        }

        if (!parsesSimultaneos.tryAcquire()) {
            throw new RateLimitExceededException("Servidor processando outras importações; tente novamente",
                    esperaSugeridaSegundos);
        }
        return new Passe(parsesSimultaneos);
    }

    /** Vaga de parse; devolver é obrigatório, senão o teto de concorrência vaza. */
    public static final class Passe implements AutoCloseable {
        private final Semaphore origem;
        private boolean devolvido;

        private Passe(Semaphore origem) {
            this.origem = origem;
        }

        @Override
        public void close() {
            if (!devolvido) {
                devolvido = true;
                origem.release();
            }
        }
    }
}

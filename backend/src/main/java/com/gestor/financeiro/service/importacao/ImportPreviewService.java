package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.dto.ImportRecordPageResponse;
import com.gestor.financeiro.dto.ImportRecordResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportRecordRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Leitura da prévia de um lote, sempre pelo titular dono do lote. */
@Service
public class ImportPreviewService {

    private static final int TAMANHO_MAXIMO = 200;

    private final ImportBatchService batches;
    private final ImportRecordRepository records;

    public ImportPreviewService(ImportBatchService batches, ImportRecordRepository records) {
        this.batches = batches;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public ImportRecordPageResponse pagina(Long usuarioId, Long batchId, String status,
                                           int aposLinha, int tamanho) {
        // Resolve o lote primeiro: lote de outro titular responde 404, nunca lista vazia.
        batches.get(usuarioId, batchId);

        ImportRecordStatus filtro = statusValido(status);
        int limite = Math.min(Math.max(1, tamanho), TAMANHO_MAXIMO);
        int cursor = Math.max(0, aposLinha);

        List<ImportRecord> pagina = records.pagina(batchId, cursor, filtro, Limit.of(limite));
        List<ImportRecordResponse> resposta = pagina.stream().map(ImportRecordResponse::de).toList();
        // Cursor só existe quando a página encheu; página curta é o fim do lote.
        Integer proximaLinha = pagina.size() < limite ? null : pagina.get(pagina.size() - 1).getSourceLine();
        return new ImportRecordPageResponse(resposta, proximaLinha);
    }

    private ImportRecordStatus statusValido(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ImportRecordStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalido) {
            throw new BusinessException("Status de registro inválido");
        }
    }
}

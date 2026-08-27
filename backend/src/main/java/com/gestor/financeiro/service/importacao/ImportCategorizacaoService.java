package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.RegraCategoria;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.service.RegraCategoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Aplica as regras do titular às linhas do lote, ainda na prévia.
 *
 * <p>Fica antes da revisão de propósito: o usuário precisa <b>ver</b> em qual categoria cada linha
 * vai cair antes de confirmar, e não descobrir depois no extrato. Linha que nenhuma regra alcança
 * segue sem categoria — o pipeline não chuta.</p>
 */
@Service
public class ImportCategorizacaoService {

    private static final List<ImportRecordStatus> CATEGORIZAVEIS =
            List.of(ImportRecordStatus.VALID, ImportRecordStatus.PENDING_REVIEW, ImportRecordStatus.DUPLICATE);

    private final ImportRecordRepository records;
    private final RegraCategoriaService regras;
    private final int tamanhoDoBloco;

    public ImportCategorizacaoService(ImportRecordRepository records, RegraCategoriaService regras,
                                      @Value("${app.import.commit.chunk-size:200}") int tamanhoDoBloco) {
        this.records = records;
        this.regras = regras;
        this.tamanhoDoBloco = Math.max(1, tamanhoDoBloco);
    }

    /** Categoriza o que der e devolve quantas linhas ganharam categoria. */
    @Transactional
    public int categorizar(Long usuarioId, Long batchId) {
        List<RegraCategoria> ativas = regras.listar(usuarioId);
        if (ativas.isEmpty()) return 0;

        int cursor = 0;
        int categorizadas = 0;
        while (true) {
            List<ImportRecord> bloco = records.paginaParaLancamento(batchId, cursor, CATEGORIZAVEIS,
                    Limit.of(tamanhoDoBloco));
            if (bloco.isEmpty()) break;
            for (ImportRecord record : bloco) {
                cursor = record.getSourceLine();
                if (record.getCategoria() != null) continue;
                Optional<Categoria> categoria = regras.aplicar(ativas, record.getNormalizedDescription(),
                        record.getDirection());
                if (categoria.isPresent()) {
                    record.setCategoria(categoria.get());
                    records.save(record);
                    categorizadas++;
                }
            }
        }
        return categorizadas;
    }
}

package com.gestor.financeiro.service.importacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ImportLimits(long fileBytes, int detectionBytes, int records, int recordChars,
                           int fieldChars, int csvColumns, int ofxDepth, int ofxElements,
                           int ofxHeaderBytes, int stagingFlush) {
    public ImportLimits(
            @Value("${app.import.limits.file-bytes:10485760}") long fileBytes,
            @Value("${app.import.limits.detection-bytes:65536}") int detectionBytes,
            @Value("${app.import.limits.records:50000}") int records,
            @Value("${app.import.limits.record-chars:65536}") int recordChars,
            @Value("${app.import.limits.field-chars:8192}") int fieldChars,
            @Value("${app.import.limits.csv-columns:64}") int csvColumns,
            @Value("${app.import.limits.ofx-depth:32}") int ofxDepth,
            @Value("${app.import.limits.ofx-elements:500000}") int ofxElements,
            @Value("${app.import.limits.ofx-header-bytes:8192}") int ofxHeaderBytes,
            @Value("${app.import.limits.staging-flush:250}") int stagingFlush) {
        this.fileBytes = positive(fileBytes); this.detectionBytes = positive(detectionBytes);
        this.records = positive(records); this.recordChars = positive(recordChars);
        this.fieldChars = positive(fieldChars); this.csvColumns = positive(csvColumns);
        this.ofxDepth = positive(ofxDepth); this.ofxElements = positive(ofxElements);
        this.ofxHeaderBytes = positive(ofxHeaderBytes); this.stagingFlush = positive(stagingFlush);
    }
    private static int positive(int value) { if (value <= 0) throw new IllegalArgumentException("Limite inválido"); return value; }
    private static long positive(long value) { if (value <= 0) throw new IllegalArgumentException("Limite inválido"); return value; }
}

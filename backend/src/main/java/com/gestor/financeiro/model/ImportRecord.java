package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "import_records")
@Getter
@Setter
public class ImportRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "source_line", nullable = false)
    private int sourceLine;

    @Column(name = "external_id", length = 180)
    private String externalId;

    @Column(name = "record_fingerprint", nullable = false, length = 64)
    private String recordFingerprint;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "normalized_description", length = 500)
    private String normalizedDescription;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TipoTransacao direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ImportRecordStatus status;

    @Column(name = "reason_code", length = 80)
    private String reasonCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id")
    private Transacao transacao;

    @Version
    @Column(nullable = false)
    private long version;
}

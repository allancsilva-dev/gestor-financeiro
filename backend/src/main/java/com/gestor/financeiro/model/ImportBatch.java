package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportOrigin;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.math.BigDecimal;
import com.gestor.financeiro.model.enums.ImportBalanceReconciliation;

@Entity
@Table(name = "import_batches")
@Getter
@Setter
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ImportFormat format;

    /** Conta financeira de destino; obrigatória a partir de COMMITTING (CHECK na V49). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id")
    private Carteira carteira;

    /** Cartão de destino quando o lote é uma fatura. Exclusivo com {@link #carteira} (V55). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    /** Proveniência do lote. Nunca inferir a partir de {@link #format} (V68). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ImportOrigin origin = ImportOrigin.UPLOAD;

    @Column(name = "institution_code", length = 80)
    private String institutionCode;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ImportBatchStatus status = ImportBatchStatus.RECEIVED;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "valid_records", nullable = false)
    private int validRecords;

    @Column(name = "invalid_records", nullable = false)
    private int invalidRecords;

    @Column(name = "pending_review_records", nullable = false)
    private int pendingReviewRecords;

    @Column(name = "duplicate_records", nullable = false)
    private int duplicateRecords;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "declared_opening_balance", precision = 19, scale = 2)
    private BigDecimal declaredOpeningBalance;

    @Column(name = "declared_closing_balance", precision = 19, scale = 2)
    private BigDecimal declaredClosingBalance;

    @Column(name = "declared_movement_total", precision = 19, scale = 2)
    private BigDecimal declaredMovementTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_reconciliation", nullable = false, length = 12)
    private ImportBalanceReconciliation balanceReconciliation = ImportBalanceReconciliation.UNAVAILABLE;

    @Column(name = "balance_mismatch_acknowledged", nullable = false)
    private boolean balanceMismatchAcknowledged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

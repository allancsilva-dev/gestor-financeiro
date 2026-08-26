package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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

package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {
}

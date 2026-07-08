package com.gestor.financeiro.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ImportResultDto {
    private int total;
    private int importadas;
    private int ignoradas;
    private int erros;

    public void addImportada() { importadas++; total++; }
    public void addIgnorada() { ignoradas++; total++; }
    public void addErro() { erros++; total++; }
}

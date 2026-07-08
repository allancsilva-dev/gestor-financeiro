package com.gestor.financeiro.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AnexoResponse {
    private Long id;
    private String nome;
    private String tipo;
    private Long tamanho;
    private LocalDateTime dataUpload;
}

package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.ExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExecucaoRecorrenciaDto(
        Long id, LocalDate dataVencimento, StatusExecucaoRecorrencia status,
        LocalDateTime tentadoEm, String mensagemFalha, ContaFixaResponseDto recorrencia) {
    public static ExecucaoRecorrenciaDto fromEntity(ExecucaoRecorrencia e) {
        return new ExecucaoRecorrenciaDto(e.getId(), e.getDataVencimento(), e.getStatus(),
                e.getTentadoEm(), e.getMensagemFalha(), ContaFixaResponseDto.fromEntity(e.getContaFixa()));
    }
}

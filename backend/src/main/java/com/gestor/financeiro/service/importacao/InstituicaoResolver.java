package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.InstituicaoFinanceira;
import com.gestor.financeiro.repository.InstituicaoFinanceiraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Traduz o código de instituição que a fonte declarou para a instituição canônica do catálogo.
 *
 * <p>Degrada de propósito: catálogo vazio, ou código desconhecido, devolve vazio e a deduplicação
 * volta a casar por texto, como antes da Fase 6. Isso mantém a importação funcionando em qualquer
 * ambiente onde ninguém populou o catálogo — inclusive o de desenvolvimento.</p>
 */
@Service
public class InstituicaoResolver {

    private final InstituicaoFinanceiraRepository instituicoes;

    public InstituicaoResolver(InstituicaoFinanceiraRepository instituicoes) {
        this.instituicoes = instituicoes;
    }

    @Transactional(readOnly = true)
    public Optional<InstituicaoFinanceira> resolver(String codigoDeclarado) {
        if (codigoDeclarado == null || codigoDeclarado.isBlank()) return Optional.empty();
        String codigo = codigoDeclarado.trim().toUpperCase(java.util.Locale.ROOT);
        Optional<InstituicaoFinanceira> direta = instituicoes.findByCodigoAndAtivaTrue(codigo);
        return direta.isPresent() ? direta : instituicoes.findByAlias(codigo);
    }
}

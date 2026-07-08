package com.gestor.financeiro.service;

import com.gestor.financeiro.model.*;
import com.gestor.financeiro.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

@Service
public class ExportService {

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MetaRepository metaRepository;

    @Autowired
    private ContaFixaRepository contaFixaRepository;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String exportarTransacoesCsv(Long usuarioId, LocalDate inicio, LocalDate fim) {
        if (inicio == null) inicio = LocalDate.of(2000, 1, 1);
        if (fim == null) fim = LocalDate.now();

        List<Transacao> transacoes = transacaoRepository
                .findByUsuarioIdAndDataBetween(usuarioId, inicio, fim);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Descrição,Valor,Tipo,Data,Categoria,Conta,Parcelado,Observações\n");

        for (Transacao t : transacoes) {
            csv.append(t.getId()).append(",");
            csv.append(escapeCsv(t.getDescricao())).append(",");
            csv.append(t.getValorTotal() != null ? t.getValorTotal() : "0").append(",");
            csv.append(t.getTipo() != null ? t.getTipo().getDescricao() : "").append(",");
            csv.append(t.getData() != null ? t.getData().format(DF) : "").append(",");
            csv.append(t.getCategoria() != null ? escapeCsv(t.getCategoria().getNome()) : "").append(",");
            csv.append(t.getConta() != null ? escapeCsv(t.getConta().getNome()) : "").append(",");
            csv.append(Boolean.TRUE.equals(t.getParcelado()) ? "Sim" : "Não").append(",");
            csv.append(t.getObservacoes() != null ? escapeCsv(t.getObservacoes()) : "").append("\n");
        }

        return csv.toString();
    }

    public String exportarCategoriasCsv(Long usuarioId) {
        List<Categoria> categorias = categoriaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Cor,Ícone,Valor Esperado,Valor Gasto,Ativo\n");

        for (Categoria c : categorias) {
            csv.append(c.getId()).append(",");
            csv.append(escapeCsv(c.getNome())).append(",");
            csv.append(c.getCor() != null ? c.getCor() : "").append(",");
            csv.append(c.getIcone() != null ? c.getIcone() : "").append(",");
            csv.append(c.getValorEsperado() != null ? c.getValorEsperado() : "0").append(",");
            csv.append(c.getValorGasto() != null ? c.getValorGasto() : "0").append(",");
            csv.append(Boolean.TRUE.equals(c.getAtivo()) ? "Sim" : "Não").append("\n");
        }

        return csv.toString();
    }

    public String exportarContasCsv(Long usuarioId) {
        List<Conta> contas = contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Tipo,Limite,Valor Gasto,Saldo Atual,Dia Fechamento,Dia Vencimento,Ativo\n");

        for (Conta c : contas) {
            csv.append(c.getId()).append(",");
            csv.append(escapeCsv(c.getNome())).append(",");
            csv.append(c.getTipo() != null ? c.getTipo().getDescricao() : "").append(",");
            csv.append(c.getLimiteTotal() != null ? c.getLimiteTotal() : "0").append(",");
            csv.append(c.getValorGasto() != null ? c.getValorGasto() : "0").append(",");
            csv.append(c.getSaldoAtual() != null ? c.getSaldoAtual() : "0").append(",");
            csv.append(c.getDiaFechamento() != null ? c.getDiaFechamento() : "").append(",");
            csv.append(c.getDiaVencimento() != null ? c.getDiaVencimento() : "").append(",");
            csv.append(Boolean.TRUE.equals(c.getAtivo()) ? "Sim" : "Não").append("\n");
        }

        return csv.toString();
    }

    public String exportarCompletoCsv(Long usuarioId) {
        StringJoiner sj = new StringJoiner("\n\n");
        sj.add("=== TRANSAÇÕES ===\n" + exportarTransacoesCsv(usuarioId, null, null));
        sj.add("=== CATEGORIAS ===\n" + exportarCategoriasCsv(usuarioId));
        sj.add("=== CONTAS ===\n" + exportarContasCsv(usuarioId));
        return sj.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

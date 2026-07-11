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

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    /**
     * Exportação completa dos dados do titular (LGPD art. 18, V - portabilidade).
     */
    public String exportarCompletoCsv(Long usuarioId) {
        StringJoiner sj = new StringJoiner("\n\n");
        sj.add("=== DADOS CADASTRAIS ===\n" + exportarCadastroCsv(usuarioId));
        sj.add("=== TRANSAÇÕES ===\n" + exportarTransacoesCsv(usuarioId, null, null));
        sj.add("=== CATEGORIAS ===\n" + exportarCategoriasCsv(usuarioId));
        sj.add("=== CONTAS ===\n" + exportarContasCsv(usuarioId));
        sj.add("=== CARTEIRAS ===\n" + exportarCarteirasCsv(usuarioId));
        sj.add("=== METAS ===\n" + exportarMetasCsv(usuarioId));
        sj.add("=== CONTAS FIXAS ===\n" + exportarContasFixasCsv(usuarioId));
        return sj.toString();
    }

    private String exportarCadastroCsv(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        StringBuilder csv = new StringBuilder();
        csv.append("Nome,Email\n");
        csv.append(escapeCsv(usuario.getNome())).append(",");
        csv.append(escapeCsv(usuario.getEmail())).append("\n");
        return csv.toString();
    }

    private String exportarCarteirasCsv(Long usuarioId) {
        List<Carteira> carteiras = carteiraRepository.findByUsuarioId(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Tipo,Saldo,Banco\n");

        for (Carteira c : carteiras) {
            csv.append(c.getId()).append(",");
            csv.append(escapeCsv(c.getNome())).append(",");
            csv.append(c.getTipo() != null ? c.getTipo() : "").append(",");
            csv.append(c.getSaldo() != null ? c.getSaldo() : "0").append(",");
            csv.append(c.getBanco() != null ? escapeCsv(c.getBanco()) : "").append("\n");
        }

        return csv.toString();
    }

    private String exportarMetasCsv(Long usuarioId) {
        List<Meta> metas = metaRepository.findByUsuarioId(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Descrição,Valor Total,Valor Reservado,Valor Mensal,Data Início,Data Prevista,Data Conclusão,Ativa\n");

        for (Meta m : metas) {
            csv.append(m.getId()).append(",");
            csv.append(escapeCsv(m.getNome())).append(",");
            csv.append(m.getDescricao() != null ? escapeCsv(m.getDescricao()) : "").append(",");
            csv.append(m.getValorTotal() != null ? m.getValorTotal() : "0").append(",");
            csv.append(m.getValorReservado() != null ? m.getValorReservado() : "0").append(",");
            csv.append(m.getValorMensal() != null ? m.getValorMensal() : "0").append(",");
            csv.append(m.getDataInicio() != null ? m.getDataInicio().format(DF) : "").append(",");
            csv.append(m.getDataPrevista() != null ? m.getDataPrevista().format(DF) : "").append(",");
            csv.append(m.getDataConclusao() != null ? m.getDataConclusao().format(DF) : "").append(",");
            csv.append(Boolean.TRUE.equals(m.getAtiva()) ? "Sim" : "Não").append("\n");
        }

        return csv.toString();
    }

    private String exportarContasFixasCsv(Long usuarioId) {
        List<ContaFixa> contasFixas = contaFixaRepository.findByUsuarioId(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Categoria,Valor Planejado,Valor Real,Dia Vencimento,Status,Recorrente,Ativo,Observações\n");

        for (ContaFixa cf : contasFixas) {
            csv.append(cf.getId()).append(",");
            csv.append(escapeCsv(cf.getNome())).append(",");
            csv.append(cf.getCategoria() != null ? escapeCsv(cf.getCategoria().getNome()) : "").append(",");
            csv.append(cf.getValorPlanejado() != null ? cf.getValorPlanejado() : "0").append(",");
            csv.append(cf.getValorReal() != null ? cf.getValorReal() : "0").append(",");
            csv.append(cf.getDiaVencimento() != null ? cf.getDiaVencimento() : "").append(",");
            csv.append(cf.getStatus() != null ? cf.getStatus() : "").append(",");
            csv.append(Boolean.TRUE.equals(cf.getRecorrente()) ? "Sim" : "Não").append(",");
            csv.append(Boolean.TRUE.equals(cf.getAtivo()) ? "Sim" : "Não").append(",");
            csv.append(cf.getObservacoes() != null ? escapeCsv(cf.getObservacoes()) : "").append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ExportService {
    private final java.time.Clock clock;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final CarteiraRepository carteiraRepository;
    private final MetaRepository metaRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final UsuarioRepository usuarioRepository;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public record AssistantExportTable(String table, String sql) { }
    public static final List<AssistantExportTable> ASSISTANT_EXPORT_TABLES = List.of(
        new AssistantExportTable("assistant_conversations", "select 'CONVERSA' tipo, id, channel papel, '' conteudo, created_at from assistant_conversations where usuario_id = ?"),
        new AssistantExportTable("assistant_messages", "select 'MENSAGEM' tipo, id, role papel, "
                + "concat(content, case when request_hash is null then '' else concat(' | request_hash=', request_hash) end, "
                + "case when response_json is null then '' else concat(' | response_json=', response_json) end) conteudo, "
                + "created_at from assistant_messages where usuario_id = ?"),
        new AssistantExportTable("assistant_drafts", "select 'RASCUNHO' tipo, id, status papel, input_hash conteudo, created_at from assistant_drafts where usuario_id = ?"),
        new AssistantExportTable("assistant_invocations", "select 'INVOCACAO' tipo, id, provider papel, "
                + "concat('result=', result, case when request_hash is null then '' else concat(' | request_hash=', request_hash) end, "
                + "case when response_json is null then '' else concat(' | response_json=', response_json) end) conteudo, "
                + "created_at from assistant_invocations where usuario_id = ?"),
        new AssistantExportTable("assistant_confirmations", "select 'CONFIRMACAO' tipo, id, provider papel, input_hash conteudo, created_at from assistant_confirmations where usuario_id = ?"),
        new AssistantExportTable("assistant_recommendations", "select 'RECOMENDACAO' tipo, id, rule_code papel, facts_json conteudo, created_at from assistant_recommendations where usuario_id = ?"),
        new AssistantExportTable("assistant_channel_events", "select 'EVENTO_CANAL' tipo, id, channel papel, payload_hash conteudo, received_at created_at from assistant_channel_events where usuario_id = ?"),
        new AssistantExportTable("assistant_whatsapp_links", "select 'VINCULO_WHATSAPP' tipo, id, wa_key_version papel, coalesce(wa_hmac, code_hash) conteudo, created_at from assistant_whatsapp_links where usuario_id = ?"),
        new AssistantExportTable("assistant_usage_daily", "select 'USO_DIARIO' tipo, id, 'EXTERNAL_CALLS' papel, cast(external_calls as varchar) conteudo, cast(usage_date as timestamp) created_at from assistant_usage_daily where usuario_id = ?")
    );

    public String exportarTransacoesCsv(Long usuarioId, LocalDate inicio, LocalDate fim) {
        if (inicio == null) inicio = LocalDate.of(2000, 1, 1);
        if (fim == null) fim = LocalDate.now(clock);

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
            csv.append(escapeCsv(c.getCor())).append(",");
            csv.append(escapeCsv(c.getIcone())).append(",");
            csv.append(c.getValorEsperado() != null ? c.getValorEsperado() : "0").append(",");
            csv.append(c.getValorGasto() != null ? c.getValorGasto() : "0").append(",");
            csv.append(Boolean.TRUE.equals(c.getAtivo()) ? "Sim" : "Não").append("\n");
        }

        return csv.toString();
    }

    public String exportarContasCsv(Long usuarioId) {
        List<Conta> contas = contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        // Contract V41: toda conta e cartao; a divida vive no ledger PASSIVO
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Limite,Divida,Dia Fechamento,Dia Vencimento,Ativo\n");

        for (Conta c : contas) {
            csv.append(c.getId()).append(",");
            csv.append(escapeCsv(c.getNome())).append(",");
            csv.append(c.getLimiteTotal() != null ? c.getLimiteTotal() : "0").append(",");
            csv.append(c.getContaFinanceira() != null && c.getContaFinanceira().getSaldo() != null
                    ? c.getContaFinanceira().getSaldo() : "0").append(",");
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
        sj.add("=== ASSISTENTE — CONVERSAS E MENSAGENS ===\n" + exportarAssistente(usuarioId));
        return sj.toString();
    }

    /** Exporta conteúdo e proveniência; hashes continuam classificados como dado pseudonimizado. */
    private String exportarAssistente(Long usuarioId) {
        StringBuilder csv = new StringBuilder("Tipo,ID,Canal/Papel,Conteúdo/Hash,Criado em\n");
        for (AssistantExportTable export : ASSISTANT_EXPORT_TABLES) {
            jdbcTemplate.query(export.sql(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> csv.append(rs.getString("tipo")).append(',')
                    .append(rs.getLong("id")).append(',')
                    .append(escapeCsv(rs.getString("papel"))).append(',')
                    .append(escapeCsv(rs.getString("conteudo"))).append(',')
                    .append(rs.getTimestamp("created_at")).append('\n'),
                usuarioId);
        }
        return csv.toString();
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
        csv.append("ID,Nome,Natureza,Subtipo,Saldo,Banco,Principal\n");

        for (Carteira c : carteiras) {
            csv.append(c.getId()).append(",");
            csv.append(escapeCsv(c.getNome())).append(",");
            csv.append(c.getNatureza() != null ? c.getNatureza() : "").append(",");
            csv.append(c.getSubtipo() != null ? c.getSubtipo() : "").append(",");
            csv.append(c.getSaldo() != null ? c.getSaldo() : "0").append(",");
            csv.append(c.getBanco() != null ? escapeCsv(c.getBanco()) : "").append(",");
            csv.append(c.isPrincipal() ? "Sim" : "Não").append("\n");
        }

        return csv.toString();
    }

    private String exportarMetasCsv(Long usuarioId) {
        List<Meta> metas = metaRepository.findByUsuarioId(usuarioId);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Nome,Descrição,Valor Total,Valor Reservado,Valor Mensal,Data Início,Data Prevista,Data Conclusão,Status,Ativa\n");

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
            csv.append(m.getStatus() != null ? m.getStatus() : "").append(",");
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

    static String escapeCsv(String value) {
        if (value == null) return "";
        String safe = neutralizeSpreadsheetFormula(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String neutralizeSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == ' ') {
            index++;
        }
        if (index >= value.length()) return value;
        char first = value.charAt(index);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }
}

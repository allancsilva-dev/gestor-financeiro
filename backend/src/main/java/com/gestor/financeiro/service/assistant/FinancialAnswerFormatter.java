package com.gestor.financeiro.service.assistant;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Component
public class FinancialAnswerFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    public String format(FinancialToolResult result) {
        Map<String, Object> f = result.facts();
        String answer = switch (result.intent()) {
            case BALANCE -> "Disponível agora: " + money(f.get("disponivelAgora"))
                    + ". Disponível para gastar: " + money(f.get("disponivelParaGastar"))
                    + ". Patrimônio líquido: " + money(f.get("patrimonioLiquido")) + ".";
            case SPENDING_BY_CATEGORY -> "O resultado mensal está em " + money(f.get("resultadoMensal"))
                    + ". Abra a composição para conferir categorias e lançamentos.";
            case BUDGET -> "Orçamento planejado: " + money(f.get("planejado"))
                    + ". Gasto até agora: " + money(f.get("gasto")) + ".";
            case GOALS -> "Você tem " + f.get("metasAtivas") + " meta(s) ativa(s), com "
                    + money(f.get("reservado")) + " reservado(s) de " + money(f.get("objetivoTotal")) + ".";
            case INVOICES -> "Dívidas de cartão consideradas nas métricas: " + money(f.get("dividas"))
                    + ". Abra Faturas para detalhar por cartão.";
            case COMMITMENTS -> "Há " + f.get("quantidade") + " compromisso(s), totalizando "
                    + money(f.get("totalComprometido")) + ".";
            case INVESTMENTS -> "Há " + f.get("ativos") + " ativo(s), com valor de mercado cotado de "
                    + money(f.get("valorMercadoCotado")) + ".";
        };
        String provenance = " Competência: " + result.competenceFrom().format(DATE) + " a "
                + result.competenceTo().format(DATE) + ". Atualizado em "
                + result.updatedAt().atZone(BUSINESS_ZONE).format(TIME) + ". Fonte: " + result.sourceRoute() + ".";
        return answer + (result.reconciled() ? "" : " Atenção: " + result.caveat()) + provenance;
    }

    private String money(Object value) {
        BigDecimal amount = value instanceof BigDecimal b ? b : BigDecimal.ZERO;
        return java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount);
    }
}

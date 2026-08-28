package com.gestor.financeiro.service.importacao;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class DeclaredBalanceParser {
    private DeclaredBalanceParser() { }

    static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().replace(" ", "").replace("R$", "");
        if (!value.matches("[+-]?[0-9.,]+")) return null;
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            char decimal = comma > dot ? ',' : '.';
            value = value.replace(String.valueOf(decimal == ',' ? '.' : ','), "")
                    .replace(decimal, '.');
        } else if (comma >= 0) {
            value = value.replace(',', '.');
        }
        try { return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException | NumberFormatException invalid) { return null; }
    }
}

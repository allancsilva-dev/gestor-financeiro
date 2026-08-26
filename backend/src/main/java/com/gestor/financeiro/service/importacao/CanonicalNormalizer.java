package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportRecordReasonCode;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Component
public final class CanonicalNormalizer {
    private static final String VERSION = "import-fingerprint-v1";
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public CanonicalImportRecord normalize(int line, String institution, String externalId, String date,
                                           String description, String amount, String currency, String direction) {
        List<ImportRecordReasonCode> issues = new ArrayList<>();
        String cleanDescription = text(description, 500, true);
        if (cleanDescription == null) issues.add(description == null || description.isBlank()
                ? ImportRecordReasonCode.DESCRIPTION_MISSING : ImportRecordReasonCode.DESCRIPTION_INVALID);
        String cleanExternalId = text(externalId, 180, false);
        if (externalId != null && !externalId.isBlank() && cleanExternalId == null) issues.add(ImportRecordReasonCode.EXTERNAL_ID_INVALID);
        LocalDate occurredOn = parseDate(date, issues);
        Amount parsedAmount = parseAmount(amount, issues);
        String isoCurrency = parseCurrency(currency, issues);
        TipoTransacao parsedDirection = parseDirection(direction, parsedAmount.sign(), issues);
        if (parsedDirection == null && parsedAmount.value() != null && parsedAmount.sign() != 0) {
            parsedDirection = parsedAmount.sign() < 0 ? TipoTransacao.SAIDA : TipoTransacao.ENTRADA;
        }
        BigDecimal magnitude = parsedAmount.value() == null ? null : parsedAmount.value().abs();
        ImportRecordStatus status = issues.isEmpty() ? ImportRecordStatus.VALID
                : issues.stream().anyMatch(this::fatal) ? ImportRecordStatus.INVALID : ImportRecordStatus.PENDING_REVIEW;
        ImportRecordReasonCode reason = issues.size() == 1 ? issues.get(0)
                : issues.isEmpty() ? null : ImportRecordReasonCode.MULTIPLE_ISSUES;
        String fingerprint = fingerprint(institution, cleanExternalId, occurredOn, magnitude,
                isoCurrency, parsedDirection, cleanDescription);
        return new CanonicalImportRecord(line, cleanExternalId, fingerprint, occurredOn, cleanDescription,
                magnitude, isoCurrency, parsedDirection, status, reason);
    }

    private LocalDate parseDate(String raw, List<ImportRecordReasonCode> issues) {
        if (raw == null || raw.isBlank()) { issues.add(ImportRecordReasonCode.DATE_MISSING); return null; }
        String value = text(raw, 64, false);
        if (value == null) { issues.add(ImportRecordReasonCode.DATE_INVALID); return null; }
        try { return LocalDate.parse(value); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value, BR_DATE); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(value).toLocalDate(); } catch (DateTimeParseException ignored) { }
        if (value.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) issues.add(ImportRecordReasonCode.DATE_AMBIGUOUS);
        else issues.add(ImportRecordReasonCode.DATE_INVALID);
        return null;
    }

    private Amount parseAmount(String raw, List<ImportRecordReasonCode> issues) {
        if (raw == null || raw.isBlank()) { issues.add(ImportRecordReasonCode.AMOUNT_MISSING); return new Amount(null, 0); }
        String value = text(raw, 80, false);
        if (value == null || !value.matches("[+-]?[0-9.,]+")) { issues.add(ImportRecordReasonCode.AMOUNT_INVALID); return new Amount(null, 0); }
        int comma = value.lastIndexOf(','); int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            char decimal = comma > dot ? ',' : '.';
            char group = decimal == ',' ? '.' : ',';
            if (!validGrouping(value, group, decimal)) { issues.add(ImportRecordReasonCode.AMOUNT_AMBIGUOUS); return new Amount(null, 0); }
            value = value.replace(String.valueOf(group), "").replace(decimal, '.');
        } else if (comma >= 0) value = singleSeparator(value, ',', issues);
        else if (dot >= 0) value = singleSeparator(value, '.', issues);
        if (value == null) return new Amount(null, 0);
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.signum() == 0) { issues.add(ImportRecordReasonCode.AMOUNT_INVALID); return new Amount(null, 0); }
            if (result.scale() > 2 && result.setScale(2, RoundingMode.UNNECESSARY) == null) return new Amount(null, 0);
            return new Amount(result.setScale(2, RoundingMode.UNNECESSARY), result.signum());
        } catch (ArithmeticException e) { issues.add(ImportRecordReasonCode.AMOUNT_ROUNDING_REQUIRED); return new Amount(null, 0); }
        catch (NumberFormatException e) { issues.add(ImportRecordReasonCode.AMOUNT_INVALID); return new Amount(null, 0); }
    }

    private String singleSeparator(String value, char separator, List<ImportRecordReasonCode> issues) {
        int count = value.length() - value.replace(String.valueOf(separator), "").length();
        int decimals = value.length() - value.lastIndexOf(separator) - 1;
        if (count == 1 && decimals >= 1 && decimals <= 2) return value.replace(separator, '.');
        if (count > 1 && decimals == 3 && validGrouping(value, separator, '\0')) return value.replace(String.valueOf(separator), "");
        issues.add(ImportRecordReasonCode.AMOUNT_AMBIGUOUS); return null;
    }

    private boolean validGrouping(String value, char group, char decimal) {
        String unsigned = value.replaceFirst("^[+-]", "");
        String integer = decimal == '\0' ? unsigned : unsigned.substring(0, unsigned.lastIndexOf(decimal));
        String[] groups = integer.split("\\" + group, -1);
        if (groups.length == 1) return true;
        if (groups[0].isEmpty() || groups[0].length() > 3) return false;
        for (int i = 1; i < groups.length; i++) if (groups[i].length() != 3) return false;
        return true;
    }

    private String parseCurrency(String raw, List<ImportRecordReasonCode> issues) {
        if (raw == null || raw.isBlank()) { issues.add(ImportRecordReasonCode.CURRENCY_MISSING); return null; }
        String value = text(raw, 3, false);
        if (value == null) { issues.add(ImportRecordReasonCode.CURRENCY_INVALID); return null; }
        value = value.toUpperCase(Locale.ROOT);
        try { return Currency.getInstance(value).getCurrencyCode(); }
        catch (IllegalArgumentException e) { issues.add(ImportRecordReasonCode.CURRENCY_INVALID); return null; }
    }

    private TipoTransacao parseDirection(String raw, int sign, List<ImportRecordReasonCode> issues) {
        if (raw == null || raw.isBlank()) {
            // Sem direção e sem sinal não há como decidir; com sinal o chamador infere.
            if (sign == 0) issues.add(ImportRecordReasonCode.DIRECTION_MISSING);
            return null;
        }
        String value = text(raw, 20, false);
        TipoTransacao explicito = switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "ENTRADA", "CREDIT", "CREDITO", "CRÉDITO" -> TipoTransacao.ENTRADA;
            case "SAIDA", "SAÍDA", "DEBIT", "DEBITO", "DÉBITO" -> TipoTransacao.SAIDA;
            default -> null;
        };
        if (explicito == null) {
            // TRNTYPE de OFX (POS, ATM, XFER, PAYMENT, DEP, FEE, INT...) é descritivo: quem
            // decide a direção é o sinal de TRNAMT. Sem sinal, o valor é inaproveitável.
            if (sign == 0) issues.add(ImportRecordReasonCode.DIRECTION_INVALID);
            return null;
        }
        if (sign != 0 && (sign > 0) != (explicito == TipoTransacao.ENTRADA)) {
            issues.add(ImportRecordReasonCode.DIRECTION_CONFLICT);
        }
        return explicito;
    }

    private String text(String raw, int max, boolean condense) {
        if (raw == null) return null;
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        if (condense) value = value.replaceAll("\\s+", " ");
        return value.isBlank() || value.length() > max ? null : value;
    }

    private boolean fatal(ImportRecordReasonCode reason) {
        return reason == ImportRecordReasonCode.AMOUNT_INVALID || reason == ImportRecordReasonCode.AMOUNT_MISSING;
    }

    private String fingerprint(String institution, String externalId, LocalDate date, BigDecimal amount,
                               String currency, TipoTransacao direction, String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            part(digest, VERSION);
            if (externalId != null) { part(digest, institution); part(digest, externalId); }
            else { part(digest, date); part(digest, amount); part(digest, currency); part(digest, direction); part(digest, description); }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new IllegalStateException("SHA-256 indisponível", e); }
    }
    private void part(MessageDigest digest, Object value) {
        byte[] bytes = String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
    private record Amount(BigDecimal value, int sign) { }
}

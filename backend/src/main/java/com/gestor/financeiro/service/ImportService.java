package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );
    private final TransacaoService transacaoService;
    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaRepository contaRepository;
    private final CarteiraRepository carteiraRepository;

    // Sem @Transactional aqui de propósito: cada linha importa na transação própria
    // de TransacaoService.criar(), para uma linha inválida não reverter as demais.
    public ImportResultDto importarCsv(Long usuarioId, MultipartFile file, Long carteiraPadraoId) {
        ImportResultDto result = new ImportResultDto();

        Carteira carteiraPadrao = null;
        if (carteiraPadraoId != null) {
            carteiraPadrao = carteiraRepository.findByIdAndUsuarioId(carteiraPadraoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return result;
            }

            Map<String, Integer> columnMap = parseHeader(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    String[] fields = parseCsvLine(line);
                    Transacao tx = buildTransacao(usuarioId, fields, columnMap, carteiraPadrao);
                    if (tx == null || tx.getStatus() == StatusPagamento.CANCELADO) {
                        result.addIgnorada();
                        continue;
                    }
                    if (transacaoRepository
                            .existsByUsuarioIdAndDataAndDescricaoIgnoreCaseAndValorTotalAndTipoAndAtivaTrue(
                                usuarioId, tx.getData(), tx.getDescricao(), tx.getValorTotal(), tx.getTipo())) {
                        result.addDuplicada();
                        continue;
                    }
                    transacaoService.criar(tx, usuarioId);
                    result.addImportada();
                } catch (Exception e) {
                    result.addErro();
                }
            }
        } catch (Exception e) {
            result.addErro();
        }

        return result;
    }

    private Map<String, Integer> parseHeader(String header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String[] columns = parseCsvLine(header);
        for (int i = 0; i < columns.length; i++) {
            map.put(normalize(columns[i]), i);
        }
        return map;
    }

    private Transacao buildTransacao(Long usuarioId, String[] fields, Map<String, Integer> col,
                                     Carteira carteiraPadrao) {
        String dataStr = getField(fields, col, "data");
        String descricao = getField(fields, col, "descricao");
        String valorStr = getField(fields, col, "valor");
        String tipoStr = getField(fields, col, "tipo");

        if (dataStr == null || descricao == null || valorStr == null) {
            return null;
        }

        LocalDate data = parseDate(dataStr);
        if (data == null) return null;

        BigDecimal valor;
        try {
            valorStr = valorStr.replace(",", ".").replace("R$", "").trim();
            valor = new BigDecimal(valorStr);
        } catch (NumberFormatException e) {
            return null;
        }

        TipoTransacao tipo = parseTipo(tipoStr);

        Transacao tx = new Transacao();
        tx.setData(data);
        tx.setDescricao(descricao.trim());
        tx.setValorTotal(valor);
        tx.setTipo(tipo);

        String categoriaNome = getField(fields, col, "categoria");
        if (categoriaNome != null) {
            categoriaRepository.findByUsuarioIdAndNomeIgnoreCase(usuarioId, categoriaNome)
                .ifPresent(tx::setCategoria);
        }

        String contaNome = getField(fields, col, "conta");
        if (contaNome != null) {
            contaRepository.findByUsuarioIdAndNomeIgnoreCase(usuarioId, contaNome)
                .ifPresent(tx::setConta);
        }

        // Carteira nomeada mas inexistente é erro de linha: importar sem carteira
        // criaria transação que não movimenta saldo — exatamente o que este fluxo evita
        String carteiraNome = getField(fields, col, "carteira");
        if (carteiraNome != null) {
            Carteira carteira = carteiraRepository.findByUsuarioIdAndNomeIgnoreCase(usuarioId, carteiraNome)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada: " + carteiraNome));
            tx.setCarteira(carteira);
        } else if (carteiraPadrao != null) {
            tx.setCarteira(carteiraPadrao);
        }

        String statusStr = getField(fields, col, "status");
        tx.setStatus(parseStatus(statusStr));

        String obs = getField(fields, col, "observacoes");
        if (obs != null && !obs.isBlank()) {
            tx.setObservacoes(obs.trim());
        }

        tx.setParcelado(false);
        tx.setRecorrente(false);

        return tx;
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private TipoTransacao parseTipo(String value) {
        if (value == null) return TipoTransacao.SAIDA;
        String v = normalize(value);
        if (v.contains("receita") || v.contains("entrada") || v.contains("credito")) {
            return TipoTransacao.ENTRADA;
        }
        return TipoTransacao.SAIDA;
    }

    private StatusPagamento parseStatus(String value) {
        if (value == null) return StatusPagamento.PENDENTE;
        String v = normalize(value);
        if (v.contains("pago") || v.contains("paga")) return StatusPagamento.PAGO;
        if (v.contains("cancelado") || v.contains("cancelada")) return StatusPagamento.CANCELADO;
        return StatusPagamento.PENDENTE;
    }

    private String getField(String[] fields, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx != null && idx < fields.length) {
            String val = fields[idx];
            return (val != null && !val.isBlank()) ? val.trim() : null;
        }
        return null;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.strip().toLowerCase()
            .replace("ã", "a").replace("á", "a").replace("à", "a")
            .replace("é", "e").replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o").replace("ô", "o").replace("õ", "o")
            .replace("ú", "u")
            .replace("ç", "c");
    }
}

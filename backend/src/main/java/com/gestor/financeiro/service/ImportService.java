package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class ImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Transactional(rollbackFor = Exception.class)
    public ImportResultDto importarCsv(Long usuarioId, MultipartFile file) {
        ImportResultDto result = new ImportResultDto();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return result;
            }

            Map<String, Integer> columnMap = parseHeader(headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;

                try {
                    String[] fields = parseCsvLine(line);
                    Transacao tx = buildTransacao(usuario, fields, columnMap);
                    if (tx != null) {
                        transacaoRepository.save(tx);
                        result.addImportada();
                    } else {
                        result.addIgnorada();
                    }
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

    private Transacao buildTransacao(Usuario usuario, String[] fields, Map<String, Integer> col) {
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
        tx.setUsuario(usuario);
        tx.setData(data);
        tx.setDescricao(descricao.trim());
        tx.setValorTotal(valor);
        tx.setTipo(tipo);

        String categoriaNome = getField(fields, col, "categoria");
        if (categoriaNome != null && !categoriaNome.isBlank()) {
            categoriaRepository.findByUsuarioIdAndNomeIgnoreCase(usuario.getId(), categoriaNome.trim())
                .ifPresent(tx::setCategoria);
        }

        String contaNome = getField(fields, col, "conta");
        if (contaNome != null && !contaNome.isBlank()) {
            contaRepository.findByUsuarioIdAndNomeIgnoreCase(usuario.getId(), contaNome.trim())
                .ifPresent(tx::setConta);
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

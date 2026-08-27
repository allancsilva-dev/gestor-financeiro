package com.gestor.financeiro.service.importacao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.ImportMapeamento;
import com.gestor.financeiro.repository.ImportMapeamentoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Perfis de mapeamento de colunas do titular.
 *
 * <p>Serve o arquivo que a detecção automática não entende: o usuário abre o extrato, vê que a
 * coluna de valor chama "Vlr (R$)" e diz isso uma vez. No próximo envio, escolhe o perfil.</p>
 *
 * <p>Limites deliberados: só os campos canônicos são aceitos (nome de coluna inventado seria
 * ignorado em silêncio), data e valor são obrigatórios (sem eles não existe lançamento), e o
 * delimitador aceito é um dos quatro que o parser reconhece.</p>
 */
@Service
public class ImportMapeamentoService {

    private static final Set<Character> DELIMITADORES = Set.of(',', ';', '|', '\t');
    private static final Set<String> OBRIGATORIOS = Set.of("date", "amount");

    private final ImportMapeamentoRepository mapeamentos;
    private final UsuarioRepository usuarios;
    private final ObjectMapper objectMapper;
    private final int limitePorTitular;

    public ImportMapeamentoService(ImportMapeamentoRepository mapeamentos, UsuarioRepository usuarios,
                                   ObjectMapper objectMapper,
                                   @Value("${app.import.max-mapeamentos:20}") int limitePorTitular) {
        this.mapeamentos = mapeamentos;
        this.usuarios = usuarios;
        this.objectMapper = objectMapper;
        this.limitePorTitular = Math.max(1, limitePorTitular);
    }

    @Transactional(readOnly = true)
    public List<ImportMapeamento> listar(Long usuarioId) {
        return mapeamentos.findByUsuarioIdOrderByNomeAsc(usuarioId);
    }

    @Transactional
    public ImportMapeamento salvar(Long usuarioId, String nome, String instituicao, String delimitador,
                                   Map<String, String> colunas) {
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.length() < 2 || nomeLimpo.length() > 80) {
            throw new BusinessException("Dê um nome ao mapeamento");
        }
        Map<String, String> validadas = validarColunas(colunas);
        String delimitadorValidado = validarDelimitador(delimitador);

        ImportMapeamento mapeamento = mapeamentos.findByUsuarioIdAndNome(usuarioId, nomeLimpo)
                .orElseGet(() -> {
                    if (mapeamentos.findByUsuarioIdOrderByNomeAsc(usuarioId).size() >= limitePorTitular) {
                        throw new BusinessException("Limite de mapeamentos atingido");
                    }
                    ImportMapeamento novo = new ImportMapeamento();
                    novo.setUsuario(usuarios.getReferenceById(usuarioId));
                    novo.setNome(nomeLimpo);
                    return novo;
                });

        mapeamento.setInstituicao(instituicao == null || instituicao.isBlank() ? null : instituicao.trim());
        mapeamento.setDelimitador(delimitadorValidado);
        mapeamento.setColunas(escrever(validadas));
        return mapeamentos.save(mapeamento);
    }

    @Transactional
    public void remover(Long usuarioId, Long id) {
        ImportMapeamento mapeamento = mapeamentos.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Mapeamento não encontrado"));
        mapeamentos.delete(mapeamento);
    }

    /** Converte o perfil salvo no que o connector entende. */
    @Transactional(readOnly = true)
    public ImportMapping carregar(Long usuarioId, Long mapeamentoId) {
        if (mapeamentoId == null) return ImportMapping.automatico();
        ImportMapeamento mapeamento = mapeamentos.findByIdAndUsuarioId(mapeamentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Mapeamento não encontrado"));
        return paraMapping(mapeamento);
    }

    public ImportMapping paraMapping(ImportMapeamento mapeamento) {
        Character delimitador = mapeamento.getDelimitador() == null || mapeamento.getDelimitador().isEmpty()
                ? null : mapeamento.getDelimitador().charAt(0);
        return new ImportMapping(ler(mapeamento.getColunas()), delimitador);
    }

    public Map<String, String> colunasDe(ImportMapeamento mapeamento) {
        return ler(mapeamento.getColunas());
    }

    private Map<String, String> validarColunas(Map<String, String> colunas) {
        if (colunas == null || colunas.isEmpty()) {
            throw new BusinessException("Indique ao menos data e valor");
        }
        Map<String, String> validadas = new HashMap<>();
        for (Map.Entry<String, String> entrada : colunas.entrySet()) {
            String campo = entrada.getKey() == null ? "" : entrada.getKey().trim();
            String coluna = entrada.getValue() == null ? "" : entrada.getValue().trim();
            if (!ImportMapping.CAMPOS.contains(campo)) {
                // Campo desconhecido não pode entrar: seria ignorado no parse e o usuário acharia
                // que mapeou algo.
                throw new BusinessException("Campo de mapeamento desconhecido: " + campo);
            }
            if (coluna.isEmpty() || coluna.length() > 120) {
                throw new BusinessException("Nome de coluna inválido para " + campo);
            }
            validadas.put(campo, coluna);
        }
        if (!validadas.keySet().containsAll(OBRIGATORIOS)) {
            throw new BusinessException("Data e valor são obrigatórios no mapeamento");
        }
        return validadas;
    }

    private String validarDelimitador(String delimitador) {
        if (delimitador == null || delimitador.isEmpty()) return null;
        if (delimitador.length() != 1 || !DELIMITADORES.contains(delimitador.charAt(0))) {
            throw new BusinessException("Delimitador não suportado");
        }
        return delimitador;
    }

    private String escrever(Map<String, String> colunas) {
        try {
            return objectMapper.writeValueAsString(colunas);
        } catch (Exception falha) {
            throw new BusinessException("Mapeamento inválido");
        }
    }

    private Map<String, String> ler(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception falha) {
            throw new BusinessException("Mapeamento inválido");
        }
    }
}

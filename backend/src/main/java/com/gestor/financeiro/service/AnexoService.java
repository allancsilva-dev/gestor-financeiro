package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.AnexoResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Anexo;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.AnexoRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnexoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    // Tipos aceitos para comprovantes. MIME canônico derivado da extensão,
    // nunca do Content-Type enviado pelo cliente.
    private static final Map<String, String> EXTENSOES_PERMITIDAS = Map.of(
        "pdf", "application/pdf",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp"
    );

    private static final String EXTENSOES_MSG = "pdf, jpg, jpeg, png, webp";

    private final AnexoRepository anexoRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    public AnexoService(AnexoRepository anexoRepository, TransacaoRepository transacaoRepository,
                        UsuarioRepository usuarioRepository) {
        this.anexoRepository = anexoRepository;
        this.transacaoRepository = transacaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public AnexoResponse upload(Long usuarioId, Long transacaoId, MultipartFile file) throws IOException {
        Transacao transacao = transacaoRepository.findByIdAndUsuarioId(transacaoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Transacao nao encontrada"));
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();

        String extensao = validarExtensao(file);
        validarAssinatura(file, extensao);

        Path dir = Paths.get(uploadDir, usuarioId.toString());
        Files.createDirectories(dir);

        // Nome em disco nunca usa o filename do cliente (path traversal / injeção).
        String storedName = UUID.randomUUID() + "." + extensao;
        Path filePath = dir.resolve(storedName);
        file.transferTo(filePath.toFile());

        Anexo anexo = new Anexo();
        anexo.setNome(nomeExibicao(file.getOriginalFilename()));
        anexo.setTipo(EXTENSOES_PERMITIDAS.get(extensao));
        anexo.setTamanho(file.getSize());
        anexo.setCaminho(filePath.toString());
        anexo.setTransacao(transacao);
        anexo.setUsuario(usuario);
        anexo.setDataUpload(LocalDateTime.now());

        anexo = anexoRepository.save(anexo);

        return toResponse(anexo);
    }

    public List<AnexoResponse> listar(Long usuarioId, Long transacaoId) {
        return anexoRepository.findByTransacaoIdAndUsuarioId(transacaoId, usuarioId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public byte[] download(Long usuarioId, Long anexoId) throws IOException {
        Anexo anexo = anexoRepository.findByIdAndUsuarioId(anexoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Anexo nao encontrado"));
        return Files.readAllBytes(Paths.get(anexo.getCaminho()));
    }

    public Anexo getAnexo(Long usuarioId, Long anexoId) {
        return anexoRepository.findByIdAndUsuarioId(anexoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Anexo nao encontrado"));
    }

    /**
     * Content-Type seguro para servir o anexo: só devolve o tipo gravado se
     * estiver na whitelist (registros antigos podem ter MIME arbitrário do cliente).
     */
    public String contentTypeSeguro(Anexo anexo) {
        String tipo = anexo.getTipo();
        if (tipo != null && EXTENSOES_PERMITIDAS.containsValue(tipo)) {
            return tipo;
        }
        return "application/octet-stream";
    }

    private String validarExtensao(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo vazio ou ausente");
        }
        String original = file.getOriginalFilename();
        int ponto = original != null ? original.lastIndexOf('.') : -1;
        if (ponto < 0 || ponto == original.length() - 1) {
            throw new BusinessException("Arquivo sem extensão. Tipos permitidos: " + EXTENSOES_MSG);
        }
        String extensao = original.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSOES_PERMITIDAS.containsKey(extensao)) {
            throw new BusinessException("Tipo de arquivo não permitido. Tipos permitidos: " + EXTENSOES_MSG);
        }
        return extensao;
    }

    /**
     * Confere os magic bytes do arquivo contra a extensão declarada, para impedir
     * conteúdo arbitrário (ex.: HTML) disfarçado com extensão permitida.
     */
    private void validarAssinatura(MultipartFile file, String extensao) throws IOException {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(12);
        }
        boolean valido = switch (extensao) {
            case "pdf" -> comecaCom(header, "%PDF");
            case "jpg", "jpeg" -> header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> header.length >= 4
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "webp" -> header.length >= 12
                && comecaCom(header, "RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
        if (!valido) {
            throw new BusinessException("Conteúdo do arquivo não corresponde à extensão ." + extensao);
        }
    }

    private boolean comecaCom(byte[] header, String prefixo) {
        byte[] esperado = prefixo.getBytes(StandardCharsets.US_ASCII);
        if (header.length < esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if (header[i] != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Nome para exibição: só o último segmento (navegadores podem mandar caminho completo).
     */
    private String nomeExibicao(String original) {
        if (original == null || original.isBlank()) {
            return "anexo";
        }
        int barra = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        return barra >= 0 ? original.substring(barra + 1) : original;
    }

    @Transactional
    public void deletar(Long usuarioId, Long anexoId) throws IOException {
        Anexo anexo = anexoRepository.findByIdAndUsuarioId(anexoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Anexo nao encontrado"));
        Files.deleteIfExists(Paths.get(anexo.getCaminho()));
        anexoRepository.delete(anexo);
    }

    private AnexoResponse toResponse(Anexo a) {
        return AnexoResponse.builder()
            .id(a.getId())
            .nome(a.getNome())
            .tipo(a.getTipo())
            .tamanho(a.getTamanho())
            .dataUpload(a.getDataUpload())
            .build();
    }
}

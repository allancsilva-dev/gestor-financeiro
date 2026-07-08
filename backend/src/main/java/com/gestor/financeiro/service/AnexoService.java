package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.AnexoResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnexoService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

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

        Path dir = Paths.get(uploadDir, usuarioId.toString());
        Files.createDirectories(dir);

        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = dir.resolve(storedName);
        file.transferTo(filePath.toFile());

        Anexo anexo = new Anexo();
        anexo.setNome(file.getOriginalFilename());
        anexo.setTipo(file.getContentType());
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

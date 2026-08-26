package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.enums.ImportFailureCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;

/**
 * Fonte de importação respaldada por arquivo temporário próprio.
 *
 * <p>O orquestrador lê o conteúdo mais de uma vez (hash, detecção, parse), então o stream precisa
 * ser reabrível — o {@link MultipartFile} não garante isso. O arquivo é criado com permissão
 * restrita ao dono e apagado em {@link #close()}, inclusive no caminho de exceção.</p>
 */
public final class TempFileImportSource implements ImportSource, AutoCloseable {

    private static final Set<PosixFilePermission> SOMENTE_DONO = PosixFilePermissions.fromString("rw-------");

    private final Path arquivo;
    private final long tamanho;
    private final String nomeExibicao;
    private final String contentType;

    private TempFileImportSource(Path arquivo, long tamanho, String nomeExibicao, String contentType) {
        this.arquivo = arquivo;
        this.tamanho = tamanho;
        this.nomeExibicao = nomeExibicao;
        this.contentType = contentType;
    }

    public static TempFileImportSource of(MultipartFile file, Path diretorio) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        }
        Files.createDirectories(diretorio);
        Path destino = criarArquivoRestrito(diretorio);
        boolean copiado = false;
        try (InputStream entrada = file.getInputStream()) {
            long copiados = Files.copy(entrada, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            copiado = true;
            return new TempFileImportSource(destino, copiados, nomeSeguro(file.getOriginalFilename()),
                    file.getContentType());
        } finally {
            if (!copiado) {
                Files.deleteIfExists(destino);
            }
        }
    }

    private static Path criarArquivoRestrito(Path diretorio) throws IOException {
        try {
            return Files.createTempFile(diretorio, "import-", ".bin",
                    PosixFilePermissions.asFileAttribute(SOMENTE_DONO));
        } catch (UnsupportedOperationException semPosix) {
            // Windows e sistemas sem POSIX: sem atributo na criação, mas o diretório é dedicado.
            return Files.createTempFile(diretorio, "import-", ".bin");
        }
    }

    /**
     * Nome só serve para diagnóstico: caminho, separador e controle saem fora para o valor nunca
     * poder ser usado como caminho de arquivo nem poluir log.
     */
    private static String nomeSeguro(String original) {
        if (original == null || original.isBlank()) {
            return "arquivo";
        }
        String limpo = original.replaceAll("[\\p{Cc}\\p{Cf}]", "")
                .replace('\\', '/');
        int barra = limpo.lastIndexOf('/');
        if (barra >= 0) {
            limpo = limpo.substring(barra + 1);
        }
        limpo = limpo.replaceAll("[^A-Za-z0-9._-]", "_").trim();
        if (limpo.isBlank() || limpo.equals(".") || limpo.equals("..")) {
            return "arquivo";
        }
        return limpo.length() > 120 ? limpo.substring(0, 120).toLowerCase(Locale.ROOT) : limpo.toLowerCase(Locale.ROOT);
    }

    @Override
    public InputStream openStream() throws IOException {
        return Files.newInputStream(arquivo);
    }

    @Override
    public long size() {
        return tamanho;
    }

    @Override
    public String displayName() {
        return nomeExibicao;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    /** Hash declarado não existe neste caminho: quem calcula é o orquestrador, lendo o arquivo. */
    @Override
    public String sha256() {
        return null;
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(arquivo);
    }
}

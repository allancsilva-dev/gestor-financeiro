package com.gestor.financeiro.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Exclusão definitiva de conta (LGPD art. 18, V - eliminação).
 *
 * Apaga todos os dados do titular em uma única transação, na ordem inversa das
 * dependências de FK (as FKs do schema não têm ON DELETE CASCADE de propósito:
 * elas protegem os fluxos normais do app contra exclusão acidental em cadeia).
 * Os arquivos de anexo em disco são removidos somente após o commit.
 */
@Service
public class UsuarioExclusaoService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioExclusaoService.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Manifesto de exclusão do titular: tabela física + JPQL de remoção, filhas antes das pais.
     * É a fonte única da ordem de exclusão. Toda tabela nova alcançável a partir do titular
     * precisa entrar aqui — o teste-guardião ({@code UsuarioExclusaoLgpdIT}) compara este
     * manifesto com o catálogo de FKs do PostgreSQL e falha se algo ficar de fora.
     */
    public record DeleteTitular(String tabela, String jpql) {}

    public static final List<DeleteTitular> MANIFESTO_EXCLUSAO = List.of(
        new DeleteTitular("anexos",
            "DELETE FROM Anexo a WHERE a.usuario.id = :id"),
        new DeleteTitular("fatura_lancamentos",
            "DELETE FROM FaturaLancamento fl WHERE fl.fatura.id IN (SELECT f.id FROM FaturaCartao f WHERE f.usuario.id = :id)"),
        new DeleteTitular("parcelas",
            "DELETE FROM Parcela p WHERE p.transacao.id IN (SELECT t.id FROM Transacao t WHERE t.usuario.id = :id)"),
        new DeleteTitular("execucoes_recorrencia",
            "DELETE FROM ExecucaoRecorrencia er WHERE er.usuario.id = :id"),
        new DeleteTitular("transacoes",
            "DELETE FROM Transacao t WHERE t.usuario.id = :id"),
        new DeleteTitular("faturas_cartao",
            "DELETE FROM FaturaCartao f WHERE f.usuario.id = :id"),
        new DeleteTitular("contas_fixas",
            "DELETE FROM ContaFixa cf WHERE cf.usuario.id = :id"),
        new DeleteTitular("orcamentos_categorias",
            "DELETE FROM OrcamentoCategoria oc WHERE oc.orcamento.id IN (SELECT o.id FROM OrcamentoMensal o WHERE o.usuario.id = :id)"),
        new DeleteTitular("orcamentos_mensais",
            "DELETE FROM OrcamentoMensal o WHERE o.usuario.id = :id"),
        new DeleteTitular("movimentos_carteira",
            "DELETE FROM MovimentoCarteira mc WHERE mc.usuario.id = :id"),
        new DeleteTitular("movimentos_meta",
            "DELETE FROM MovimentoMeta mm WHERE mm.usuario.id = :id"),
        new DeleteTitular("metas",
            "DELETE FROM Meta m WHERE m.usuario.id = :id"),
        new DeleteTitular("movimentacoes_ativo",
            "DELETE FROM MovimentacaoAtivo ma WHERE ma.usuario.id = :id"),
        new DeleteTitular("ativos",
            "DELETE FROM Ativo a WHERE a.usuario.id = :id"),
        new DeleteTitular("categorias",
            "DELETE FROM Categoria c WHERE c.usuario.id = :id"),
        new DeleteTitular("contas",
            "DELETE FROM Conta c WHERE c.usuario.id = :id"),
        new DeleteTitular("carteiras",
            "DELETE FROM Carteira c WHERE c.usuario.id = :id"),
        new DeleteTitular("refresh_tokens",
            "DELETE FROM RefreshToken rt WHERE rt.usuario.id = :id"),
        new DeleteTitular("password_reset_tokens",
            "DELETE FROM PasswordResetToken pt WHERE pt.usuario.id = :id"),
        new DeleteTitular("usuarios",
            "DELETE FROM Usuario u WHERE u.id = :id")
    );

    @Transactional
    public void excluirConta(Long usuarioId) {
        for (DeleteTitular delete : MANIFESTO_EXCLUSAO) {
            entityManager.createQuery(delete.jpql())
                .setParameter("id", usuarioId)
                .executeUpdate();
        }

        log.info("LGPD: conta e dados do usuário {} excluídos", usuarioId);

        // Arquivos só somem se o banco confirmou; rollback não perde anexo.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removerDiretorioDeUploads(usuarioId);
            }
        });
    }

    private void removerDiretorioDeUploads(Long usuarioId) {
        Path dir = Paths.get(uploadDir, usuarioId.toString());
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("LGPD: falha ao remover arquivo {} do usuário {}", path, usuarioId, e);
                }
            });
        } catch (IOException e) {
            log.warn("LGPD: falha ao varrer diretório de uploads do usuário {}", usuarioId, e);
        }
    }
}

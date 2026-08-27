package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Notificacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.model.enums.TipoNotificacao;
import com.gestor.financeiro.repository.NotificacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Notificacoes in-app. Nao inventa evento novo: deriva do que o sistema ja
 * detecta — compromissos proximos, falha de saldo de recorrencia, orcamento
 * estourado e meta concluida.
 *
 * A sincronizacao roda quando o usuario abre a home e e idempotente pela chave
 * natural do evento, entao chamar varias vezes no mesmo dia nao duplica nada.
 * Toda notificacao carrega destino de navegacao no padrao do PR-F3-04.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    /** Fatura/parcela dentro desta janela ja merece aviso. */
    private static final int DIAS_DE_AVISO = 7;

    public static final String DESTINO_FATURA = "FATURA";
    public static final String DESTINO_TRANSACAO = "TRANSACAO";
    public static final String DESTINO_CONTA_FIXA = "CONTA_FIXA";
    public static final String DESTINO_ORCAMENTO = "ORCAMENTO";
    public static final String DESTINO_META = "META";

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CompromissosService compromissosService;
    private final OrcamentoService orcamentoService;
    private final MetaService metaService;
    private final Clock clock;

    // ── leitura ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Notificacao> listar(Long usuarioId, Pageable pageable) {
        return notificacaoRepository.findByUsuarioIdOrderByLidaAscCriadaEmDesc(usuarioId, pageable);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    // ── escrita ──────────────────────────────────────────────────────────────

    @Transactional
    public Notificacao marcarComoLida(Long id, Long usuarioId) {
        Notificacao n = notificacaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
        n.setLida(true);
        return notificacaoRepository.save(n);
    }

    @Transactional
    public int marcarTodasComoLidas(Long usuarioId) {
        return notificacaoRepository.marcarTodasComoLidas(usuarioId);
    }

    /**
     * Deriva os eventos atuais e persiste os que ainda nao existem.
     * Devolve a contagem de nao lidas ja atualizada.
     */
    @Transactional
    public long sincronizar(Long usuarioId) {
        sincronizarRetornandoNovas(usuarioId);
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    /**
     * Mesma sincronizacao, devolvendo o que nasceu agora. O envio de push precisa saber o que e
     * novidade: reenviar aviso ja entregue todo dia treina o usuario a ignorar a notificacao.
     */
    @Transactional
    public List<Notificacao> sincronizarRetornandoNovas(Long usuarioId) {
        Set<String> existentes = notificacaoRepository.findChavesDoUsuario(usuarioId);
        List<Notificacao> novas = new ArrayList<>();

        for (Rascunho r : derivar(usuarioId)) {
            if (existentes.contains(r.chave())) {
                continue;
            }
            existentes.add(r.chave());
            novas.add(montar(usuarioId, r));
        }
        if (novas.isEmpty()) {
            return List.of();
        }
        return notificacaoRepository.saveAll(novas);
    }

    // ── derivacao ────────────────────────────────────────────────────────────

    private record Rascunho(TipoNotificacao tipo, String titulo, String mensagem,
                            String destino, Long destinoId, String chave) {
    }

    private List<Rascunho> derivar(Long usuarioId) {
        List<Rascunho> out = new ArrayList<>();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate limite = hoje.plusDays(DIAS_DE_AVISO);

        compromissosService.listar(usuarioId, null).itens().forEach(item -> {
            if (CompromissosService.ALERTA_FALHA_SALDO.equals(item.alerta())) {
                out.add(new Rascunho(TipoNotificacao.FALHA_SALDO,
                        "Recorrência sem saldo",
                        item.descricao() + " não pôde ser lançada por falta de saldo.",
                        DESTINO_CONTA_FIXA, item.id(),
                        "FALHA_SALDO:" + item.id()));
                return;
            }
            if (item.vencimento() == null || item.vencimento().isAfter(limite)) {
                return;
            }
            if ("FATURA".equals(item.tipo())) {
                out.add(new Rascunho(TipoNotificacao.FATURA_VENCENDO,
                        "Fatura vencendo",
                        item.descricao() + " vence em " + item.vencimento() + ".",
                        DESTINO_FATURA, item.id(),
                        "FATURA_VENCENDO:" + item.id()));
            } else if ("PARCELA".equals(item.tipo())) {
                out.add(new Rascunho(TipoNotificacao.PARCELA_AGENDADA,
                        "Parcela agendada",
                        item.descricao() + " vence em " + item.vencimento() + ".",
                        DESTINO_TRANSACAO, item.id(),
                        "PARCELA_AGENDADA:" + item.id()));
            }
        });

        // Orcamento estourado: so a categoria que passou do limite do mes corrente
        var orcamento = orcamentoService.buscarOuCriarAtual(usuarioId);
        orcamento.categorias().stream()
                .filter(c -> c.valorLimite() != null && c.valorLimite().signum() > 0)
                .filter(c -> nvl(c.valorGasto()).compareTo(c.valorLimite()) > 0)
                .forEach(c -> out.add(new Rascunho(TipoNotificacao.ORCAMENTO_ESTOURADO,
                        "Orçamento estourado",
                        "Você passou do limite em " + c.categoriaNome() + " neste mês.",
                        DESTINO_ORCAMENTO, c.categoriaId(),
                        "ORCAMENTO_ESTOURADO:" + orcamento.ano() + "-" + orcamento.mes()
                                + ":" + c.categoriaId())));

        // Meta concluida: uma vez por meta, para sempre
        metaService.listarPorUsuario(usuarioId, StatusMeta.CONCLUIDA, PageRequest.of(0, 20))
                .forEach((Meta m) -> out.add(new Rascunho(TipoNotificacao.META_ATINGIDA,
                        "Meta atingida",
                        "Você concluiu a meta " + m.getNome() + ".",
                        DESTINO_META, m.getId(),
                        "META_ATINGIDA:" + m.getId())));

        return out;
    }

    private Notificacao montar(Long usuarioId, Rascunho r) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Notificacao n = new Notificacao();
        n.setUsuario(usuario);
        n.setTipo(r.tipo());
        n.setTitulo(r.titulo());
        n.setMensagem(r.mensagem());
        n.setDestino(r.destino());
        n.setDestinoId(r.destinoId());
        n.setChave(r.chave());
        n.setLida(false);
        n.setCriadaEm(LocalDateTime.now(clock));
        return n;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

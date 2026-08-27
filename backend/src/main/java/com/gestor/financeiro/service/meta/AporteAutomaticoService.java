package com.gestor.financeiro.service.meta;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Notificacao;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.model.enums.TipoNotificacao;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.NotificacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.MetaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Aporte mensal automático das metas do titular.
 *
 * <p>Regras que este serviço existe para garantir:</p>
 * <ul>
 *   <li><b>Passa pelo caminho de domínio.</b> A reserva é feita por
 *       {@link MetaService#adicionarValor(Long, java.math.BigDecimal, Long, Long, String)}, que
 *       mantém o par de lançamentos e o invariante {@code valorReservado == saldo do cofre}
 *       (V37). Mexer direto na coluna quebraria a reconciliação.</li>
 *   <li><b>Idempotente por competência.</b> A chave {@code META_APORTE:{metaId}:{competência}}
 *       chega ao ledger; reexecutar o job no mesmo mês não reserva duas vezes.</li>
 *   <li><b>Saldo insuficiente não vira saldo negativo.</b> O aporte falha, o mês fica sem reserva e
 *       o titular recebe um aviso — o app não empurra a conta para o vermelho para cumprir meta.</li>
 *   <li><b>Uma transação por meta.</b> Falha em uma não impede as outras.</li>
 * </ul>
 */
@Service
public class AporteAutomaticoService {

    private static final Logger log = LoggerFactory.getLogger(AporteAutomaticoService.class);

    private final MetaRepository metas;
    private final CarteiraRepository carteiras;
    private final MetaService metaService;
    private final NotificacaoRepository notificacoes;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate porMeta;
    private final Clock clock;

    public AporteAutomaticoService(MetaRepository metas, CarteiraRepository carteiras, MetaService metaService,
                                   NotificacaoRepository notificacoes, UsuarioRepository usuarios,
                                   PlatformTransactionManager transactionManager, Clock clock) {
        this.metas = metas;
        this.carteiras = carteiras;
        this.metaService = metaService;
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.porMeta = new TransactionTemplate(transactionManager);
        this.porMeta.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Liga ou desliga o aporte automático da meta.
     *
     * <p>Ligar exige valor, dia e conta: sem os três, o job não teria instrução, e o CHECK da V54
     * recusaria a linha de qualquer forma. Dia vai até 28 para existir em todo mês — quem escolhe
     * 31 não seria atendido em fevereiro.</p>
     */
    @Transactional
    public Meta configurar(Long usuarioId, Long metaId, boolean ativo, Short dia, Long carteiraId,
                           java.math.BigDecimal valorMensal) {
        Meta meta = metas.findById(metaId)
                .filter(m -> m.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));

        if (!ativo) {
            meta.setAporteAutomatico(false);
            meta.setAporteDia(null);
            meta.setAporteCarteira(null);
            return metas.save(meta);
        }

        java.math.BigDecimal valor = valorMensal != null ? valorMensal : meta.getValorMensal();
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException("Defina quanto guardar por mês antes de automatizar");
        }
        if (dia == null || dia < 1 || dia > 28) {
            throw new BusinessException("Escolha um dia entre 1 e 28");
        }
        Carteira carteira = carteiras.findByIdAndUsuarioId(carteiraId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta financeira não encontrada"));
        if (carteira.getSubtipo() == SubtipoContaFinanceira.CARTAO
                || carteira.getSubtipo() == SubtipoContaFinanceira.COFRE
                || carteira.getSubtipo() == SubtipoContaFinanceira.CUSTODIA) {
            throw new BusinessException("Escolha uma conta de caixa como origem do aporte");
        }

        meta.setValorMensal(valor);
        meta.setAporteAutomatico(true);
        meta.setAporteDia(dia);
        meta.setAporteCarteira(carteira);
        return metas.save(meta);
    }

    /** Executa os aportes devidos do titular na competência atual. Devolve quantos foram feitos. */
    public int executar(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth competencia = YearMonth.from(hoje);
        List<Meta> candidatas = metas.findByUsuarioIdAndAtivaTrue(usuarioId).stream()
                .filter(meta -> Boolean.TRUE.equals(meta.getAporteAutomatico()))
                .filter(meta -> meta.getStatus() == StatusMeta.ATIVA)
                .filter(meta -> meta.getAporteDia() != null && meta.getAporteDia() <= hoje.getDayOfMonth())
                // Competência já aportada não volta: a reserva virtual não passa pelo ledger, então
                // a chave de idempotência de lá não protegeria essa modalidade.
                .filter(meta -> !competencia.toString().equals(meta.getAporteUltimaCompetencia()))
                .toList();

        int aportados = 0;
        for (Meta meta : candidatas) {
            if (aportar(usuarioId, meta, competencia)) {
                aportados++;
            }
        }
        return aportados;
    }

    private boolean aportar(Long usuarioId, Meta meta, YearMonth competencia) {
        String chave = "META_APORTE:" + meta.getId() + ":" + competencia;
        try {
            return Boolean.TRUE.equals(porMeta.execute(status -> {
                metaService.adicionarValor(meta.getId(), meta.getValorMensal(),
                        meta.getAporteCarteira().getId(), usuarioId, chave);
                Meta atual = metas.findById(meta.getId()).orElseThrow();
                atual.setAporteUltimaCompetencia(competencia.toString());
                metas.save(atual);
                return true;
            }));
        } catch (RuntimeException falha) {
            // Saldo insuficiente é o caso comum aqui, e não é erro de sistema: o mês fica sem
            // reserva e o titular decide o que fazer.
            log.info("meta_aporte_nao_realizado metaId={} competencia={} motivo={}",
                    meta.getId(), competencia, falha.getClass().getSimpleName());
            avisar(usuarioId, meta, competencia);
            return false;
        }
    }

    /** Aviso na caixa in-app, idempotente pela chave natural do evento. */
    private void avisar(Long usuarioId, Meta meta, YearMonth competencia) {
        String chave = "META_APORTE_FALHOU:" + meta.getId() + ":" + competencia;
        porMeta.executeWithoutResult(status -> {
            if (notificacoes.findChavesDoUsuario(usuarioId).contains(chave)) return;
            Notificacao notificacao = new Notificacao();
            notificacao.setUsuario(usuarios.getReferenceById(usuarioId));
            notificacao.setTipo(TipoNotificacao.FALHA_SALDO);
            notificacao.setTitulo("Aporte da meta não saiu");
            notificacao.setMensagem("Não havia saldo na conta escolhida para guardar o valor de "
                    + meta.getNome() + " neste mês.");
            notificacao.setDestino("META");
            notificacao.setDestinoId(meta.getId());
            notificacao.setChave(chave);
            notificacoes.save(notificacao);
        });
    }
}

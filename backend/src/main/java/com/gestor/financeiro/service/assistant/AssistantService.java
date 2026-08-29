package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.AssistantDtos.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.AssistantException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.*;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.service.CriarOperacaoCommand;
import com.gestor.financeiro.service.OperacaoFinanceiraService;
import com.gestor.financeiro.service.TransacaoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssistantService {
    private static final String SCHEMA_VERSION = "transaction-draft-v1";
    private static final String PROMPT_VERSION = "deterministic-v1";
    private final AssistantConversationRepository conversations;
    private final AssistantMessageRepository messages;
    private final AssistantDraftRepository drafts;
    private final AssistantConfirmationRepository confirmations;
    private final UsuarioRepository usuarios;
    private final CarteiraRepository carteiras;
    private final CategoriaRepository categorias;
    private final ContaRepository contas;
    private final RuleBasedFinancialInputParser parser;
    private final AiExtractionPipeline ai;
    private final FinancialQuestionClassifier questions;
    private final FinancialReadTool financialReadTool;
    private final FinancialAnswerFormatter answerFormatter;
    private final OperacaoFinanceiraService operacoes;
    private final TransacaoService transacoes;
    private final ObjectMapper objectMapper;
    private final AssistantMutationReplay mutationReplay;
    private final Clock clock;

    public AssistantService(AssistantConversationRepository conversations, AssistantMessageRepository messages,
                            AssistantDraftRepository drafts, AssistantConfirmationRepository confirmations,
                            UsuarioRepository usuarios, CarteiraRepository carteiras,
                            CategoriaRepository categorias, ContaRepository contas,
                            RuleBasedFinancialInputParser parser, AiExtractionPipeline ai,
                            FinancialQuestionClassifier questions, FinancialReadTool financialReadTool,
                            FinancialAnswerFormatter answerFormatter,
                            OperacaoFinanceiraService operacoes, TransacaoService transacoes,
                            ObjectMapper objectMapper, AssistantMutationReplay mutationReplay, Clock clock) {
        this.conversations = conversations; this.messages = messages; this.drafts = drafts;
        this.confirmations = confirmations; this.usuarios = usuarios; this.carteiras = carteiras;
        this.categorias = categorias; this.contas = contas; this.parser = parser; this.ai = ai; this.questions = questions;
        this.financialReadTool = financialReadTool; this.answerFormatter = answerFormatter; this.operacoes = operacoes;
        this.transacoes = transacoes; this.objectMapper = objectMapper; this.mutationReplay = mutationReplay; this.clock = clock;
    }

    @Transactional
    public MessageResponse receive(Long usuarioId, MessageRequest request) {
        return receive(usuarioId, request, null);
    }

    @Transactional
    public MessageResponse receive(Long usuarioId, MessageRequest request, String idempotencyKey) {
        Usuario usuario = (idempotencyKey == null ? usuarios.findById(usuarioId) : usuarios.findByIdComLock(usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        String requestHash = OperacaoFinanceiraService.hashPayload(
                (request.conversationId() == null ? "NEW" : request.conversationId()) + "\n" + request.text());
        if (idempotencyKey != null) {
            var replay = messages.findByUsuarioIdAndIdempotencyKey(usuarioId, idempotencyKey);
            if (replay.isPresent()) {
                if (!requestHash.equals(replay.get().getRequestHash())) throw new AssistantException(
                        "DRAFT_CONFLICT", "Idempotency-Key reutilizada com payload diferente",
                        org.springframework.http.HttpStatus.CONFLICT);
                return readReplay(replay.get());
            }
        }
        AssistantConversation conversation = request.conversationId() == null
                ? newConversation(usuario) : conversations.findByIdAndUsuarioId(request.conversationId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada"));
        AssistantMessage userMessage = saveMessage(conversation, usuario, "USER", request.text());
        if (request.conversationId() != null) {
            var clarification = drafts.findLatestClarificationForUpdate(
                    conversation.getId(), usuarioId, LocalDateTime.now(clock));
            if (clarification.isPresent()) {
                MessageResponse response = continueClarification(
                        clarification.get(), conversation, usuario, request.text());
                return storeReplay(userMessage, idempotencyKey, requestHash, response);
            }
        }
        var question = questions.classify(request.text());
        if (question.isPresent()) {
            String reply = answerFormatter.format(financialReadTool.execute(usuarioId, question.get()));
            saveMessage(conversation, usuario, "ASSISTANT", reply);
            return storeReplay(userMessage, idempotencyKey, requestHash,
                    new MessageResponse(conversation.getId(), ParseOutcome.NOT_FINANCIAL, reply, null));
        }
        FinancialParseResult parsed = parser.parse(usuarioId, request.text());
        if (parsed.outcome() == ParseOutcome.NOT_FINANCIAL) {
            String reply = "Posso ajudar a registrar entradas e saídas. Descreva o valor, a data, a categoria e a conta.";
            saveMessage(conversation, usuario, "ASSISTANT", reply);
            return storeReplay(userMessage, idempotencyKey, requestHash,
                    new MessageResponse(conversation.getId(), parsed.outcome(), reply, null));
        }
        String provider = "DETERMINISTIC"; String model = "RULE_BASED"; String promptVersion = PROMPT_VERSION;
        if (parsed.outcome() != ParseOutcome.COMPLETE) {
            var extraction = ai.extract(new ProviderExtractionRequest(usuarioId, null, request.text(), trustedContext(usuarioId)));
            if (extraction.isPresent()) {
                provider = extraction.get().provider(); model = extraction.get().model(); promptVersion = "financial-extract-v1";
                parsed = classify(usuarioId, extraction.get().draft());
            }
        }
        AssistantDraft draft = persistDraft(usuario, conversation, parsed, request.text(), provider, model, promptVersion);
        String reply = parsed.question() != null ? parsed.question()
                : parsed.outcome() == ParseOutcome.COMPLETE ? "Rascunho pronto. Revise os dados antes de confirmar."
                : "Ainda faltam alguns dados. Complete o formulário para continuar.";
        saveMessage(conversation, usuario, "ASSISTANT", reply);
        return storeReplay(userMessage, idempotencyKey, requestHash,
                new MessageResponse(conversation.getId(), parsed.outcome(), reply,
                response(draft, parsed.draft().missingFields())));
    }

    @Transactional(readOnly = true)
    public List<StoredMessageResponse> listMessages(Long usuarioId, Long conversationId) {
        conversations.findByIdAndUsuarioId(conversationId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada"));
        return messages.findByConversationIdAndUsuarioIdOrderByCreatedAt(conversationId, usuarioId).stream()
                .map(m -> new StoredMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    @Transactional
    public DraftResponse patch(Long usuarioId, Long draftId, PatchDraftRequest request) {
        return patch(usuarioId, draftId, request, null);
    }

    @Transactional
    public DraftResponse patch(Long usuarioId, Long draftId, PatchDraftRequest request, String idempotencyKey) {
        String requestHash = hash(draftId + "\n" + json(request));
        if (idempotencyKey != null) {
            usuarios.findByIdComLock(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            var replay = mutationReplay.find(usuarioId, idempotencyKey, requestHash, DraftResponse.class);
            if (replay.isPresent()) return replay.get();
        }
        AssistantDraft draft = lockPending(usuarioId, draftId);
        requireVersion(draft, request.version());
        if (request.valor() != null && request.valor().signum() <= 0) throw new BusinessException("Valor deve ser positivo");
        draft.setTipo(request.tipo()); draft.setValor(request.valor()); draft.setDescricao(trim(request.descricao()));
        draft.setData(request.data());
        draft.setCarteira(request.carteiraId() == null ? null : carteiras.findByIdAndUsuarioId(request.carteiraId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta financeira não encontrada")));
        draft.setCategoria(request.categoriaId() == null ? null : categorias.findByIdAndUsuarioId(request.categoriaId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada")));
        draft.setConta(request.cartaoId() == null ? null : contas.findByIdAndUsuarioId(request.cartaoId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado")));
        if (request.parcelas() != null && draft.getConta() == null) throw new BusinessException("Parcelamento exige cartão");
        draft.setParcelas(request.parcelas());
        if (draft.getConta() != null) draft.setCarteira(null);
        DraftResponse response = response(drafts.saveAndFlush(draft), missing(draft));
        if (idempotencyKey != null) mutationReplay.store(usuarioId,
                draft.getConversation() == null ? null : draft.getConversation().getId(),
                "PATCH_DRAFT", idempotencyKey, requestHash, response);
        return response;
    }

    @Transactional
    public ConfirmationResponse confirm(Long usuarioId, Long draftId, ConfirmDraftRequest request) {
        return confirm(usuarioId, draftId, request, null);
    }

    @Transactional
    public ConfirmationResponse confirm(Long usuarioId, Long draftId, ConfirmDraftRequest request,
                                        String idempotencyKey) {
        String requestHash = hash("CONFIRM\n" + draftId + "\n" + request.version());
        if (idempotencyKey != null) {
            usuarios.findByIdComLock(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            var replay = mutationReplay.find(usuarioId, idempotencyKey, requestHash, ConfirmationResponse.class);
            if (replay.isPresent()) return replay.get();
        }
        AssistantDraft draft = drafts.findOwnedForUpdate(draftId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Rascunho não encontrado"));
        var existing = confirmations.findByDraftIdAndUsuarioId(draftId, usuarioId);
        if (draft.getStatus() == AssistantDraftStatus.CONFIRMED && existing.isPresent()) {
            if (request.version() == null || !request.version().equals(existing.get().getDraftVersion()))
                throw new AssistantException("DRAFT_CONFLICT", "Payload diverge da confirmação existente",
                        org.springframework.http.HttpStatus.CONFLICT);
            ConfirmationResponse replay = confirmation(existing.get());
            storeConfirmationReplay(usuarioId, draft, idempotencyKey, requestHash, replay);
            return replay;
        }
        requirePendingAndFresh(draft);
        requireVersion(draft, request.version());
        List<String> missing = missing(draft);
        if (!missing.isEmpty()) throw new BusinessException("Rascunho incompleto: " + String.join(", ", missing));

        String canonical = canonical(draft);
        OperacaoFinanceira operation = operacoes.criar(new CriarOperacaoCommand(usuarioId,
                TipoOperacaoFinanceira.TRANSACAO, PoliticaOperacao.CAIXA, OrigemOperacaoFinanceira.ASSISTENTE,
                draft.getData().atStartOfDay(), "ASSISTANT:" + draftId, canonical,
                "Confirmação de rascunho do assistente", null));
        Transacao transaction = new Transacao();
        transaction.setTipo(draft.getTipo()); transaction.setValorTotal(draft.getValor());
        transaction.setDescricao(draft.getDescricao()); transaction.setData(draft.getData());
        transaction.setCategoria(draft.getCategoria());
        transaction.setStatus(StatusPagamento.PAGO);
        if (draft.getConta() != null) {
            // Compra no cartão: cronograma canônico é a fatura, sem carteira envolvida.
            transaction.setConta(draft.getConta());
            transaction.setParcelado(draft.getParcelas() != null && draft.getParcelas() > 1);
            transaction.setTotalParcelas(draft.getParcelas());
        } else {
            transaction.setCarteira(draft.getCarteira());
            transaction.setParcelado(false);
        }
        transaction = transacoes.criar(transaction, usuarioId, "ASSISTANT:" + draftId + ":LEDGER", operation);

        AssistantConfirmation saved = new AssistantConfirmation();
        saved.setDraftId(draftId); saved.setDraftVersion(request.version()); saved.setUsuario(draft.getUsuario()); saved.setOperacao(operation);
        saved.setTransacao(transaction); saved.setSnapshotJson(canonical); saved.setInputHash(draft.getInputHash());
        saved.setProvider(draft.getProvider()); saved.setModel(draft.getModel());
        saved.setPromptVersion(draft.getPromptVersion()); saved.setSchemaVersion(draft.getSchemaVersion());
        saved.setCorrectionsJson("{}"); saved.setCreatedAt(LocalDateTime.now(clock));
        saved = confirmations.save(saved);
        draft.setStatus(AssistantDraftStatus.CONFIRMED); drafts.save(draft);
        ConfirmationResponse response = confirmation(saved);
        storeConfirmationReplay(usuarioId, draft, idempotencyKey, requestHash, response);
        return response;
    }

    @Transactional
    public void cancel(Long usuarioId, Long draftId) {
        cancel(usuarioId, draftId, null);
    }

    @Transactional
    public void cancel(Long usuarioId, Long draftId, String idempotencyKey) {
        String requestHash = hash("CANCEL\n" + draftId);
        if (idempotencyKey != null) {
            usuarios.findByIdComLock(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            if (mutationReplay.find(usuarioId, idempotencyKey, requestHash, String.class).isPresent()) return;
        }
        AssistantDraft draft = drafts.findOwnedForUpdate(draftId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Rascunho não encontrado"));
        if (draft.getStatus() == AssistantDraftStatus.CANCELLED) return;
        requirePendingAndFresh(draft);
        draft.setStatus(AssistantDraftStatus.CANCELLED);
        if (idempotencyKey != null) mutationReplay.store(usuarioId,
                draft.getConversation() == null ? null : draft.getConversation().getId(),
                "CANCEL_DRAFT", idempotencyKey, requestHash, "CANCELLED");
    }

    private AssistantDraft lockPending(Long usuarioId, Long id) {
        AssistantDraft draft = drafts.findOwnedForUpdate(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Rascunho não encontrado"));
        requirePendingAndFresh(draft);
        return draft;
    }

    private void requirePendingAndFresh(AssistantDraft draft) {
        if (draft.getStatus() != AssistantDraftStatus.PENDING) throw new AssistantException(
                "DRAFT_CONFLICT", "Rascunho já foi alterado", org.springframework.http.HttpStatus.CONFLICT);
        if (!draft.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            draft.setStatus(AssistantDraftStatus.EXPIRED);
            throw new AssistantException("DRAFT_EXPIRED", "Rascunho expirado", org.springframework.http.HttpStatus.GONE);
        }
    }

    private void requireVersion(AssistantDraft draft, Long expected) {
        if (expected == null || !expected.equals(draft.getVersion())) throw new AssistantException(
                "DRAFT_CONFLICT", "Versão do rascunho está desatualizada", org.springframework.http.HttpStatus.CONFLICT);
    }

    private AssistantConversation newConversation(Usuario usuario) {
        LocalDateTime now = LocalDateTime.now(clock);
        AssistantConversation c = new AssistantConversation(); c.setUsuario(usuario); c.setChannel("APP");
        c.setCreatedAt(now); c.setUpdatedAt(now); return conversations.save(c);
    }

    private AssistantMessage saveMessage(AssistantConversation c, Usuario u, String role, String content) {
        LocalDateTime now = LocalDateTime.now(clock);
        AssistantMessage m = new AssistantMessage(); m.setConversation(c); m.setUsuario(u); m.setRole(role);
        m.setContent(content); m.setCreatedAt(now); m.setExpiresAt(now.plusDays(30)); messages.save(m);
        c.setUpdatedAt(now);
        return m;
    }

    private MessageResponse storeReplay(AssistantMessage message, String idempotencyKey, String requestHash,
                                        MessageResponse response) {
        if (idempotencyKey == null) return response;
        try {
            message.setIdempotencyKey(idempotencyKey);
            message.setRequestHash(requestHash);
            message.setResponseJson(objectMapper.writeValueAsString(response));
            messages.save(message);
            return response;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao persistir replay idempotente", e);
        }
    }

    private MessageResponse readReplay(AssistantMessage message) {
        try {
            return objectMapper.readValue(message.getResponseJson(), MessageResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Replay idempotente inválido", e);
        }
    }

    private void storeConfirmationReplay(Long usuarioId, AssistantDraft draft, String key,
                                         String requestHash, ConfirmationResponse response) {
        if (key == null) return;
        mutationReplay.store(usuarioId,
                draft.getConversation() == null ? null : draft.getConversation().getId(),
                "CONFIRM_DRAFT", key, requestHash, response);
    }

    private AssistantDraft persistDraft(Usuario u, AssistantConversation c, FinancialParseResult parsed, String input,
                                        String provider, String model, String promptVersion) {
        TransactionDraftV1 value = parsed.draft(); LocalDateTime now = LocalDateTime.now(clock);
        AssistantDraft d = new AssistantDraft(); d.setUsuario(u); d.setConversation(c); d.setStatus(AssistantDraftStatus.PENDING);
        d.setTipo(value.tipo()); d.setValor(value.valor()); d.setDescricao(value.descricao()); d.setData(value.data());
        d.setCarteira(findWallet(u.getId(), value.contaNome())); d.setCategoria(findCategory(u.getId(), value.categoriaNome()));
        d.setConta(findCard(u.getId(), value.cartaoNome())); d.setParcelas(value.parcelas());
        d.setProvider(provider); d.setModel(model); d.setPromptVersion(promptVersion); d.setSchemaVersion(SCHEMA_VERSION);
        d.setQuestionCount((short) (parsed.outcome() == ParseOutcome.NEEDS_ONE_FIELD ? 1 : 0));
        d.setInputHash(OperacaoFinanceiraService.hashPayload(input)); d.setCreatedAt(now); d.setExpiresAt(now.plusHours(24));
        return drafts.save(d);
    }

    private Carteira findWallet(Long userId, String name) { return name == null ? null : carteiras.findByUsuarioIdAndNomeIgnoreCase(userId, name).orElse(null); }
    private Conta findCard(Long userId, String name) { return name == null ? null : contas.findByUsuarioIdAndNomeIgnoreCase(userId, name).orElse(null); }
    private Categoria findCategory(Long userId, String name) { return name == null ? null : categorias.findByUsuarioIdAndNomeIgnoreCase(userId, name).orElse(null); }
    private String trustedContext(Long userId) {
        String wallets = carteiras.findByUsuarioId(userId).stream().map(Carteira::getNome).sorted().reduce((a,b) -> a + ", " + b).orElse("");
        String cats = categorias.findByUsuarioIdAndAtivoTrue(userId).stream().map(Categoria::getNome).sorted().reduce((a,b) -> a + ", " + b).orElse("");
        String cards = contas.findByUsuarioIdAndAtivoTrue(userId).stream().map(Conta::getNome).sorted().reduce((a,b) -> a + ", " + b).orElse("");
        String value = "Contas permitidas: [" + wallets + "]\nCategorias permitidas: [" + cats + "]\nCartoes permitidos: [" + cards + "]";
        return value.substring(0, Math.min(value.length(), 8_000));
    }
    private FinancialParseResult classify(Long usuarioId, TransactionDraftV1 d) {
        List<String> missing = new ArrayList<>();
        if (d.tipo() == null) missing.add("tipo"); if (d.valor() == null || d.valor().signum() <= 0) missing.add("valor");
        if (d.descricao() == null || d.descricao().isBlank()) missing.add("descricao"); if (d.data() == null) missing.add("data");
        boolean card = d.cartaoNome() != null && !d.cartaoNome().isBlank()
                && contas.findByUsuarioIdAndNomeIgnoreCase(usuarioId, d.cartaoNome()).isPresent();
        // Cartão substitui a conta: a compra vive na fatura, não no saldo da carteira.
        if (!card && (d.contaNome() == null || d.contaNome().isBlank()
                || carteiras.findByUsuarioIdAndNomeIgnoreCase(usuarioId, d.contaNome()).isEmpty())) missing.add("contaNome");
        if (d.categoriaNome() == null || d.categoriaNome().isBlank()
                || categorias.findByUsuarioIdAndNomeIgnoreCase(usuarioId, d.categoriaNome()).isEmpty()) missing.add("categoriaNome");
        // Parcelar é privilégio do cartão, como no formulário manual.
        if (d.parcelas() != null && !card) missing.add("cartaoNome");
        Integer parcelas = d.parcelas() != null && d.parcelas() >= 2 && d.parcelas() <= 48 ? d.parcelas() : null;
        TransactionDraftV1 safe = new TransactionDraftV1("CREATE_TRANSACTION", d.tipo(), d.valor(), trim(d.descricao()), d.data(),
                card ? null : trim(d.contaNome()), trim(d.categoriaNome()), card ? trim(d.cartaoNome()) : null, parcelas, missing);
        ParseOutcome outcome = missing.isEmpty() ? ParseOutcome.COMPLETE : missing.size() == 1 ? ParseOutcome.NEEDS_ONE_FIELD : ParseOutcome.NEEDS_FORM;
        return new FinancialParseResult(outcome, safe, missing.size() == 1 ? question(missing.get(0)) : null);
    }

    private MessageResponse continueClarification(AssistantDraft draft, AssistantConversation conversation,
                                                   Usuario usuario, String answer) {
        List<String> before = missing(draft);
        boolean applied = before.size() == 1 && applyClarification(draft, before.get(0), answer, usuario.getId());
        List<String> after = missing(draft);
        ParseOutcome outcome = applied && after.isEmpty() ? ParseOutcome.COMPLETE : ParseOutcome.NEEDS_FORM;
        drafts.save(draft);
        String reply = outcome == ParseOutcome.COMPLETE
                ? "Rascunho pronto. Revise os dados antes de confirmar."
                : "Ainda há dúvida nesse lançamento. Complete o formulário para continuar.";
        saveMessage(conversation, usuario, "ASSISTANT", reply);
        return new MessageResponse(conversation.getId(), outcome, reply, response(draft, after));
    }

    private boolean applyClarification(AssistantDraft draft, String field, String raw, Long usuarioId) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return false;
        return switch (field) {
            case "valor" -> {
                BigDecimal parsed = clarificationAmount(value);
                if (parsed == null) yield false;
                draft.setValor(parsed); yield true;
            }
            case "tipo" -> {
                String normalized = RuleBasedFinancialInputParser.normalize(value);
                if (normalized.matches(".*\\b(entrou|entrada|recebi|ganhei)\\b.*")) draft.setTipo(TipoTransacao.ENTRADA);
                else if (normalized.matches(".*\\b(saiu|saida|gastei|paguei)\\b.*")) draft.setTipo(TipoTransacao.SAIDA);
                else yield false;
                yield true;
            }
            case "data" -> {
                LocalDate parsed = clarificationDate(value);
                if (parsed == null) yield false;
                draft.setData(parsed); yield true;
            }
            case "contaNome" -> carteiras.findByUsuarioIdAndNomeIgnoreCase(usuarioId, value)
                    .map(found -> { draft.setCarteira(found); return true; }).orElse(false);
            case "categoriaNome" -> categorias.findByUsuarioIdAndNomeIgnoreCase(usuarioId, value)
                    .map(found -> { draft.setCategoria(found); return true; }).orElse(false);
            case "descricao" -> {
                if (value.length() < 3 || value.length() > 500) yield false;
                draft.setDescricao(value); yield true;
            }
            default -> false;
        };
    }

    private BigDecimal clarificationAmount(String raw) {
        String value = raw.toLowerCase(java.util.Locale.ROOT).replace("r$", "").replaceAll("\\s", "");
        if (!value.matches("[0-9]+(?:[.,][0-9]{1,2})?")) return null;
        try {
            BigDecimal parsed = new BigDecimal(value.replace(',', '.')).setScale(2);
            return parsed.signum() > 0 ? parsed : null;
        } catch (NumberFormatException | ArithmeticException invalid) { return null; }
    }

    private LocalDate clarificationDate(String raw) {
        String value = RuleBasedFinancialInputParser.normalize(raw);
        if (value.equals("hoje")) return LocalDate.now(clock);
        if (value.equals("ontem")) return LocalDate.now(clock).minusDays(1);
        try { return LocalDate.parse(value); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(value, DateTimeFormatter.ofPattern("d/M/uuuu")); }
        catch (DateTimeParseException ignored) { return null; }
    }
    private String question(String field) { return switch (field) {
        case "tipo" -> "Esse valor entrou ou saiu?"; case "valor" -> "Qual foi o valor?";
        case "data" -> "Em que dia aconteceu?"; case "contaNome" -> "Qual conta você usou?";
        case "categoriaNome" -> "Qual categoria devo usar?"; case "cartaoNome" -> "Qual cartão você usou?";
        default -> "Como você descreve esse lançamento?";
    }; }
    private List<String> missing(AssistantDraft d) {
        List<String> m = new ArrayList<>(); if (d.getTipo() == null) m.add("tipo"); if (d.getValor() == null) m.add("valor");
        if (d.getDescricao() == null || d.getDescricao().isBlank()) m.add("descricao"); if (d.getData() == null) m.add("data");
        if (d.getCarteira() == null && d.getConta() == null) m.add("carteiraId");
        if (d.getCategoria() == null) m.add("categoriaId");
        if (d.getParcelas() != null && d.getConta() == null) m.add("cartaoId"); return m;
    }

    private DraftResponse response(AssistantDraft d, List<String> missing) {
        return new DraftResponse(d.getId(), d.getVersion(), d.getTipo(), d.getValor(), d.getDescricao(), d.getData(),
                d.getCarteira() == null ? null : d.getCarteira().getId(), d.getCategoria() == null ? null : d.getCategoria().getId(),
                d.getConta() == null ? null : d.getConta().getId(), d.getParcelas(), missing, d.getExpiresAt());
    }
    private ConfirmationResponse confirmation(AssistantConfirmation c) { return new ConfirmationResponse(c.getId(), c.getDraftId(), c.getOperacao().getId(), c.getTransacao().getId(), c.getCreatedAt()); }
    private String canonical(AssistantDraft d) {
        try { return objectMapper.writeValueAsString(new TransactionDraftV1("CREATE_TRANSACTION", d.getTipo(), d.getValor(),
                d.getDescricao(), d.getData(), d.getCarteira() == null ? null : d.getCarteira().getNome(), d.getCategoria().getNome(),
                d.getConta() == null ? null : d.getConta().getNome(), d.getParcelas(), List.of())); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao serializar snapshot", e); }
    }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao serializar mutação", e); }
    }
    private String hash(String value) { return OperacaoFinanceiraService.hashPayload(value); }
}

import React, { useEffect, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { AudioModule, RecordingPresets, setAudioModeAsync, useAudioRecorder, useAudioRecorderState } from 'expo-audio';
import { File } from 'expo-file-system';
import { useTheme, useTabBarSpace, radius, screenPadding, spacing, typography } from '../../../src/theme';
import { mensagemDeErro } from '../../../src/utils/erros';
import assistantService, { assistantIdempotencyKey, AssistantDraft } from '../../../src/services/assistantService';
import { useCapacidades } from '../../../src/hooks/useCapacidades';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Botao from '../../../src/components/ui/Botao';
import Field from '../../../src/components/ui/Field';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import NovaTransacaoModal, { LancamentoInicial } from '../../../src/components/NovaTransacaoModal';

type LocalMessage = { id: string; role: 'USER' | 'ASSISTANT'; text: string };

const starter: LocalMessage = {
  id: 'starter',
  role: 'ASSISTANT',
  text: 'Conte um lançamento do seu jeito. Por exemplo: “gasolina 85 no Nubank hoje”. Você sempre revisa antes de salvar.',
};

export default function AssistenteScreen() {
  const colors = useTheme();
  const bottomSpace = useTabBarSpace();
  const router = useRouter();
  const scroll = useRef<ScrollView>(null);
  const capacidades = useCapacidades();
  const [messages, setMessages] = useState<LocalMessage[]>([starter]);
  const [text, setText] = useState('');
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<AssistantDraft | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [whatsappCode, setWhatsappCode] = useState<string | null>(null);
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recording = useAudioRecorderState(recorder, 250);
  const submittedAudio = useRef<string | null>(null);
  const pendingMessage = useRef<{ text: string; conversationId: number | null; key: string } | null>(null);

  useEffect(() => () => {
    if (recorder.isRecording) void recorder.stop();
  }, [recorder]);

  useEffect(() => {
    if (!recording.isRecording && recording.durationMillis >= 60_000 && recorder.uri
        && submittedAudio.current !== recorder.uri) void sendRecordedAudio(recorder.uri);
  }, [recording.isRecording, recording.durationMillis, recorder.uri]);

  const review = (candidate: AssistantDraft | null) => {
    if (candidate) { setDraft(candidate); setReviewing(true); }
  };

  const send = async () => {
    const content = text.trim();
    if (!content || sending) return;
    setText(''); setError(null); setSending(true);
    setMessages(current => [...current, { id: `user-${Date.now()}`, role: 'USER', text: content }]);
    try {
      const pending = pendingMessage.current?.text === content
        && pendingMessage.current.conversationId === conversationId
        ? pendingMessage.current
        : { text: content, conversationId, key: assistantIdempotencyKey('message') };
      pendingMessage.current = pending;
      const response = await assistantService.sendMessage(content, conversationId, pending.key);
      pendingMessage.current = null;
      setConversationId(response.conversationId);
      setMessages(current => [...current, { id: `assistant-${Date.now()}`, role: 'ASSISTANT', text: response.reply }]);
      setDraft(response.draft);
      if (response.outcome === 'COMPLETE' || response.outcome === 'NEEDS_FORM') review(response.draft);
    } catch (err) {
      setError(mensagemDeErro(err, 'Não foi possível enviar. Tente novamente.'));
      setText(content);
    } finally {
      setSending(false);
      requestAnimationFrame(() => scroll.current?.scrollToEnd({ animated: true }));
    }
  };

  const applyResponse = (response: Awaited<ReturnType<typeof assistantService.sendMessage>>, transcript?: string) => {
    setConversationId(response.conversationId);
    setMessages(current => [
      ...current,
      ...(transcript ? [{ id: `user-audio-${Date.now()}`, role: 'USER' as const, text: transcript }] : []),
      { id: `assistant-${Date.now()}`, role: 'ASSISTANT' as const, text: response.reply },
    ]);
    setDraft(response.draft);
    if (response.outcome === 'COMPLETE' || response.outcome === 'NEEDS_FORM') review(response.draft);
  };

  const sendRecordedAudio = async (uri: string) => {
    if (submittedAudio.current === uri) return;
    submittedAudio.current = uri;
    setSending(true);
    try {
      const response = await assistantService.transcribeAudio(uri, conversationId);
      applyResponse(response.message, response.transcript);
    } catch (err) {
      submittedAudio.current = null;
      setError(mensagemDeErro(err, 'Não foi possível transcrever. Grave novamente.'));
    } finally {
      try {
        const recorded = new File(uri);
        if (recorded.exists) recorded.delete();
      } catch { /* arquivo de cache pode já ter sido removido pelo sistema */ }
      setSending(false);
      requestAnimationFrame(() => scroll.current?.scrollToEnd({ animated: true }));
    }
  };

  const toggleRecording = async () => {
    if (sending) return;
    setError(null);
    try {
      if (!recording.isRecording) {
        const permission = await AudioModule.requestRecordingPermissionsAsync();
        if (!permission.granted) { setError('Permita o uso do microfone para gravar um lançamento.'); return; }
        submittedAudio.current = null;
        await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
        await recorder.prepareToRecordAsync();
        recorder.record({ forDuration: 60 });
        return;
      }
      await recorder.stop();
      await setAudioModeAsync({ allowsRecording: false });
      if (!recorder.uri) { setError('Não foi possível concluir a gravação. Tente novamente.'); return; }
      await sendRecordedAudio(recorder.uri);
    } catch (err) {
      setError(mensagemDeErro(err, 'Não foi possível usar o microfone. Tente novamente.'));
    }
  };

  const connectWhatsapp = async () => {
    setError(null); setSending(true);
    try { setWhatsappCode((await assistantService.createWhatsappLink()).code); }
    catch (err) { setError(mensagemDeErro(err, 'Não foi possível iniciar o vínculo com o WhatsApp.')); }
    finally { setSending(false); }
  };

  const initialData: LancamentoInicial | null = draft ? {
    descricao: draft.descricao ?? '', valor: draft.valor ?? 0, tipo: draft.tipo ?? 'SAIDA',
    categoriaId: draft.categoriaId ?? undefined, carteiraId: draft.carteiraId ?? undefined,
    cartaoId: draft.cartaoId ?? undefined, parcelas: draft.parcelas ?? undefined,
    data: draft.data ?? undefined, mode: 'ASSISTANT_DRAFT', draftId: draft.id, draftVersion: draft.version,
  } : null;

  // O tile em Ajustes já respeita a capacidade, mas a rota continua alcançável por deep link.
  // Sem esta guarda o canal desligado só se revelaria como 404 travestido de "não foi possível
  // enviar", depois da pessoa ter digitado a frase inteira.
  if (!capacidades.assistenteTexto) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <CabecalhoSubTela titulo="Assistente" />
        <EstadoVazio
          emoji="💬"
          titulo="Assistente indisponível"
          texto="Este recurso ainda não está ligado na sua conta. Continue lançando pelo formulário."
        />
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.bg }}
      behavior={Platform.select({ ios: 'padding', android: 'height' })}
    >
      <ScrollView
        ref={scroll}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ paddingBottom: bottomSpace, paddingHorizontal: screenPadding }}
      >
        <CabecalhoSubTela
          titulo="Assistente"
          apoio={<Text style={{ ...typography.body, color: colors.textSecondary }}>Lançamentos por texto, sempre com revisão</Text>}
        />

        <View accessibilityRole="list" style={{ gap: spacing.md }}>
          {messages.map(message => (
            <View
              key={message.id}
              accessibilityRole="text"
              style={{
                alignSelf: message.role === 'USER' ? 'flex-end' : 'flex-start',
                maxWidth: '88%', paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
                borderRadius: radius.lg,
                backgroundColor: message.role === 'USER' ? colors.brandBg : colors.card,
              }}
            >
              <Text style={{ ...typography.body, color: message.role === 'USER' ? colors.brandFg : colors.textPrimary }}>
                {message.text}
              </Text>
            </View>
          ))}
        </View>

        {draft && draft.missingFields.length > 0 ? (
          <View style={{ marginTop: spacing.lg }}>
            <Botao titulo="Completar no formulário" variante="secundario" onPress={() => review(draft)} />
          </View>
        ) : null}

        <View style={{ marginTop: spacing.xxl }}>
          <Field
            testID="assistant-message"
            label="Mensagem"
            value={text}
            onChangeText={setText}
            placeholder="Ex: mercado 50 ontem"
            multiline
            style={{ minHeight: spacing.xxxl * 3, textAlignVertical: 'top' }}
            error={error}
            editable={!sending}
            maxLength={2000}
          />
          <Botao testID="assistant-send" titulo="Enviar" icone="arrow-up" onPress={send} carregando={sending} desabilitado={!text.trim()} />
          <View style={{ marginTop: spacing.sm }}>
            <Botao
              testID="assistant-audio"
              titulo={recording.isRecording ? `Parar gravação · ${Math.ceil(recording.durationMillis / 1000)}s` : 'Gravar áudio'}
              icone={recording.isRecording ? 'stop' : 'mic'}
              variante="secundario"
              onPress={toggleRecording}
              desabilitado={sending}
            />
          </View>
          <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.sm, textAlign: 'center' }}>
            O assistente prepara o rascunho. Nada é lançado sem sua confirmação.
          </Text>
          {capacidades.assistenteWhatsapp ? (
            <View style={{ marginTop: spacing.xxl, gap: spacing.sm }}>
              <Botao titulo="Conectar WhatsApp" icone="logo-whatsapp" variante="secundario" onPress={connectWhatsapp} desabilitado={sending} />
              {whatsappCode ? (
                <View style={{ padding: spacing.lg, borderRadius: radius.lg, backgroundColor: colors.brandBg }}>
                  <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Envie este código ao WhatsApp do Nexos</Text>
                  <Text selectable style={{ ...typography.section, color: colors.brandFg, marginTop: spacing.sm }}>{whatsappCode}</Text>
                  <Text style={{ ...typography.meta, color: colors.textSecondary, marginTop: spacing.sm }}>
                    Uso único, válido por 10 minutos. O vínculo acontece sem revelar se outro número possui conta.
                  </Text>
                </View>
              ) : null}
            </View>
          ) : null}
        </View>
      </ScrollView>

      <NovaTransacaoModal
        visible={reviewing}
        initialData={initialData}
        onClose={() => setReviewing(false)}
        onSaved={() => { setDraft(null); setReviewing(false); router.replace('/transacoes'); }}
      />
    </KeyboardAvoidingView>
  );
}

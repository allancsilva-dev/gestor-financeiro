import React, { useMemo, useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import {
  useTheme, useTabBarSpace, numeric, radius, screenPadding, spacing, typography,
} from '../../../src/theme';
import importacaoService from '../../../src/services/importacaoService';
import contaFinanceiraService from '../../../src/services/contaFinanceiraService';
import { ImportBatch, ImportRecord } from '../../../src/types';
import { formatCurrency, formatDateOnlyBR } from '../../../src/utils/format';
import { mensagemDeErro } from '../../../src/utils/erros';
import Badge from '../../../src/components/ui/Badge';
import Botao from '../../../src/components/ui/Botao';
import CabecalhoSubTela from '../../../src/components/ui/CabecalhoSubTela';
import Card from '../../../src/components/ui/Card';
import Chip from '../../../src/components/ui/Chip';
import EstadoVazio from '../../../src/components/ui/EstadoVazio';
import SkeletonBox from '../../../src/components/ui/SkeletonBox';

/**
 * O SAF do Android filtra pelo MIME declarado pelo provedor, e extrato vindo de
 * Downloads, Drive ou WhatsApp chega como octet-stream ou text/plain: com filtro
 * estrito o arquivo aparece cinza e não dá para tocar. Abre-se o filtro e
 * revalida-se pela extensão. Mesmo padrão de `ajustes.tsx`.
 */
const TIPOS_ACEITOS = [
  'text/csv',
  'text/comma-separated-values',
  'application/vnd.ms-excel',
  'application/x-ofx',
  'application/xml',
  'text/xml',
  'text/plain',
  'application/octet-stream',
];

const EXTENSOES = ['csv', 'ofx'];

/** Motivos vêm do backend em enum fechado; aqui viram frase de gente. */
const MOTIVO: Record<string, string> = {
  DATE_MISSING: 'sem data',
  DATE_INVALID: 'data inválida',
  DATE_AMBIGUOUS: 'data ambígua (dia ou mês?)',
  AMOUNT_MISSING: 'sem valor',
  AMOUNT_INVALID: 'valor inválido',
  AMOUNT_AMBIGUOUS: 'valor ambíguo',
  AMOUNT_ROUNDING_REQUIRED: 'valor com mais de dois decimais',
  CURRENCY_MISSING: 'sem moeda',
  CURRENCY_INVALID: 'moeda inválida',
  CURRENCY_UNSUPPORTED: 'moeda não suportada',
  DIRECTION_MISSING: 'sem entrada/saída',
  DIRECTION_INVALID: 'entrada/saída não reconhecida',
  DIRECTION_CONFLICT: 'sinal do valor conflita com entrada/saída',
  DESCRIPTION_MISSING: 'sem descrição',
  DESCRIPTION_INVALID: 'descrição inválida',
  EXTERNAL_ID_INVALID: 'identificador inválido',
  COMMIT_FAILED: 'não foi possível lançar',
  MULTIPLE_ISSUES: 'mais de um problema',
};

const emAndamento = (lote: ImportBatch | null) => lote?.status === 'COMMITTING';
const revisavel = (lote: ImportBatch | null) =>
  lote != null && ['PARSED', 'PENDING_REVIEW', 'READY_TO_COMMIT'].includes(lote.status);

export default function ImportacaoScreen() {
  const colors = useTheme();
  const tabBarSpace = useTabBarSpace();
  const queryClient = useQueryClient();

  const [loteId, setLoteId] = useState<number | null>(null);
  const [contaId, setContaId] = useState<number | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const { data: contas = [] } = useQuery({
    queryKey: ['contas-para-importacao'],
    queryFn: () => contaFinanceiraService.listarParaCaixa(),
  });

  const { data: lote = null } = useQuery<ImportBatch | null>({
    queryKey: ['importacao', loteId],
    queryFn: () => (loteId ? importacaoService.consultar(loteId) : Promise.resolve(null)),
    enabled: loteId != null,
    // Enquanto o lançamento roda na fila, a tela acompanha sozinha.
    refetchInterval: (query) => (query.state.data?.status === 'COMMITTING' ? 2000 : false),
  });

  const { data: previa, isLoading: carregandoPrevia } = useQuery({
    queryKey: ['importacao-registros', loteId, lote?.status],
    queryFn: () => importacaoService.registros(loteId as number, { tamanho: 50 }),
    enabled: loteId != null && lote != null,
  });

  const { data: historico } = useQuery({
    queryKey: ['importacoes'],
    queryFn: () => importacaoService.historico(0, 5),
  });

  const registros = previa?.registros ?? [];
  const precisamDecisao = useMemo(
    () => registros.filter((r) => r.status === 'PENDING_REVIEW' || r.status === 'DUPLICATE'),
    [registros],
  );
  const prontos = (lote?.validRecords ?? 0)
    + registros.filter((r) => r.status === 'APPROVED').length;

  const falhar = (err: unknown) => setErro(mensagemDeErro(err));

  const escolherArquivo = async () => {
    setErro(null);
    try {
      const escolha = await DocumentPicker.getDocumentAsync({
        type: TIPOS_ACEITOS,
        multiple: false,
        copyToCacheDirectory: true,
      });
      if (escolha.canceled || !escolha.assets?.[0]) return;
      const arquivo = escolha.assets[0];
      const extensao = (arquivo.name?.split('.').pop() ?? '').toLowerCase();
      if (!EXTENSOES.includes(extensao)) {
        setErro('Selecione um arquivo .csv ou .ofx.');
        return;
      }
      setEnviando(true);
      const novo = await importacaoService.enviar({
        uri: arquivo.uri,
        name: arquivo.name || 'extrato.csv',
        type: arquivo.mimeType || 'text/csv',
      });
      setLoteId(novo.id);
      setContaId(null);
      queryClient.setQueryData(['importacao', novo.id], novo);
      queryClient.invalidateQueries({ queryKey: ['importacoes'] });
    } catch (err) {
      falhar(err);
    } finally {
      setEnviando(false);
    }
  };

  const preparar = useMutation({
    mutationFn: (conta: number) => importacaoService.preparar(loteId as number, conta),
    onSuccess: (atualizado) => {
      setErro(null);
      setContaId(atualizado.id === loteId ? contaId : contaId);
      queryClient.setQueryData(['importacao', loteId], atualizado);
    },
    onError: falhar,
  });

  const aprovar = useMutation({
    mutationFn: (registroId: number) => importacaoService.aprovar(loteId as number, registroId),
    onSuccess: () => {
      setErro(null);
      queryClient.invalidateQueries({ queryKey: ['importacao-registros', loteId] });
      queryClient.invalidateQueries({ queryKey: ['importacao', loteId] });
    },
    onError: falhar,
  });

  const lancar = useMutation({
    mutationFn: () => importacaoService.lancar(loteId as number),
    onSuccess: (atualizado) => {
      setErro(null);
      queryClient.setQueryData(['importacao', loteId], atualizado);
    },
    onError: falhar,
  });

  const reverter = useMutation({
    mutationFn: (id: number) => importacaoService.reverter(id),
    onSuccess: () => {
      setErro(null);
      queryClient.invalidateQueries();
    },
    onError: falhar,
  });

  const confirmarReversao = (id: number) => {
    Alert.alert(
      'Desfazer importação',
      'Os lançamentos desta importação saem do extrato e o saldo volta ao valor anterior. O histórico da importação continua guardado.',
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Desfazer', style: 'destructive', onPress: () => reverter.mutate(id) },
      ],
    );
  };

  const escolherConta = (id: number) => {
    setContaId(id);
    preparar.mutate(id);
  };

  const recomecar = () => {
    setLoteId(null);
    setContaId(null);
    setErro(null);
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <CabecalhoSubTela
        titulo="Importar extrato"
        apoio={
          <Text style={{ ...typography.body, color: colors.textMuted }}>
            CSV ou OFX. Você revisa antes de qualquer coisa entrar no extrato.
          </Text>
        }
      />

      <ScrollView
        contentContainerStyle={{ padding: screenPadding, paddingBottom: tabBarSpace, gap: spacing.lg }}
        keyboardShouldPersistTaps="handled"
      >
        {erro && (
          <Card>
            <Text style={{ ...typography.body, color: colors.danger }}>{erro}</Text>
          </Card>
        )}

        {loteId == null && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Escolher arquivo</Text>
            <Text style={{ ...typography.body, color: colors.textMuted, marginTop: spacing.xs }}>
              O arquivo vira uma prévia: você confere linha a linha, escolhe a conta e só então
              confirma. Repetir o mesmo arquivo não duplica lançamento.
            </Text>
            <View style={{ marginTop: spacing.lg }}>
              <Botao
                titulo="Escolher arquivo"
                icone="document-attach-outline"
                onPress={escolherArquivo}
                carregando={enviando}
                testID="importacao-escolher-arquivo"
              />
            </View>
          </Card>
        )}

        {lote && (
          <Card>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>
                {lote.totalRecords} linha{lote.totalRecords === 1 ? '' : 's'} lida{lote.totalRecords === 1 ? '' : 's'}
              </Text>
              <Badge tone={lote.status === 'COMMITTED' ? 'success' : lote.status === 'FAILED' ? 'danger' : 'info'}>
                {rotuloDoStatus(lote.status)}
              </Badge>
            </View>

            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, marginTop: spacing.md }}>
              <Contagem rotulo="Prontas" valor={prontos} cor={colors.success} />
              <Contagem rotulo="Em revisão" valor={lote.pendingReviewRecords} cor={colors.warning} />
              <Contagem rotulo="Repetidas" valor={lote.duplicateRecords} cor={colors.textMuted} />
              <Contagem rotulo="Com erro" valor={lote.invalidRecords} cor={colors.danger} />
            </View>
          </Card>
        )}

        {revisavel(lote) && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Em qual conta entra?</Text>
            {contas.length === 0 ? (
              <EstadoVazio
                compacto
                emoji="🏦"
                titulo="Nenhuma conta disponível"
                texto="Crie uma conta de caixa antes de importar."
              />
            ) : (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.md }}>
                {contas.map((conta) => (
                  <Chip
                    key={conta.id}
                    label={conta.nome}
                    selected={contaId === conta.id}
                    onPress={() => escolherConta(conta.id)}
                  />
                ))}
              </View>
            )}
          </Card>
        )}

        {revisavel(lote) && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>
              {precisamDecisao.length > 0 ? 'Precisam da sua decisão' : 'Prévia'}
            </Text>
            {carregandoPrevia ? (
              <View style={{ gap: spacing.sm, marginTop: spacing.md }}>
                <SkeletonBox width="100%" height={56} />
                <SkeletonBox width="100%" height={56} />
              </View>
            ) : registros.length === 0 ? (
              <EstadoVazio compacto emoji="📄" titulo="Nada para revisar" />
            ) : (
              <View style={{ marginTop: spacing.md }}>
                {(precisamDecisao.length > 0 ? precisamDecisao : registros).map((registro) => (
                  <LinhaDoExtrato
                    key={registro.id}
                    registro={registro}
                    onAprovar={() => aprovar.mutate(registro.id)}
                    aprovando={aprovar.isPending}
                  />
                ))}
              </View>
            )}
          </Card>
        )}

        {revisavel(lote) && (
          <Botao
            titulo={prontos > 0 ? `Lançar ${prontos} no extrato` : 'Nada pronto para lançar'}
            onPress={() => lancar.mutate()}
            carregando={lancar.isPending}
            desabilitado={prontos === 0 || lote?.status !== 'READY_TO_COMMIT'}
            testID="importacao-lancar"
            dica={lote?.status !== 'READY_TO_COMMIT' ? 'Escolha a conta de destino primeiro' : undefined}
          />
        )}

        {emAndamento(lote) && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Lançando…</Text>
            <Text style={{ ...typography.body, color: colors.textMuted, marginTop: spacing.xs }}>
              O lançamento roda em segundo plano. Pode sair desta tela: o extrato atualiza sozinho.
            </Text>
            <View style={{ marginTop: spacing.md }}>
              <SkeletonBox width="100%" height={44} />
            </View>
          </Card>
        )}

        {lote?.status === 'COMMITTED' && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Importação concluída</Text>
            <Text style={{ ...typography.body, color: colors.textMuted, marginTop: spacing.xs }}>
              Os lançamentos já estão no extrato. Se algo ficou errado, dá para desfazer tudo de uma vez.
            </Text>
            <View style={{ gap: spacing.sm, marginTop: spacing.lg }}>
              <Botao titulo="Importar outro arquivo" onPress={recomecar} />
              <Botao
                titulo="Desfazer importação"
                variante="perigo"
                onPress={() => confirmarReversao(lote.id)}
                carregando={reverter.isPending}
              />
            </View>
          </Card>
        )}

        {lote?.status === 'FAILED' && (
          <Card>
            <EstadoVazio
              compacto
              emoji="⚠️"
              titulo="Não deu para ler o arquivo"
              texto={MOTIVO[lote.failureCode ?? ''] ?? 'Confira se o arquivo é um extrato CSV ou OFX.'}
              acao={{ rotulo: 'Escolher outro arquivo', onPress: recomecar }}
            />
          </Card>
        )}

        {(historico?.content?.length ?? 0) > 0 && (
          <Card>
            <Text style={{ ...typography.cardTitle, color: colors.textPrimary }}>Importações anteriores</Text>
            <View style={{ marginTop: spacing.md, gap: spacing.md }}>
              {(historico?.content ?? []).map((item) => (
                <View
                  key={item.id}
                  style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md }}
                >
                  <View style={{ flex: 1 }}>
                    <Text style={{ ...typography.rowTitle, color: colors.textPrimary }}>
                      {item.format} · {item.totalRecords} linha{item.totalRecords === 1 ? '' : 's'}
                    </Text>
                    <Text style={{ ...typography.meta, color: colors.textMuted }}>
                      {formatDateOnlyBR(item.createdAt)} · {rotuloDoStatus(item.status)}
                    </Text>
                  </View>
                  {item.status === 'COMMITTED' && (
                    <Botao
                      titulo="Desfazer"
                      variante="texto"
                      tamanho="pill"
                      onPress={() => confirmarReversao(item.id)}
                    />
                  )}
                </View>
              ))}
            </View>
          </Card>
        )}
      </ScrollView>
    </View>
  );
}

function rotuloDoStatus(status: ImportBatch['status']): string {
  switch (status) {
    case 'RECEIVED': return 'Recebido';
    case 'PARSED': return 'Lido';
    case 'PENDING_REVIEW': return 'Em revisão';
    case 'READY_TO_COMMIT': return 'Pronto';
    case 'COMMITTING': return 'Lançando';
    case 'COMMITTED': return 'Lançado';
    case 'REVERSED': return 'Desfeito';
    case 'FAILED': return 'Falhou';
    default: return status;
  }
}

function Contagem({ rotulo, valor, cor }: { rotulo: string; valor: number; cor: string }) {
  const colors = useTheme();
  return (
    <View style={{ minWidth: 76 }}>
      <Text style={{ ...typography.value, ...numeric, color: cor }}>{valor}</Text>
      <Text style={{ ...typography.meta, color: colors.textMuted }}>{rotulo}</Text>
    </View>
  );
}

function LinhaDoExtrato({
  registro,
  onAprovar,
  aprovando,
}: {
  registro: ImportRecord;
  onAprovar: () => void;
  aprovando: boolean;
}) {
  const colors = useTheme();
  const saida = registro.direction === 'SAIDA';
  const valor = registro.amount == null ? '—' : formatCurrency(registro.amount);
  const decidivel = registro.status === 'PENDING_REVIEW' || registro.status === 'DUPLICATE';

  return (
    <View
      style={{
        paddingVertical: spacing.md,
        borderTopWidth: 1,
        borderTopColor: colors.border,
        gap: spacing.xs,
      }}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md }}>
        <Text style={{ ...typography.rowTitle, color: colors.textPrimary, flex: 1 }} numberOfLines={1}>
          {registro.description ?? 'Sem descrição'}
        </Text>
        <Text style={{ ...typography.value, ...numeric, color: saida ? colors.danger : colors.success }}>
          {saida ? '-' : '+'}{valor}
        </Text>
      </View>

      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
        <Text style={{ ...typography.meta, color: colors.textMuted }}>
          {registro.occurredOn ? formatDateOnlyBR(registro.occurredOn) : 'sem data'}
        </Text>
        {registro.status === 'DUPLICATE' && <Badge tone="warning">Já importado antes</Badge>}
        {registro.reasonCode && (
          <Text style={{ ...typography.meta, color: colors.warning }}>
            {MOTIVO[registro.reasonCode] ?? registro.reasonCode}
          </Text>
        )}
      </View>

      {decidivel && (
        <View style={{ alignSelf: 'flex-start', marginTop: spacing.xs }}>
          <Botao
            titulo="Trazer mesmo assim"
            variante="secundario"
            tamanho="pill"
            onPress={onAprovar}
            carregando={aprovando}
            accessibilityLabel={`Trazer ${registro.description ?? 'lançamento'} para o extrato`}
          />
        </View>
      )}
    </View>
  );
}

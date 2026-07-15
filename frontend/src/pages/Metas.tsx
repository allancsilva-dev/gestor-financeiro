import { useState, useEffect } from 'react';
import { metaService, Meta, StatusMeta } from '../services/metaService';
import carteiraService, { Carteira } from '../services/carteiraService';
import { useAuth } from '../context/AuthContext';
import { useApi } from '../hooks/useApi';
import toast from 'react-hot-toast';
import Layout from '../components/Layout';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';
import IconPicker from '../components/IconPicker';
import FieldError from '../components/FieldError';
import { fieldA11y } from '../validation/fieldA11y';
import { useZodForm } from '../hooks/useZodForm';
import { aporteMetaSchema, metaSchema } from '../validation/schemas';
import { toNullableNumber } from '../validation/numbers';
import { acoesDaMeta } from '../domain/metaPolicy';

export default function Metas() {
  const { usuario } = useAuth();
  const [metas, setMetas] = useState<Meta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [mostrarAdicionar, setMostrarAdicionar] = useState<number | null>(null);
  const [mostrarResgatar, setMostrarResgatar] = useState<number | null>(null);
  const [statusFiltro, setStatusFiltro] = useState<StatusMeta>('ATIVA');
  const [loading, setLoading] = useState(false);
  const [acaoFinanceiraId, setAcaoFinanceiraId] = useState<string | null>(null);
  const [editando, setEditando] = useState<Meta | null>(null);
  const [paginaAtual, setPaginaAtual] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(1);
  const tamanhoPagina = 20;
  
  const [formData, setFormData] = useState({
    nome: '',
    valorTotal: '',
    valorMensal: '',
    cor: '#3498DB',
    icone: 'target',
    descricao: ''
  });

  const [valorAdicionar, setValorAdicionar] = useState('');
  const [carteiras, setCarteiras] = useState<Carteira[]>([]);
  const [carteiraOrigem, setCarteiraOrigem] = useState('');
  const valorTotalNumerico = toNullableNumber(formData.valorTotal);
  const valorMensalNumerico = toNullableNumber(formData.valorMensal);
  const valorAdicionarNumerico = toNullableNumber(valorAdicionar);
  const [previsaoMesesManual, setPrevisaoMesesManual] = useState('');
  const [usarPrevisaoManual, setUsarPrevisaoManual] = useState(false);
  const formValidation = useZodForm(metaSchema);
  const aporteValidation = useZodForm(aporteMetaSchema);

  const previsaoCalculada =
    valorTotalNumerico && valorMensalNumerico && valorMensalNumerico > 0
      ? Math.ceil(valorTotalNumerico / valorMensalNumerico)
      : null;

  const previsaoMeses = usarPrevisaoManual
    ? toNullableNumber(previsaoMesesManual)
    : previsaoCalculada;

  const {
    data: metasPaginadas,
    loading: loadingLista,
    error: erroLista,
    refetch,
  } = useApi(
    (signal) => {
      if (!usuario?.id) {
        return Promise.resolve(null as any);
      }
      return metaService.listarPorUsuarioPaginado(paginaAtual, tamanhoPagina, signal, statusFiltro);
    },
    {
      immediate: !!usuario?.id,
      deps: [usuario?.id, paginaAtual, statusFiltro],
    }
  );

  useEffect(() => {
    if (metasPaginadas) {
      setMetas(metasPaginadas.content || []);
      setTotalPaginas(Math.max(metasPaginadas.totalPages || 1, 1));
    }
  }, [metasPaginadas]);

  useEffect(() => {
    if (!usuario?.id) return;
    carteiraService.listarCarteiras(usuario.id, 0, 100)
      .then(setCarteiras)
      .catch(() => toast.error('Erro ao carregar contas'));
  }, [usuario?.id]);

  useEffect(() => {
    if (erroLista) {
      const erroApi = erroLista as any;
      const mensagem = erroApi?.userMessage || erroApi?.response?.data?.message || 'Erro ao carregar metas';
      toast.error(mensagem);
    }
  }, [erroLista]);

  const carregarMetas = async () => {
    if (!usuario?.id) {
      return;
    }

    await refetch();
  };

  const abrirFormulario = (meta?: Meta) => {
    if (meta) {
      // Modo edição
      setEditando(meta);
      setFormData({
        nome: meta.nome,
        valorTotal: meta.valorTotal?.toString() || '',
        valorMensal: meta.valorMensal?.toString() || '',
        cor: meta.cor || '#3498DB',
        icone: meta.icone || '🎯',
        descricao: meta.descricao || ''
      });
      setUsarPrevisaoManual(false);
      setPrevisaoMesesManual('');
    } else {
      // Modo criação
      resetarFormulario();
    }
    setMostrarForm(true);
  };

  const resetarFormulario = () => {
    setEditando(null);
    setFormData({
      nome: '',
      valorTotal: '',
      valorMensal: '',
      cor: '#3498DB',
      icone: '🎯',
      descricao: ''
    });
    setUsarPrevisaoManual(false);
    setPrevisaoMesesManual('');
    formValidation.resetValidation();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!usuario?.id) {
      toast.error('Usuário não autenticado');
      return;
    }
    const parsed = formValidation.validate(formData);
    if (!parsed) return;

    try {
      setLoading(true);
      
      const metaParaEnviar = {
        nome: parsed.nome,
        valorTotal: parsed.valorTotal,
        valorMensal: parsed.valorMensal,
        cor: parsed.cor,
        icone: parsed.icone,
        descricao: parsed.descricao,
      };
      
      if (editando) {
        // Atualizar
        await metaService.atualizar(editando.id!, metaParaEnviar);
        toast.success('Meta atualizada com sucesso!');
      } else {
        // Criar
        await metaService.criar(metaParaEnviar);
        toast.success('Meta criada com sucesso!');
      }
      
      resetarFormulario();
      setMostrarForm(false);
      carregarMetas();
    } catch (error: any) {
      toast.error(editando ? 'Erro ao atualizar meta' : 'Erro ao criar meta');
    } finally {
      setLoading(false);
    }
  };

  const handleAdicionarValor = async (metaId: number) => {
    const actionKey = `adicionar:${metaId}`;
    if (acaoFinanceiraId) return;
    const aporte = aporteValidation.validate({ valor: valorAdicionar, carteiraId: carteiraOrigem });
    if (!aporte) return;

    try {
      setAcaoFinanceiraId(actionKey);
      await metaService.adicionarValor(metaId, aporte.valor, aporte.carteiraId);
      toast.success('Valor adicionado!');
      setValorAdicionar('');
      setCarteiraOrigem('');
      setMostrarAdicionar(null);
      aporteValidation.resetValidation();
      carregarMetas();
    } catch (error: any) {
      toast.error(error?.response?.data?.message ?? 'Erro ao adicionar valor');
    } finally {
      setAcaoFinanceiraId(null);
    }
  };

  const handleDeletar = async (id: number) => {
    if (!window.confirm('Tem certeza que deseja excluir? A meta vai para "Arquivadas".')) return;

    try {
      await metaService.deletar(id);
      toast.success('Meta arquivada!');
      carregarMetas();
    } catch (error: any) {
      // backend bloqueia exclusão com valor reservado: resgate primeiro (ADR-0004)
      toast.error(error?.response?.data?.message ?? 'Erro ao excluir meta');
    }
  };

  const handleResgatarValor = async (metaId: number) => {
    const actionKey = `resgatar:${metaId}`;
    if (acaoFinanceiraId) return;
    const resgate = aporteValidation.validate({ valor: valorAdicionar, carteiraId: carteiraOrigem });
    if (!resgate) return;

    try {
      setAcaoFinanceiraId(actionKey);
      await metaService.removerValor(metaId, resgate.valor, resgate.carteiraId);
      toast.success('Valor resgatado para a conta!');
      setValorAdicionar('');
      setCarteiraOrigem('');
      setMostrarResgatar(null);
      aporteValidation.resetValidation();
      carregarMetas();
    } catch (error: any) {
      toast.error(error?.response?.data?.message ?? 'Erro ao resgatar valor');
    } finally {
      setAcaoFinanceiraId(null);
    }
  };

  const calcularProgresso = (meta: Meta) => {
    return ((meta.valorReservado || 0) / meta.valorTotal) * 100;
  };

  const calcularMesesRestantes = (meta: Meta) => {
    const faltam = meta.valorTotal - (meta.valorReservado || 0);
    return Math.ceil(faltam / meta.valorMensal);
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Metas</h1>
            <button
              onClick={() => {
                resetarFormulario();
                setMostrarForm(!mostrarForm);
              }}
              className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
            >
              {mostrarForm ? 'Cancelar' : '+ Nova Meta'}
            </button>
          </div>

          <div className="flex gap-2 mb-6" role="tablist" aria-label="Filtro de metas por status">
            {([
              { valor: 'ATIVA', rotulo: 'Ativas' },
              { valor: 'CONCLUIDA', rotulo: 'Concluídas' },
              { valor: 'ARQUIVADA', rotulo: 'Arquivadas' },
            ] as { valor: StatusMeta; rotulo: string }[]).map((aba) => (
              <button
                key={aba.valor}
                role="tab"
                aria-selected={statusFiltro === aba.valor}
                onClick={() => {
                  setStatusFiltro(aba.valor);
                  setPaginaAtual(0);
                }}
                className={`px-4 py-2 rounded-full text-sm font-medium transition ${
                  statusFiltro === aba.valor
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                }`}
              >
                {aba.rotulo}
              </button>
            ))}
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">
                {editando ? 'Editar Meta' : 'Nova Meta'}
              </h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Nome da Meta</label>
                  <input
                    {...fieldA11y('nome', formValidation.errors.nome)}
                    type="text"
                    value={formData.nome}
                    onChange={(e) => { const next = { ...formData, nome: e.target.value }; setFormData(next); formValidation.revalidateField('nome', next); }}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                    placeholder="Ex: Viagem para Europa"
                    required
                  />
                  <FieldError name="nome" error={formValidation.errors.nome} />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Valor Total (R$)</label>
                    <CurrencyInput
                      {...fieldA11y('valorTotal', formValidation.errors.valorTotal)}
                      value={valorTotalNumerico}
                      onValueChange={(value) => { const next = { ...formData, valorTotal: value === null ? '' : value.toString() }; setFormData(next); formValidation.revalidateField('valorTotal', next); }}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                      placeholder="R$ 0,00"
                      required
                    />
                    <FieldError name="valorTotal" error={formValidation.errors.valorTotal} />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Valor Mensal (R$)</label>
                    <CurrencyInput
                      {...fieldA11y('valorMensal', formValidation.errors.valorMensal)}
                      value={valorMensalNumerico}
                      onValueChange={(value) => { const next = { ...formData, valorMensal: value === null ? '' : value.toString() }; setFormData(next); formValidation.revalidateField('valorMensal', next); }}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                      placeholder="R$ 0,00"
                      required
                    />
                    <FieldError name="valorMensal" error={formValidation.errors.valorMensal} />
                  </div>
                </div>

                <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
                  <label className="block text-sm font-medium mb-1 text-gray-700">Previsão de Conclusão (meses)</label>
                  <div className="flex flex-col md:flex-row md:items-center gap-3">
                    <input
                      {...fieldA11y('cor', formValidation.errors.cor)}
                      type="number"
                      min="1"
                      value={usarPrevisaoManual ? previsaoMesesManual : (previsaoMeses ?? '')}
                      onChange={(e) => {
                        setUsarPrevisaoManual(true);
                        setPrevisaoMesesManual(e.target.value);
                      }}
                      className="w-full md:w-48 border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="Automatico"
                    />
                    {usarPrevisaoManual && (
                      <button
                        type="button"
                        onClick={() => {
                          setUsarPrevisaoManual(false);
                          setPrevisaoMesesManual('');
                        }}
                        className="text-sm text-blue-700 hover:text-blue-900 font-medium"
                      >
                        Usar calculo automatico
                      </button>
                    )}
                  </div>
                  <p className="text-sm text-gray-600 mt-2">
                    Previsão: {previsaoMeses && Number.isFinite(previsaoMeses) ? `~${previsaoMeses} meses` : '—'}
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Cor</label>
                    <input
                      type="color"
                      value={formData.cor}
                      onChange={(e) => { const next = { ...formData, cor: e.target.value }; setFormData(next); formValidation.revalidateField('cor', next); }}
                      className="w-full h-10 border border-gray-300 rounded-lg cursor-pointer"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Ícone</label>
                    <IconPicker
                      value={formData.icone}
                      onChange={(icone) => setFormData({ ...formData, icone })}
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Descrição</label>
                  <textarea
                    {...fieldA11y('descricao', formValidation.errors.descricao)}
                    value={formData.descricao}
                    onChange={(e) => { const next = { ...formData, descricao: e.target.value }; setFormData(next); formValidation.revalidateField('descricao', next); }}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                    placeholder="Ex: Viagem de 15 dias para Paris, Londres e Roma"
                    rows={3}
                  />
                  <FieldError name="descricao" error={formValidation.errors.descricao} />
                </div>

                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setMostrarForm(false);
                      resetarFormulario();
                    }}
                    className="flex-1 bg-gray-300 text-gray-700 py-2 rounded-lg hover:bg-gray-400 transition"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    disabled={loading}
                    className="flex-1 bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                  >
                    {loading ? 'Salvando...' : (editando ? 'Atualizar' : 'Salvar')}
                  </button>
                </div>
              </form>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {loading || loadingLista ? (
              <div className="col-span-2 text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : metas.length === 0 ? (
              <div className="col-span-2 text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma meta cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Meta" para começar</p>
              </div>
            ) : (
              metas.map((meta) => (
                <div
                  key={meta.id}
                  className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition"
                  style={{ borderTop: `4px solid ${meta.cor || '#3498DB'}` }}
                >
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-12 h-12 rounded-full flex items-center justify-center text-white text-xl font-bold"
                        style={{ backgroundColor: meta.cor || '#3498DB' }}
                      >
                        {meta.icone || '🎯'}
                      </div>
                      <div>
                        <h3 className="text-xl font-bold text-gray-800">{meta.nome}</h3>
                        {meta.status === 'CONCLUIDA' && (
                          <span className="inline-block text-xs font-semibold text-green-700 bg-green-100 rounded-full px-2 py-0.5">
                            ✓ Concluída{meta.dataConclusao ? ` em ${new Date(meta.dataConclusao + 'T00:00:00').toLocaleDateString('pt-BR')}` : ''}
                          </span>
                        )}
                        {meta.status === 'ARQUIVADA' && (
                          <span className="inline-block text-xs font-semibold text-gray-600 bg-gray-200 rounded-full px-2 py-0.5">
                            Arquivada
                          </span>
                        )}
                        {meta.descricao && (
                          <p className="text-sm text-gray-500">{meta.descricao}</p>
                        )}
                      </div>
                    </div>
                    {acoesDaMeta(meta).editar && (
                      <div className="flex gap-2">
                        <button
                          onClick={() => abrirFormulario(meta)}
                          className="text-blue-600 hover:text-blue-800 text-sm"
                        >
                          Editar
                        </button>
                        {acoesDaMeta(meta).excluir && (
                          <button
                            onClick={() => handleDeletar(meta.id!)}
                            className="text-red-600 hover:text-red-800 text-sm"
                          >
                            Excluir
                          </button>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="mb-4">
                    <div className="flex justify-between text-sm text-gray-600 mb-1">
                      <span>{formatCurrency(meta.valorReservado || 0)}</span>
                      <span>{formatCurrency(meta.valorTotal)}</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-4">
                      <div
                        className="h-4 rounded-full transition-all"
                        style={{ 
                          width: `${Math.min(calcularProgresso(meta), 100)}%`,
                          backgroundColor: meta.cor || '#3498DB'
                        }}
                      ></div>
                    </div>
                    <p className="text-center text-lg font-bold text-gray-800 mt-2">
                      {calcularProgresso(meta).toFixed(1)}%
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-4 mb-4 text-sm">
                    <div>
                      <p className="text-gray-600">Faltam</p>
                      <p className="font-bold text-gray-800">
                        {formatCurrency(meta.valorTotal - (meta.valorReservado || 0))}
                      </p>
                    </div>
                    <div>
                      <p className="text-gray-600">Meses restantes</p>
                      <p className="font-bold text-gray-800">
                        {calcularMesesRestantes(meta)} meses
                      </p>
                    </div>
                  </div>

                  {acoesDaMeta(meta).editar && (
                    mostrarAdicionar === meta.id || mostrarResgatar === meta.id ? (
                    <div className="space-y-2">
                      <CurrencyInput
                        {...fieldA11y('valor', aporteValidation.errors.valor)}
                        value={valorAdicionarNumerico}
                        onValueChange={(value) => { const next = value === null ? '' : value.toString(); setValorAdicionar(next); aporteValidation.revalidateField('valor', { valor: next, carteiraId: carteiraOrigem }); }}
                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                        placeholder="R$ 0,00"
                      />
                      <FieldError name="valor" error={aporteValidation.errors.valor} />
                      <select
                        {...fieldA11y('carteiraId', aporteValidation.errors.carteiraId)}
                        value={carteiraOrigem}
                        onChange={(e) => { setCarteiraOrigem(e.target.value); aporteValidation.revalidateField('carteiraId', { valor: valorAdicionar, carteiraId: e.target.value }); }}
                        className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500 aria-invalid:border-red-500"
                      >
                        <option value="">
                          {mostrarResgatar === meta.id ? 'Vai para qual conta?' : 'Sai de qual conta?'}
                        </option>
                        {carteiras.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.nome} · {formatCurrency(c.saldo)}
                          </option>
                        ))}
                      </select>
                      <FieldError name="carteiraId" error={aporteValidation.errors.carteiraId} />
                      <div className="flex gap-2">
                        <button
                          disabled={acaoFinanceiraId !== null}
                          onClick={() => (mostrarResgatar === meta.id
                            ? handleResgatarValor(meta.id!)
                            : handleAdicionarValor(meta.id!))}
                          className="flex-1 bg-green-600 text-white py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                        >
                          {acaoFinanceiraId === `adicionar:${meta.id}` || acaoFinanceiraId === `resgatar:${meta.id}`
                            ? 'Processando...'
                            : 'Confirmar'}
                        </button>
                        <button
                          disabled={acaoFinanceiraId !== null}
                          onClick={() => {
                            setMostrarAdicionar(null);
                            setMostrarResgatar(null);
                            setValorAdicionar('');
                            setCarteiraOrigem('');
                            aporteValidation.resetValidation();
                          }}
                          className="flex-1 bg-gray-300 text-gray-700 py-2 rounded-lg hover:bg-gray-400 transition disabled:opacity-50"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                    ) : (
                    <div className="flex gap-2">
                      {acoesDaMeta(meta).adicionar && (
                        <button
                          onClick={() => { setMostrarResgatar(null); setMostrarAdicionar(meta.id!); }}
                          className="flex-1 bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700 transition"
                        >
                          💰 Adicionar Dinheiro
                        </button>
                      )}
                      {acoesDaMeta(meta).resgatar && (
                        <button
                          onClick={() => { setMostrarAdicionar(null); setMostrarResgatar(meta.id!); }}
                          className="flex-1 bg-gray-100 text-gray-800 border border-gray-300 py-2 rounded-lg hover:bg-gray-200 transition"
                        >
                          ↩ Resgatar
                        </button>
                      )}
                    </div>
                    )
                  )}
                </div>
              ))
            )}
          </div>

          {!loading && metas.length > 0 && (
            <div className="flex items-center justify-between mt-6">
              <button
                onClick={() => setPaginaAtual((prev) => Math.max(prev - 1, 0))}
                disabled={paginaAtual === 0}
                className="px-4 py-2 rounded-lg border border-gray-300 disabled:opacity-50"
              >
                Anterior
              </button>
              <span className="text-sm text-gray-600">
                Página {paginaAtual + 1} de {totalPaginas}
              </span>
              <button
                onClick={() => setPaginaAtual((prev) => Math.min(prev + 1, totalPaginas - 1))}
                disabled={paginaAtual >= totalPaginas - 1}
                className="px-4 py-2 rounded-lg border border-gray-300 disabled:opacity-50"
              >
                Próximo
              </button>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}

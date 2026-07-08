import { useState, useEffect } from 'react';
import { transacaoService, Transacao } from '../services/transacaoService';
import { categoriaService } from '../services/categoriaService';
import { contaService, Conta } from '../services/contaService';
import { useAuth } from '../context/AuthContext'; // ← ADICIONADO
import toast from 'react-hot-toast';
import Layout from '../components/Layout';
import CategoriaDropdown from '../components/CategoriaDropdown';
import CurrencyInput from '../components/CurrencyInput';
import { formatCurrency } from '../utils/currency';
import { importService, ImportResult } from '../services/importService';
import { anexoService, Anexo } from '../services/anexoService';

export default function Transacoes() {
  const { usuario } = useAuth(); // ← ADICIONADO
  const [transacoes, setTransacoes] = useState<any[]>([]);
  const [contas, setContas] = useState<Conta[]>([]);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [mostrarImport, setMostrarImport] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [anexos, setAnexos] = useState<Anexo[]>([]);
  const [loading, setLoading] = useState(false);
  const [editando, setEditando] = useState<any | null>(null);
  const [paginaAtual, setPaginaAtual] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(1);
  const tamanhoPagina = 20;
  
  const [formData, setFormData] = useState({
    descricao: '',
    valorTotal: '',
    tipo: 'SAIDA' as 'ENTRADA' | 'SAIDA',
    tipoConta: 'CREDITO' as Conta['tipo'],
    data: new Date().toISOString().split('T')[0],
    categoriaNome: '',
    categoriaCor: '',
    categoriaIcone: '',
    contaId: '',
    parcelado: false,
    totalParcelas: ''
  });

  useEffect(() => {
    if (usuario?.id) { // ← ADICIONADO: só carrega se tiver usuário
      carregarDados();
    }
  }, [usuario, paginaAtual]);

  const carregarDados = async () => {
    if (!usuario?.id) return; // ← ADICIONADO: proteção

    try {
      setLoading(true);
      const [transacoesData, contasData] = await Promise.all([
        transacaoService.listarPorUsuarioPaginado(paginaAtual, tamanhoPagina),
        contaService.listarPorUsuario(usuario.id)      // ← CORRIGIDO!
      ]);
      setTransacoes(transacoesData.content || []);
      setTotalPaginas(Math.max(transacoesData.totalPages || 1, 1));
      setContas(contasData);
    } catch (error: any) {
      toast.error('Erro ao carregar dados');
    } finally {
      setLoading(false);
    }
  };

  const abrirFormulario = (transacao?: any) => {
    if (transacao) {
      // Modo edição
      setEditando(transacao);
      setAnexos([]);
      carregarAnexos(transacao.id);
      setFormData({
        descricao: transacao.descricao,
        valorTotal: transacao.valorTotal?.toString() || '',
        tipo: transacao.tipo,
        tipoConta: transacao.conta?.tipo || 'CREDITO',
        data: transacao.data,
        categoriaNome: transacao.categoria?.nome || '',
        categoriaCor: transacao.categoria?.cor || '',
        categoriaIcone: transacao.categoria?.icone || '',
        contaId: transacao.conta?.id?.toString() || '',
        parcelado: transacao.parcelado || false,
        totalParcelas: transacao.totalParcelas?.toString() || ''
      });
    } else {
      // Modo criação
      resetarFormulario();
    }
    setMostrarForm(true);
  };

  const resetarFormulario = () => {
    setEditando(null);
    setFormData({
      descricao: '',
      valorTotal: '',
      tipo: 'SAIDA',
      tipoConta: 'CREDITO',
      data: new Date().toISOString().split('T')[0],
      categoriaNome: '',
      categoriaCor: '',
      categoriaIcone: '',
      contaId: '',
      parcelado: false,
      totalParcelas: ''
    });
  };

  const handleCategoriaChange = (categoria: { nome: string; cor: string; icone: string }) => {
    setFormData({
      ...formData,
      categoriaNome: categoria.nome,
      categoriaCor: categoria.cor,
      categoriaIcone: categoria.icone
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!usuario?.id) { // ← ADICIONADO: proteção
      toast.error('Usuário não autenticado');
      return;
    }

    if (!formData.categoriaNome) {
      toast.error('Selecione uma categoria');
      return;
    }

    try {
      setLoading(true);
      
      // Primeiro, buscar ou criar a categoria
      let categoriaId;
      try {
        const categoriasExistentes = await categoriaService.listarMinhas(0, 100);
        const categoriaExistente = categoriasExistentes.find(
          (c: any) => c.nome.toLowerCase() === formData.categoriaNome.toLowerCase()
        );
        
        if (categoriaExistente) {
          categoriaId = categoriaExistente.id;
        } else {
          // Criar nova categoria
          const novaCategoria = await categoriaService.criar({
            nome: formData.categoriaNome,
            cor: formData.categoriaCor,
            icone: formData.categoriaIcone,
            valorEsperado: 0
          });
          categoriaId = novaCategoria.id;
          toast.success('Nova categoria criada!');
        }
      } catch (error) {
        toast.error('Erro ao processar categoria');
        return;
      }
      
      const transacaoParaEnviar: any = {
        descricao: formData.descricao,
        valor: parseFloat(formData.valorTotal),
        tipo: formData.tipo,
        data: formData.data,
        categoriaId,
        contaId: parseInt(formData.contaId),
        parcelado: formData.parcelado,
        totalParcelas: formData.parcelado ? parseInt(formData.totalParcelas) : null
      };
      
      if (editando) {
        // Atualizar
        await transacaoService.atualizar(editando.id, transacaoParaEnviar);
        toast.success('Transação atualizada com sucesso!');
      } else {
        // Criar
        await transacaoService.criar(transacaoParaEnviar);
        toast.success('Transação criada com sucesso!');
      }
      
      resetarFormulario();
      setMostrarForm(false);
      carregarDados();
    } catch (error: any) {
      toast.error(editando ? 'Erro ao atualizar transação' : 'Erro ao criar transação');
    } finally {
      setLoading(false);
    }
  };

  const handleDeletar = async (id: number) => {

  const handleImportar = async () => {
    if (!importFile) return;
    setLoading(true);
    try {
      const result = await importService.importarCsv(importFile);
      setImportResult(result);
      if (result.importadas > 0) {
        carregarDados();
        toast.success(`${result.importadas} transações importadas`);
      }
    } catch (error) {
      toast.error('Erro ao importar arquivo');
    } finally {
      setLoading(false);
    }
  };

  const carregarAnexos = async (transacaoId: number) => {
    try {
      const data = await anexoService.listar(transacaoId);
      setAnexos(data);
    } catch {
      setAnexos([]);
    }
  };

  const handleUploadAnexo = async (transacaoId: number, file: File) => {
    try {
      await anexoService.upload(transacaoId, file);
      await carregarAnexos(transacaoId);
      toast.success('Anexo enviado');
    } catch {
      toast.error('Erro ao enviar anexo');
    }
  };

  const handleDeletarAnexo = async (anexoId: number, transacaoId: number) => {
    try {
      await anexoService.deletar(anexoId);
      await carregarAnexos(transacaoId);
    } catch {
      toast.error('Erro ao excluir anexo');
    }
  };
    if (!window.confirm('Tem certeza que deseja deletar?')) return;
    
    try {
      await transacaoService.deletar(id);
      toast.success('Transação deletada!');
      carregarDados();
    } catch (error: any) {
      toast.error('Erro ao deletar transação');
    }
  };

  const valorTotalNumerico = formData.valorTotal ? parseFloat(formData.valorTotal) : null;
  const contasPorTipo = contas.filter((conta) => conta.tipo === formData.tipoConta);
  const tipoContaLabels: Record<Conta['tipo'], string> = {
    CREDITO: 'Cartão de Crédito',
    DEBITO: 'Débito',
    DINHEIRO: 'Dinheiro',
    POUPANCA: 'Poupança',
  };

  return (
    <Layout>
      <div className="p-8">
        <div className="max-w-6xl mx-auto">
          
          <div className="flex justify-between items-center mb-8">
            <h1 className="text-3xl font-bold text-gray-800">Transações</h1>
            <div className="flex gap-3">
              <button
                onClick={() => {
                  setMostrarImport(!mostrarImport);
                  setImportResult(null);
                  setImportFile(null);
                }}
                className="border border-gray-300 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 transition"
              >
                {mostrarImport ? 'Fechar' : 'Importar CSV'}
              </button>
              <button
                onClick={() => {
                  resetarFormulario();
                  setMostrarForm(!mostrarForm);
                }}
                className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition"
              >
                {mostrarForm ? 'Cancelar' : '+ Nova Transação'}
              </button>
            </div>
          </div>

          {mostrarForm && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">
                {editando ? 'Editar Transação' : 'Nova Transação'}
              </h2>
              
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Descrição</label>
                  <input
                    type="text"
                    value={formData.descricao}
                    onChange={(e) => setFormData({ ...formData, descricao: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    placeholder="Ex: Compra no mercado"
                    required
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Valor Total (R$)</label>
                    <CurrencyInput
                      value={valorTotalNumerico}
                      onValueChange={(value) => setFormData({ ...formData, valorTotal: value === null ? '' : value.toString() })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      placeholder="R$ 0,00"
                      required
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Tipo</label>
                    <select
                      value={formData.tipo}
                      onChange={(e) => setFormData({ ...formData, tipo: e.target.value as any })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="SAIDA">💸 Saída</option>
                      <option value="ENTRADA">💰 Entrada</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Categoria</label>
                    <CategoriaDropdown
                      value={formData.categoriaNome}
                      onChange={handleCategoriaChange}
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Tipo de Conta</label>
                    <select
                      value={formData.tipoConta}
                      onChange={(e) => setFormData({ ...formData, tipoConta: e.target.value as Conta['tipo'], contaId: '' })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="CREDITO">Cartão de Crédito</option>
                      <option value="DEBITO">Débito</option>
                      <option value="DINHEIRO">Dinheiro</option>
                      <option value="POUPANCA">Poupança</option>
                    </select>
                  </div>
                </div>

                <div>
                    <label className="block text-sm font-medium mb-1 text-gray-700">Conta</label>
                    <select
                      value={formData.contaId}
                      onChange={(e) => setFormData({ ...formData, contaId: e.target.value })}
                      className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                      required
                    >
                      <option value="">Selecione...</option>
                      {contasPorTipo.map((conta) => (
                        <option key={conta.id} value={conta.id}>
                          {conta.nome} ({tipoContaLabels[conta.tipo]})
                        </option>
                      ))}
                    </select>
                    {contasPorTipo.length === 0 && (
                      <p className="text-xs text-gray-500 mt-1">
                        Nenhuma conta do tipo {tipoContaLabels[formData.tipoConta]} cadastrada.
                      </p>
                    )}
                  </div>

                <div>
                  <label className="block text-sm font-medium mb-1 text-gray-700">Data</label>
                  <input
                    type="date"
                    value={formData.data}
                    onChange={(e) => setFormData({ ...formData, data: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                    required
                  />
                </div>

                {!editando && (
                  <>
                    <div className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        id="parcelado"
                        checked={formData.parcelado}
                        onChange={(e) => setFormData({ ...formData, parcelado: e.target.checked })}
                        className="w-4 h-4"
                      />
                      <label htmlFor="parcelado" className="text-sm font-medium text-gray-700">
                        Parcelar compra?
                      </label>
                    </div>

                    {formData.parcelado && (
                      <div>
                        <label className="block text-sm font-medium mb-1 text-gray-700">Número de Parcelas</label>
                        <input
                          type="number"
                          min="2"
                          max="48"
                          value={formData.totalParcelas}
                          onChange={(e) => setFormData({ ...formData, totalParcelas: e.target.value })}
                          className="w-full border border-gray-300 rounded-lg px-4 py-2 focus:ring-2 focus:ring-blue-500"
                          placeholder="12"
                          required={formData.parcelado}
                        />
                        {formData.totalParcelas && formData.valorTotal && (
                          <p className="text-sm text-gray-600 mt-1">
                            {formData.totalParcelas}x de {formatCurrency(parseFloat(formData.valorTotal) / parseInt(formData.totalParcelas))}
                          </p>
                        )}
                      </div>
                    )}
                  </>
                )}

                {editando && editando.parcelado && (
                  <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
                    <p className="text-sm text-yellow-800">
                      ⚠️ Esta é uma transação parcelada ({editando.totalParcelas}x). Alterar o valor afetará apenas esta transação, não as parcelas.
                    </p>
                  </div>
                )}

                {editando && (
                  <div className="border-t pt-4">
                    <h3 className="text-sm font-semibold text-gray-700 mb-2">Anexos</h3>
                    <div className="flex items-center gap-3 mb-3">
                      <label className="cursor-pointer bg-gray-100 px-3 py-1.5 rounded text-sm hover:bg-gray-200 transition">
                        + Adicionar
                        <input
                          type="file"
                          className="hidden"
                          accept="image/*,.pdf"
                          onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) handleUploadAnexo(editando.id, file);
                          }}
                        />
                      </label>
                    </div>
                    {anexos.length > 0 ? (
                      <ul className="space-y-1">
                        {anexos.map((a) => (
                          <li key={a.id} className="flex items-center justify-between text-sm bg-gray-50 px-3 py-1.5 rounded">
                            <a
                              href={anexoService.downloadUrl(a.id)}
                              className="text-blue-600 hover:underline truncate flex-1"
                              target="_blank"
                              rel="noreferrer"
                            >
                              {a.nome} ({(a.tamanho / 1024).toFixed(1)} KB)
                            </a>
                            <button
                              onClick={() => handleDeletarAnexo(a.id, editando.id)}
                              className="text-red-500 hover:text-red-700 ml-2"
                            >
                              x
                            </button>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="text-xs text-gray-400">Nenhum anexo</p>
                    )}
                  </div>
                )}

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

          {mostrarImport && (
            <div className="bg-white p-6 rounded-lg shadow-md mb-8">
              <h2 className="text-xl font-bold mb-4">Importar Transações (CSV)</h2>
              <p className="text-sm text-gray-600 mb-4">
                Formato esperado: <code className="bg-gray-100 px-1 rounded">data,descricao,valor,tipo,categoria,conta,status,observacoes</code>
              </p>
              <div className="flex gap-3 items-end">
                <div className="flex-1">
                  <input
                    type="file"
                    accept=".csv"
                    onChange={(e) => {
                      setImportFile(e.target.files?.[0] || null);
                      setImportResult(null);
                    }}
                    className="w-full border border-gray-300 rounded-lg px-4 py-2"
                  />
                </div>
                <button
                  onClick={handleImportar}
                  disabled={!importFile || loading}
                  className="bg-green-600 text-white px-6 py-2 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                >
                  {loading ? 'Importando...' : 'Importar'}
                </button>
              </div>
              {importResult && (
                <div className="mt-4 p-4 bg-gray-50 rounded-lg text-sm">
                  <p>Total de linhas: <strong>{importResult.total}</strong></p>
                  <p className="text-green-600">Importadas: <strong>{importResult.importadas}</strong></p>
                  <p className="text-yellow-600">Ignoradas: <strong>{importResult.ignoradas}</strong></p>
                  <p className="text-red-600">Erros: <strong>{importResult.erros}</strong></p>
                </div>
              )}
            </div>
          )}

          <div className="bg-white rounded-lg shadow-md overflow-hidden">
            {loading ? (
              <div className="text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
                <p className="mt-2 text-gray-600">Carregando...</p>
              </div>
            ) : transacoes.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-gray-500 text-lg">Nenhuma transação cadastrada</p>
                <p className="text-gray-400 text-sm mt-2">Clique em "+ Nova Transação" para começar</p>
              </div>
            ) : (
              <>
                <table className="w-full">
                  <thead className="bg-gray-100">
                    <tr>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Data</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Descrição</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Categoria</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Cartão</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Valor</th>
                      <th className="px-6 py-3 text-left text-sm font-semibold text-gray-700">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transacoes.map((t) => (
                      <tr key={t.id} className="border-t border-gray-200 hover:bg-gray-50 transition">
                        <td className="px-6 py-4 text-gray-700">
                          {new Date(t.data).toLocaleDateString('pt-BR')}
                        </td>
                        <td className="px-6 py-4">
                          <div>
                            <p className="font-medium text-gray-800">{t.descricao}</p>
                            {t.parcelado && (
                              <p className="text-xs text-gray-500">
                                {t.totalParcelas}x de {formatCurrency(t.valorParcela || 0)}
                              </p>
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="w-3 h-3 rounded-full"
                              style={{ backgroundColor: t.categoria?.cor || '#666' }}
                            ></span>
                            {t.categoria?.nome || 'N/A'}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-gray-700">
                          {t.conta?.nome || 'N/A'}
                        </td>
                        <td className="px-6 py-4">
                          <span className={`font-semibold ${t.tipo === 'ENTRADA' ? 'text-green-600' : 'text-red-600'}`}>
                            {t.tipo === 'ENTRADA' ? '+' : '-'} {formatCurrency(t.valorTotal || 0)}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex gap-3">
                            <button
                              onClick={() => abrirFormulario(t)}
                              className="text-blue-600 hover:text-blue-800 font-medium transition"
                            >
                              Editar
                            </button>
                            <button
                              onClick={() => handleDeletar(t.id!)}
                              className="text-red-600 hover:text-red-800 font-medium transition"
                            >
                              Deletar
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="flex items-center justify-between px-6 py-4 border-t border-gray-200">
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
              </>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}
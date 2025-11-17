import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

interface ComparacaoMes {
  periodo: string;
  entradas: number;
  saidas: number;
}

interface Props {
  dados: ComparacaoMes[];
}

export default function GraficoComparacaoMensal({ dados }: Props) {
  if (!dados || dados.length === 0) {
    return (
      <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
        <h3 className="text-lg font-bold text-white mb-4">📊 Comparação Mensal</h3>
        <div className="flex items-center justify-center h-64 text-gray-500">
          Sem dados para exibir
        </div>
      </div>
    );
  }

  const formatarMoeda = (valor: number) => {
    return `R$ ${valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    })}`;
  };

  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-slate-900 border border-slate-700 rounded-lg p-3 shadow-xl">
          <p className="text-white font-semibold mb-2">{payload[0].payload.periodo}</p>
          {payload.map((entry: any, index: number) => (
            <p key={index} style={{ color: entry.color }} className="text-sm">
              {entry.name}: {formatarMoeda(entry.value)}
            </p>
          ))}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
      <h3 className="text-lg font-bold text-white mb-4">📊 Comparação: Mês Anterior vs Atual</h3>
      
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={dados}>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
          <XAxis 
            dataKey="periodo" 
            stroke="#94a3b8"
            style={{ fontSize: '12px' }}
          />
          <YAxis 
            stroke="#94a3b8"
            style={{ fontSize: '12px' }}
            tickFormatter={(value) => `R$ ${value}`}
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend 
            wrapperStyle={{ paddingTop: '20px' }}
            formatter={(value) => <span className="text-gray-300">{value}</span>}
          />
          <Bar 
            dataKey="entradas" 
            fill="#10b981" 
            name="Entradas"
            radius={[8, 8, 0, 0]}
          />
          <Bar 
            dataKey="saidas" 
            fill="#ef4444" 
            name="Saídas"
            radius={[8, 8, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
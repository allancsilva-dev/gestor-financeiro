import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

interface EvolucaoMes {
  mes: string;
  entradas: number;
  saidas: number;
  saldo: number;
}

interface Props {
  // --- CORREÇÃO (V8) ---
  chartData: EvolucaoMes[];
}

// --- CORREÇÃO (V8) ---
export default function GraficoEvolucaoMensal({ chartData }: Props) {
  
  // --- CORREÇÃO (V8) ---
  console.log("--- DENTRO DO GRÁFICO LINHA (Componente):", chartData);

  // --- CORREÇÃO (V8) ---
  if (!chartData || chartData.length === 0) {
    return (
      <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
        <h3 className="text-lg font-bold text-white mb-4">📈 Evolução Mensal</h3>
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
          <p className="text-white font-semibold mb-2">{payload[0].payload.mes}</p>
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
      <h3 className="text-lg font-bold text-white mb-4">📈 Evolução dos Últimos 6 Meses</h3>
      
      <ResponsiveContainer width="100%" height={300}>
        {/* --- CORREÇÃO (V8) --- */}
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
          <XAxis 
            dataKey="mes" 
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
          <Line 
            type="monotone" 
            dataKey="entradas" 
            stroke="#10b981" 
            strokeWidth={2}
            name="Entradas"
            dot={{ fill: '#10b981', r: 4 }}
            activeDot={{ r: 6 }}
          />
          <Line 
            type="monotone" 
            dataKey="saidas" 
            stroke="#ef4444" 
            strokeWidth={2}
            name="Saídas"
            dot={{ fill: '#ef4444', r: 4 }}
            activeDot={{ r: 6 }}
          />
          <Line 
            type="monotone" 
            dataKey="saldo" 
            stroke="#f97316" 
            strokeWidth={2}
            name="Saldo"
            dot={{ fill: '#f97316', r: 4 }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';

interface Props {
  // --- CORREÇÃO (V8) ---
  chartData: any[]; 
}

// --- CORREÇÃO (V8) ---
export default function GraficoGastosPorCategoria({ chartData }: Props) {
  
  // --- CORREÇÃO (V8) ---
  console.log("--- DENTRO DO GRÁFICO PIZZA (Componente):", chartData);

  // --- CORREÇÃO (V8) ---
  if (!chartData || chartData.length === 0) {
    return (
      <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
        <h3 className="text-lg font-bold text-white mb-4">📊 Gastos por Categoria</h3>
        <div className="flex items-center justify-center h-64 text-gray-500">
          Sem dados para exibir
        </div>
      </div>
    );
  }

  const formatarMoeda = (valor: number) => {
    return `R$ ${valor.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  };

  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-slate-900 border border-slate-700 rounded-lg p-3 shadow-xl">
          <p className="text-white font-semibold">{payload[0].name}</p>
          <p className="text-orange-400">{formatarMoeda(payload[0].value)}</p>
          <p className="text-gray-400 text-sm">{payload[0].payload.percentual}%</p>
        </div>
      );
    }
    return null;
  };

  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700">
      <h3 className="text-lg font-bold text-white mb-4">📊 Gastos por Categoria</h3>
      
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie
            // --- CORREÇÃO (V8) ---
            data={chartData}
            dataKey="valor"
            nameKey="categoria"
            cx="50%"
            cy="50%"
            outerRadius={100}
            label={(entry: any) => `${entry.payload.percentual}%`}
            labelLine={false}
          >
            {/* --- CORREÇÃO (V8) --- */}
            {chartData.map((entry: any, index: number) => (
              <Cell key={`cell-${index}`} fill={entry.cor} />
            ))}
          </Pie>
          <Tooltip content={<CustomTooltip />} />
          <Legend 
            formatter={(value: string, entry: any) => (
              <span className="text-gray-300 text-sm">
                {value}: {formatarMoeda(entry.payload.valor)}
              </span>
            )}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
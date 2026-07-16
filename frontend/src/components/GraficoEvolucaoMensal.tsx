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

  if (!chartData || chartData.length === 0) {
    return (
      <div className="rounded-xl bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-lg font-bold text-slate-950">Evolução mensal</h3>
        <div className="flex h-64 items-center justify-center text-slate-600">
          Sem histórico no período
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
        <div className="rounded-lg bg-slate-950 p-3 text-white shadow-lg">
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
    <div className="rounded-xl bg-white p-6 shadow-sm">
      <h3 className="mb-4 text-lg font-bold text-slate-950">Evolução dos últimos 6 meses</h3>
      
      <ResponsiveContainer width="100%" height={300}>
        {/* --- CORREÇÃO (V8) --- */}
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
          <XAxis 
            dataKey="mes" 
            stroke="#64748b"
            style={{ fontSize: '12px' }}
          />
          <YAxis 
            stroke="#64748b"
            style={{ fontSize: '12px' }}
            tickFormatter={(value) => `R$ ${value}`}
          />
          <Tooltip content={<CustomTooltip />} />
          <Legend 
            wrapperStyle={{ paddingTop: '20px' }}
            formatter={(value) => <span className="text-slate-700">{value}</span>}
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
            stroke="#7c5cfc"
            strokeWidth={2}
            name="Saldo"
            dot={{ fill: '#7c5cfc', r: 4 }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

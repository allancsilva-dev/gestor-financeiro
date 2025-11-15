import Layout from '../components/Layout';

export default function Dashboard() {
  return (
    <Layout>
      <div className="p-8">
        <h1 className="text-3xl font-bold text-gray-800 mb-6">Dashboard</h1>
        <div className="bg-white p-6 rounded-lg shadow-md">
          <p className="text-gray-600">Em breve: gráficos e resumo financeiro!</p>
        </div>
      </div>
    </Layout>
  );
}
import { useState } from 'react';
import { AlertsDashboard } from './components/AlertsDashboard';
import { PerformanceDashboard } from './components/PerformanceDashboard';

type Tab = 'alerts' | 'performance';

export default function App() {
  const [tab, setTab] = useState<Tab>('alerts');

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Top Nav */}
      <nav className="bg-gray-950 border-b border-gray-800 sticky top-0 z-[60]">
        <div className="max-w-7xl mx-auto px-4 flex items-center gap-1 py-2">
          <h1 className="text-lg font-bold tracking-tight mr-6">
            <span className="text-brand-green">Value</span> Betting
          </h1>
          <button
            onClick={() => setTab('alerts')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-all ${
              tab === 'alerts'
                ? 'bg-brand-green text-black'
                : 'text-gray-400 hover:text-white hover:bg-gray-800'
            }`}
          >
            Alertas
          </button>
          <button
            onClick={() => setTab('performance')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-all ${
              tab === 'performance'
                ? 'bg-brand-green text-black'
                : 'text-gray-400 hover:text-white hover:bg-gray-800'
            }`}
          >
            Performance Analytics
          </button>
        </div>
      </nav>

      {tab === 'alerts' ? <AlertsDashboard /> : (
        <div className="max-w-7xl mx-auto px-4 py-6">
          <PerformanceDashboard />
        </div>
      )}
    </div>
  );
}

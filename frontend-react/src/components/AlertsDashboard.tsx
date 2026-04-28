import { useState, useEffect } from 'react';
import { useValueBetAlerts } from '../hooks/useValueBetAlerts';
import { LiveBetCard } from './LiveBetCard';
import { api } from '../services/api';
import type { Rank, StatusResponse } from '../types';

const RANK_FILTERS: { key: Rank | 'ALL'; label: string }[] = [
  { key: 'ALL', label: 'Todas' },
  { key: 'ELITE', label: 'Elite' },
  { key: 'ALTA', label: 'Alta' },
  { key: 'MEDIA', label: 'Media' },
  { key: 'BAJA', label: 'Baja' },
];

export function AlertsDashboard() {
  const { alerts, connected, refresh } = useValueBetAlerts();
  const [filter, setFilter] = useState<Rank | 'ALL'>('ALL');
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<StatusResponse | null>(null);

  useEffect(() => {
    const fetchStatus = () => api.getStatus().then(setStatus).catch(() => {});
    fetchStatus();
    const interval = setInterval(fetchStatus, 10000);
    return () => clearInterval(interval);
  }, []);

  const filtered = filter === 'ALL'
    ? alerts
    : alerts.filter(a => a.rank === filter);

  const eliteCount = alerts.filter(a => a.rank === 'ELITE').length;
  const altaCount = alerts.filter(a => a.rank === 'ALTA').length;

  async function handleInjectDemo() {
    setLoading(true);
    try {
      await api.injectDemo();
      setTimeout(refresh, 1000);
    } catch (e) {
      console.error('Error injecting demo:', e);
    }
    setLoading(false);
  }

  return (
    <div>
      {/* Header */}
      <header className="border-b border-gray-800 bg-gray-950/90 backdrop-blur-sm sticky top-[44px] z-50">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight">
                <span className="text-brand-green">Value</span> Betting Engine
              </h1>
              <span className={`
                flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold tracking-wider
                ${connected
                  ? 'bg-green-500/15 text-green-400 border border-green-500/30'
                  : 'bg-red-500/10 text-red-400 border border-red-500/20'}
              `}>
                <span className={`w-2 h-2 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
                {connected ? 'LIVE' : 'OFFLINE'}
              </span>
            </div>

            <div className="flex items-center gap-3">
              <div className="hidden sm:flex items-center gap-4 text-xs text-gray-400">
                {status && status.rateLimited && (
                  <span className="flex items-center gap-1.5 text-yellow-400">
                    <span className="w-1.5 h-1.5 bg-yellow-400 rounded-full" />
                    Cooldown {Math.ceil(status.rateLimitSecondsRemaining / 60)}m
                  </span>
                )}
                {status && status.liveMatches > 0 && (
                  <span className="flex items-center gap-1.5">
                    <span className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse" />
                    <span className="text-white font-medium">{status.liveMatches} partidos en vivo</span>
                  </span>
                )}
                <span>{alerts.length} alertas</span>
                {eliteCount > 0 && (
                  <span className="text-yellow-400 font-medium">{eliteCount} Elite</span>
                )}
                {altaCount > 0 && (
                  <span className="text-green-400 font-medium">{altaCount} Alta</span>
                )}
              </div>
              <button
                onClick={handleInjectDemo}
                disabled={loading}
                className="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-xs text-gray-300 transition-colors disabled:opacity-50"
              >
                {loading ? 'Cargando...' : 'Demo Data'}
              </button>
              <button
                onClick={refresh}
                className="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-xs text-gray-300 transition-colors"
              >
                Refrescar
              </button>
            </div>
          </div>

          {/* Rank Filters */}
          <div className="flex gap-2 mt-3">
            {RANK_FILTERS.map(({ key, label }) => (
              <button
                key={key}
                onClick={() => setFilter(key)}
                className={`
                  px-3 py-1 rounded-full text-xs font-medium transition-all
                  ${filter === key
                    ? 'bg-brand-green text-black'
                    : 'bg-gray-800 text-gray-400 hover:text-white hover:bg-gray-700'
                  }
                `}
              >
                {label}
                {key !== 'ALL' && (
                  <span className="ml-1 opacity-60">
                    ({alerts.filter(a => a.rank === key).length})
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      </header>

      {/* Alerts Grid */}
      <main className="max-w-7xl mx-auto px-4 py-6 pb-16">
        {filtered.length === 0 ? (
          <div className="text-center py-20">
            <div className="text-4xl mb-4 opacity-30">&#9917;</div>
            <h2 className="text-gray-400 text-lg mb-2">Sin alertas activas</h2>
            <p className="text-gray-600 text-sm mb-6">
              El agente evaluador analiza partidos en vivo cada 30 segundos.<br />
              Las alertas aparecen cuando detecta valor esperado positivo (EV {'>'} 5%).
            </p>
            <button
              onClick={handleInjectDemo}
              disabled={loading}
              className="px-6 py-2.5 bg-brand-green hover:bg-green-400 text-black font-bold rounded-lg transition-colors disabled:opacity-50"
            >
              Cargar Demo
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {filtered.map(alert => (
              <LiveBetCard key={alert.id} alert={alert} />
            ))}
          </div>
        )}
      </main>

      {/* Footer Stats */}
      <footer className="fixed bottom-0 left-0 right-0 bg-gray-900/90 backdrop-blur-sm border-t border-gray-800 py-2 px-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between text-xs text-gray-500">
          <span className="flex items-center gap-2">
            Football Value Betting Engine v2.0
            {connected && (
              <span className="flex items-center gap-1 text-green-500">
                <span className="w-1 h-1 bg-green-500 rounded-full animate-pulse" />
                Conectado
              </span>
            )}
          </span>
          <span>Monte Carlo: 10,000 sims | Kelly: 25% fraccional | API-Football</span>
        </div>
      </footer>
    </div>
  );
}

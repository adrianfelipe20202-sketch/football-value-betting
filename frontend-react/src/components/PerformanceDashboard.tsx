import { useState, useEffect } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, BarChart, Bar, Cell
} from 'recharts';
import { api } from '../services/api';
import type { PerformanceStats, PaperBet, BankrollPoint } from '../types';

export function formatCOP(value: number): string {
  return '$' + Math.round(value).toLocaleString('es-CO') + ' COP';
}

export function PerformanceDashboard() {
  const [stats, setStats] = useState<PerformanceStats | null>(null);
  const [bets, setBets] = useState<PaperBet[]>([]);
  const [history, setHistory] = useState<BankrollPoint[]>([]);
  const [tab, setTab] = useState<'overview' | 'bets'>('overview');

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 15000);
    return () => clearInterval(interval);
  }, []);

  function refresh() {
    api.getPerformanceStats().then(setStats).catch(() => {});
    api.getPaperBets().then(setBets).catch(() => {});
    api.getBankrollHistory().then(setHistory).catch(() => {});
  }

  if (!stats) return <div className="text-gray-500 text-center py-20">Cargando stats...</div>;

  const pendingBets = bets.filter(b => b.status === 'PENDING');
  const settledBets = bets.filter(b => b.status === 'WON' || b.status === 'LOST');

  return (
    <div className="space-y-6">
      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-3">
        <StatCard label="Bankroll" value={formatCOP(stats.currentBankroll)}
          sub={`${stats.growthPercent >= 0 ? '+' : ''}${stats.growthPercent}%`}
          color={stats.growthPercent >= 0 ? 'text-green-400' : 'text-red-400'} />
        <StatCard label="P/L Total" value={formatCOP(stats.totalProfitLoss)}
          color={stats.totalProfitLoss >= 0 ? 'text-green-400' : 'text-red-400'} />
        <StatCard label="ROI" value={`${stats.roi}%`}
          color={stats.roi >= 0 ? 'text-green-400' : 'text-red-400'} />
        <StatCard label="Win Rate" value={`${stats.winRate}%`}
          sub={`${stats.won}W / ${stats.lost}L`} color="text-blue-400" />
        <StatCard label="Total Bets" value={`${stats.totalBets}`}
          sub={`${stats.pending} pendientes`} color="text-purple-400" />
        <StatCard label="Yield" value={`${stats.yield}%`}
          sub={`Kelly ${stats.kellyFraction * 100}%`} color="text-cyan-400" />
      </div>

      {/* ROI Split: Antes vs Después del Min 80 */}
      {stats.before80 && stats.after80 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="bg-gray-900/60 rounded-xl border border-gray-800 p-4">
            <div className="flex items-center gap-2 mb-3">
              <span className="w-2 h-2 rounded-full bg-green-500" />
              <h4 className="text-white font-semibold text-sm">Antes del Min 80</h4>
              <span className="text-gray-500 text-xs">(EV &gt; 5%)</span>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <MiniStat label="ROI" value={`${stats.before80.roi}%`}
                color={stats.before80.roi >= 0 ? 'text-green-400' : 'text-red-400'} />
              <MiniStat label="Win Rate" value={`${stats.before80.winRate}%`} color="text-blue-400" />
              <MiniStat label="P/L" value={formatCOP(stats.before80.profitLoss)}
                color={stats.before80.profitLoss >= 0 ? 'text-green-400' : 'text-red-400'} />
            </div>
            <div className="text-gray-500 text-[10px] mt-2">
              {stats.before80.bets} resueltas · {stats.before80.won}W · Staked: {formatCOP(stats.before80.staked)}
            </div>
          </div>
          <div className="bg-gray-900/60 rounded-xl border border-yellow-800/40 p-4">
            <div className="flex items-center gap-2 mb-3">
              <span className="w-2 h-2 rounded-full bg-yellow-500" />
              <h4 className="text-white font-semibold text-sm">Después del Min 80</h4>
              <span className="text-yellow-500/70 text-xs">(EV &gt; 12% filtro activo)</span>
            </div>
            <div className="grid grid-cols-3 gap-2">
              <MiniStat label="ROI" value={`${stats.after80.roi}%`}
                color={stats.after80.roi >= 0 ? 'text-green-400' : 'text-red-400'} />
              <MiniStat label="Win Rate" value={`${stats.after80.winRate}%`} color="text-blue-400" />
              <MiniStat label="P/L" value={formatCOP(stats.after80.profitLoss)}
                color={stats.after80.profitLoss >= 0 ? 'text-green-400' : 'text-red-400'} />
            </div>
            <div className="text-gray-500 text-[10px] mt-2">
              {stats.after80.bets} resueltas · {stats.after80.won}W · Staked: {formatCOP(stats.after80.staked)}
            </div>
          </div>
        </div>
      )}

      {/* Bankroll Chart */}
      {history.length > 1 && (
        <div className="bg-gray-900/60 rounded-xl border border-gray-800 p-4">
          <h3 className="text-white font-semibold mb-3">Crecimiento del Bankroll</h3>
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={history}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="label" tick={{ fill: '#9ca3af', fontSize: 10 }} />
              <YAxis tick={{ fill: '#9ca3af', fontSize: 10 }}
                tickFormatter={(v: number) => `$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: '#1f2937', border: '1px solid #374151', borderRadius: 8 }}
                labelStyle={{ color: '#fff' }}
                formatter={(value) => [formatCOP(Number(value)), 'Bankroll']} />
              <Line type="monotone" dataKey="bankroll" stroke="#00e676" strokeWidth={2}
                dot={{ fill: '#00e676', r: 3 }} activeDot={{ r: 5 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-2">
        <button onClick={() => setTab('overview')}
          className={`px-4 py-1.5 rounded-lg text-xs font-medium transition-all
            ${tab === 'overview' ? 'bg-brand-green text-black' : 'bg-gray-800 text-gray-400 hover:text-white'}`}>
          P/L por Apuesta
        </button>
        <button onClick={() => setTab('bets')}
          className={`px-4 py-1.5 rounded-lg text-xs font-medium transition-all
            ${tab === 'bets' ? 'bg-brand-green text-black' : 'bg-gray-800 text-gray-400 hover:text-white'}`}>
          Historial ({bets.length})
        </button>
      </div>

      {tab === 'overview' && settledBets.length > 0 && (
        <div className="bg-gray-900/60 rounded-xl border border-gray-800 p-4">
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={settledBets.slice(-20)}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="selectionLabel" tick={{ fill: '#9ca3af', fontSize: 9 }} />
              <YAxis tick={{ fill: '#9ca3af', fontSize: 10 }}
                tickFormatter={(v: number) => `$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: '#1f2937', border: '1px solid #374151', borderRadius: 8 }}
                formatter={(value) => [formatCOP(Number(value)), 'P/L']} />
              <Bar dataKey="profitLoss">
                {settledBets.slice(-20).map((bet, i) => (
                  <Cell key={i} fill={bet.profitLoss >= 0 ? '#00e676' : '#ff1744'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {tab === 'bets' && (
        <div className="space-y-2">
          {/* Pending Bets */}
          {pendingBets.length > 0 && (
            <div className="mb-4">
              <h4 className="text-yellow-400 text-sm font-medium mb-2">
                Apuestas Pendientes ({pendingBets.length})
              </h4>
              {pendingBets.map(bet => (
                <BetRow key={bet.id} bet={bet} onSettle={(id, won) => {
                  api.settleBet(id, won).then(refresh);
                }} />
              ))}
            </div>
          )}

          {/* Settled Bets */}
          <h4 className="text-gray-400 text-sm font-medium mb-2">Historial</h4>
          {settledBets.length === 0 ? (
            <p className="text-gray-600 text-sm">Sin apuestas resueltas aún</p>
          ) : (
            settledBets.map(bet => <BetRow key={bet.id} bet={bet} />)
          )}
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value, sub, color = 'text-white' }: {
  label: string; value: string; sub?: string; color?: string;
}) {
  return (
    <div className="bg-gray-900/60 rounded-lg border border-gray-800 p-3">
      <div className="text-gray-500 text-[10px] uppercase tracking-wider">{label}</div>
      <div className={`${color} font-bold text-lg mt-0.5`}>{value}</div>
      {sub && <div className="text-gray-500 text-xs mt-0.5">{sub}</div>}
    </div>
  );
}

function MiniStat({ label, value, color = 'text-white' }: {
  label: string; value: string; color?: string;
}) {
  return (
    <div className="text-center">
      <div className="text-gray-500 text-[10px] uppercase tracking-wider">{label}</div>
      <div className={`${color} font-bold text-sm mt-0.5`}>{value}</div>
    </div>
  );
}

function BetRow({ bet, onSettle }: {
  bet: PaperBet;
  onSettle?: (id: number, won: boolean) => void;
}) {
  const statusColors = {
    PENDING: 'text-yellow-400 bg-yellow-500/10',
    WON: 'text-green-400 bg-green-500/10',
    LOST: 'text-red-400 bg-red-500/10',
    VOID: 'text-gray-400 bg-gray-500/10',
  };

  return (
    <div className="bg-gray-900/40 rounded-lg border border-gray-800/50 p-3 mb-1.5 flex items-center justify-between">
      <div className="flex-1">
        <div className="text-white text-sm font-medium">
          {bet.homeTeam} vs {bet.awayTeam}
        </div>
        <div className="text-gray-400 text-xs mt-0.5">
          {bet.market} - {bet.selectionLabel} @ {bet.oddsAtBet.toFixed(2)}
          <span className="text-gray-600 ml-2">|</span>
          <span className="ml-2">Stake: {formatCOP(bet.stakeAmount)}</span>
          <span className="text-gray-600 ml-2">|</span>
          <span className="ml-2">EV: +{(bet.expectedValue * 100).toFixed(1)}%</span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {bet.status !== 'PENDING' && (
          <span className={`font-bold text-sm ${bet.profitLoss >= 0 ? 'text-green-400' : 'text-red-400'}`}>
            {bet.profitLoss >= 0 ? '+' : ''}{formatCOP(bet.profitLoss)}
          </span>
        )}

        <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${statusColors[bet.status]}`}>
          {bet.status}
        </span>

        {bet.status === 'PENDING' && onSettle && (
          <div className="flex gap-1 ml-2">
            <button onClick={() => onSettle(bet.id, true)}
              className="px-2 py-0.5 bg-green-600 hover:bg-green-500 text-white text-[10px] rounded font-bold">
              WON
            </button>
            <button onClick={() => onSettle(bet.id, false)}
              className="px-2 py-0.5 bg-red-600 hover:bg-red-500 text-white text-[10px] rounded font-bold">
              LOST
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

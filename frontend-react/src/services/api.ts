import type { ValueBetAlert, LiveMatch, StatusResponse, PaperBet, PerformanceStats, BankrollPoint } from '../types';

const BACKEND_URL = import.meta.env.VITE_API_URL || `http://${window.location.hostname}:8080`;
const API_BASE = `${BACKEND_URL}/api`;

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export const api = {
  getAlerts: () => fetchJson<ValueBetAlert[]>(`${API_BASE}/alerts`),
  getAlertHistory: () => fetchJson<ValueBetAlert[]>(`${API_BASE}/alerts/history`),
  getAlertsByRank: (rank: string) => fetchJson<ValueBetAlert[]>(`${API_BASE}/alerts/rank/${rank}`),
  getLiveMatches: () => fetchJson<LiveMatch[]>(`${API_BASE}/matches/live`),
  getStatus: () => fetchJson<StatusResponse>(`${API_BASE}/status`),
  injectDemo: () => fetch(`${API_BASE}/demo/inject`, { method: 'POST' }).then(r => r.json()),

  // Paper Betting
  getPaperBets: () => fetchJson<PaperBet[]>(`${API_BASE}/paper/bets`),
  getPendingBets: () => fetchJson<PaperBet[]>(`${API_BASE}/paper/bets/pending`),
  getPerformanceStats: () => fetchJson<PerformanceStats>(`${API_BASE}/paper/stats`),
  getBankrollHistory: () => fetchJson<BankrollPoint[]>(`${API_BASE}/paper/bankroll/history`),
  settleBet: (id: number, won: boolean) =>
    fetch(`${API_BASE}/paper/bets/${id}/settle?won=${won}`, { method: 'POST' }).then(r => r.json()),
  voidBet: (id: number) =>
    fetch(`${API_BASE}/paper/bets/${id}/void`, { method: 'POST' }).then(r => r.json()),
};

import { formatCOP } from './PerformanceDashboard';
import type { ValueBetAlert, Rank } from '../types';

const rankConfig: Record<Rank, { bg: string; border: string; badge: string; label: string; glow: string }> = {
  ELITE: {
    bg: 'bg-gradient-to-br from-yellow-900/40 to-yellow-950/60',
    border: 'border-yellow-500/60',
    badge: 'bg-yellow-500 text-black',
    label: 'ELITE',
    glow: 'shadow-yellow-500/20 hover:shadow-yellow-500/40',
  },
  ALTA: {
    bg: 'bg-gradient-to-br from-green-900/30 to-green-950/50',
    border: 'border-green-500/50',
    badge: 'bg-green-500 text-black',
    label: 'ALTA',
    glow: 'shadow-green-500/15 hover:shadow-green-500/30',
  },
  MEDIA: {
    bg: 'bg-gradient-to-br from-blue-900/25 to-blue-950/40',
    border: 'border-blue-500/40',
    badge: 'bg-blue-500 text-white',
    label: 'MEDIA',
    glow: 'shadow-blue-500/10 hover:shadow-blue-500/20',
  },
  BAJA: {
    bg: 'bg-gradient-to-br from-gray-800/30 to-gray-900/40',
    border: 'border-gray-600/30',
    badge: 'bg-gray-500 text-white',
    label: 'BAJA',
    glow: '',
  },
};

export function LiveBetCard({ alert }: { alert: ValueBetAlert }) {
  const config = rankConfig[alert.rank];
  const isElite = alert.rank === 'ELITE';
  const bookmaker = alert.bookmakerName || 'Multi-book';

  return (
    <div className={`
      relative rounded-xl border ${config.border} ${config.bg}
      p-5 transition-all duration-300 shadow-lg ${config.glow}
      ${isElite ? 'animate-glow ring-1 ring-yellow-500/30' : ''}
    `}>
      {/* Rank Badge + Time */}
      <div className="flex items-center justify-between mb-3">
        <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold tracking-wider ${config.badge}`}>
          {config.label}
        </span>
        <div className="flex items-center gap-2">
          {alert.status === 'LIVE' && (
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />
              <span className="text-red-400 text-xs font-medium">{alert.minute}'</span>
            </span>
          )}
          <span className="text-gray-500 text-xs">
            {new Date(alert.timestamp).toLocaleTimeString('es-CO')}
          </span>
        </div>
      </div>

      {/* Teams */}
      <div className="mb-3">
        <div className="text-white font-semibold text-lg leading-tight">
          {alert.homeTeam} <span className="text-gray-500">vs</span> {alert.awayTeam}
        </div>
        <div className="flex items-center gap-2 mt-1">
          <span className="text-gray-400 text-xs">{alert.league}</span>
          {alert.status === 'LIVE' && (
            <span className="text-gray-500 text-xs">| {alert.score}</span>
          )}
        </div>
      </div>

      {/* Bookmaker Badge (prominent) */}
      <div className={`
        rounded-lg px-3 py-2 mb-3 flex items-center justify-between
        ${alert.oddsStale
          ? 'bg-yellow-900/20 border border-yellow-500/30'
          : 'bg-brand-green/10 border border-brand-green/40'
        }
      `}>
        <span className={`font-bold text-sm tracking-wider ${alert.oddsStale ? 'text-yellow-400' : 'text-brand-green'}`}>
          CASA: {bookmaker.toUpperCase()}
        </span>
        {alert.oddsStale && (
          <span className="text-yellow-400/70 text-[10px] font-medium">NO DISPONIBLE</span>
        )}
      </div>

      {/* Market & Selection */}
      <div className="bg-gray-900/60 rounded-lg p-3 mb-3 border border-gray-700/30">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-gray-400 text-xs uppercase tracking-wider">{alert.market}</div>
            <div className="text-white font-bold text-base mt-0.5">{alert.selectionLabel}</div>
          </div>
          <div className="text-right">
            <div className="text-gray-400 text-xs">Cuota</div>
            <div className={`font-bold text-2xl ${alert.oddsStale ? 'text-yellow-400/50 line-through' : 'text-brand-green'}`}>
              {alert.bookmakerOdds.toFixed(2)}
            </div>
          </div>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-3 gap-2 mb-4">
        <StatBox
          label="EV"
          value={`+${(alert.expectedValue * 100).toFixed(1)}%`}
          color={alert.expectedValue >= 0.15 ? 'text-yellow-400' : 'text-green-400'}
        />
        <StatBox
          label="Edge"
          value={`${alert.edgePercent.toFixed(1)}%`}
          color="text-blue-400"
        />
        <StatBox
          label="Prob. Modelo"
          value={`${(alert.modelProbability * 100).toFixed(1)}%`}
          color="text-purple-400"
        />
        <StatBox
          label="Prob. Implícita"
          value={`${(alert.impliedProbability * 100).toFixed(1)}%`}
          color="text-gray-300"
        />
        <StatBox
          label="Cuota Justa"
          value={alert.fairOdds.toFixed(2)}
          color="text-orange-400"
        />
        <StatBox
          label="Kelly"
          value={`${alert.recommendedStakePct.toFixed(1)}%`}
          color="text-cyan-400"
        />
      </div>

      {/* Confidence Bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-1">
          <span className="text-gray-400 text-xs">Confianza</span>
          <span className="text-white text-xs font-medium">{alert.confidenceScore.toFixed(0)}/100</span>
        </div>
        <div className="w-full bg-gray-800 rounded-full h-1.5">
          <div
            className={`h-1.5 rounded-full transition-all duration-500 ${
              alert.confidenceScore >= 85 ? 'bg-yellow-500' :
              alert.confidenceScore >= 60 ? 'bg-green-500' :
              alert.confidenceScore >= 35 ? 'bg-blue-500' : 'bg-gray-500'
            }`}
            style={{ width: `${Math.min(100, alert.confidenceScore)}%` }}
          />
        </div>
      </div>

      {/* Suggested Stake */}
      {alert.suggestedStakeCOP > 0 && (
        <div className={`
          rounded-lg p-3 mb-3 text-center border
          ${isElite
            ? 'bg-yellow-500/10 border-yellow-500/30'
            : 'bg-gray-800/60 border-gray-700/30'
          }
        `}>
          <div className="text-gray-400 text-xs mb-1">Sugerencia de inversi&oacute;n</div>
          <div className="text-brand-green font-bold text-lg">
            {formatCOP(alert.suggestedStakeCOP)}
          </div>
          <div className="text-gray-500 text-[10px] mt-0.5">
            Kelly {alert.recommendedStakePct.toFixed(1)}% del bankroll
          </div>
        </div>
      )}

      {/* Action Button */}
      {alert.oddsStale ? (
        <div className="w-full py-2.5 rounded-lg text-center text-sm font-medium bg-gray-800 text-yellow-400/70 border border-yellow-500/20">
          Cuota no disponible — esperando datos frescos
        </div>
      ) : (
        <button className={`
          w-full py-2.5 rounded-lg font-bold text-sm tracking-wider
          transition-all duration-200 active:scale-95
          ${isElite
            ? 'bg-yellow-500 hover:bg-yellow-400 text-black shadow-lg shadow-yellow-500/25'
            : 'bg-brand-green hover:bg-green-400 text-black shadow-lg shadow-green-500/25'
          }
        `}>
          Ir a {bookmaker}
        </button>
      )}
    </div>
  );
}

function StatBox({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="bg-gray-900/40 rounded-md p-2 text-center">
      <div className="text-gray-500 text-[10px] uppercase tracking-wider">{label}</div>
      <div className={`${color} font-bold text-sm mt-0.5`}>{value}</div>
    </div>
  );
}

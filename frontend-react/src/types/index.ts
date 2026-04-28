export type Rank = 'ELITE' | 'ALTA' | 'MEDIA' | 'BAJA';

export interface ValueBetAlert {
  id: string;
  eventId: number;
  homeTeam: string;
  awayTeam: string;
  league: string;
  status: string;
  minute: number;
  score: string;
  market: string;
  selection: string;
  selectionLabel: string;
  modelProbability: number;
  impliedProbability: number;
  bookmakerOdds: number;
  fairOdds: number;
  expectedValue: number;
  edgePercent: number;
  kellyFraction: number;
  recommendedStakePct: number;
  suggestedStakeCOP: number;
  confidenceScore: number;
  rank: Rank;
  timestamp: string;
  paperBetId: number | null;
  bookmakerName: string | null;
  oddsStale: boolean;
}

export interface PaperBet {
  id: number;
  matchId: number;
  homeTeam: string;
  awayTeam: string;
  league: string;
  market: string;
  selection: string;
  selectionLabel: string;
  oddsAtBet: number;
  impliedProbability: number;
  monteCarloProbability: number;
  expectedValue: number;
  edgePercent: number;
  kellyFraction: number;
  confidenceScore: number;
  rank: string;
  stakeAmount: number;
  stakePercent: number;
  profitLoss: number;
  bankrollBefore: number;
  bankrollAfter: number;
  status: 'PENDING' | 'WON' | 'LOST' | 'VOID';
  matchMinute: number;
  matchScore: string;
  placedAt: string;
  settledAt: string | null;
}

export interface MinuteSplitStats {
  bets: number;
  won: number;
  profitLoss: number;
  staked: number;
  roi: number;
  winRate: number;
}

export interface PerformanceStats {
  initialBankroll: number;
  currentBankroll: number;
  totalProfitLoss: number;
  growthPercent: number;
  totalBets: number;
  won: number;
  lost: number;
  pending: number;
  winRate: number;
  roi: number;
  yield: number;
  totalStaked: number;
  maxStakePercent: number;
  kellyFraction: number;
  before80: MinuteSplitStats;
  after80: MinuteSplitStats;
}

export interface BankrollPoint {
  timestamp: string;
  bankroll: number;
  label: string;
  profitLoss?: number;
  status?: string;
}

export interface LiveMatch {
  eventId: number;
  homeTeam: string;
  awayTeam: string;
  league: string;
  status: string;
  minute: number;
  homeGoals: number;
  awayGoals: number;
  lastUpdated: string;
  odds: Record<string, Record<string, number>>;
}

export interface StatusResponse {
  liveMatches: number;
  activeAlerts: number;
  historySize: number;
  rateLimited: boolean;
  rateLimitSecondsRemaining: number;
}

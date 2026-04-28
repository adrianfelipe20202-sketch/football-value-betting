package com.valuebetting.service;

import com.valuebetting.model.BankrollConfig;
import com.valuebetting.model.LiveMatch;
import com.valuebetting.model.PaperBet;
import com.valuebetting.model.ValueBetAlert;
import com.valuebetting.repository.BankrollConfigRepository;
import com.valuebetting.repository.PaperBetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class BankrollService {

    private static final Logger log = LoggerFactory.getLogger(BankrollService.class);

    private final PaperBetRepository paperBetRepo;
    private final BankrollConfigRepository bankrollConfigRepo;
    private final OddsFetcherService oddsFetcher;

    @Value("${bankroll.initial:1000000}")
    private double fallbackInitialBankroll;

    @Value("${value.engine.kelly-fraction}")
    private double kellyFractionMultiplier;

    private static final double MAX_STAKE_PERCENT = 0.05;

    public BankrollService(PaperBetRepository paperBetRepo, BankrollConfigRepository bankrollConfigRepo,
                           OddsFetcherService oddsFetcher) {
        this.paperBetRepo = paperBetRepo;
        this.bankrollConfigRepo = bankrollConfigRepo;
        this.oddsFetcher = oddsFetcher;
    }

    public double calculateKellyStakePercent(double mcProbability, double decimalOdds) {
        double b = decimalOdds;
        double p = mcProbability;
        double kellyFull = ((p * (b - 1)) - (1 - p)) / (b - 1);
        if (kellyFull <= 0) return 0;
        double kellyFractional = kellyFull * kellyFractionMultiplier;
        return Math.min(kellyFractional, MAX_STAKE_PERCENT);
    }

    public double calculateStakeAmount(double stakePercent) {
        return getCurrentBankroll() * stakePercent;
    }

    public double getCurrentBankroll() {
        double totalPL = paperBetRepo.totalProfitLoss();
        return getInitialBankroll() + totalPL;
    }

    public double getInitialBankroll() {
        return bankrollConfigRepo.findAll().stream()
                .findFirst()
                .map(BankrollConfig::getInitialBankroll)
                .orElse(fallbackInitialBankroll);
    }

    // =====================================================
    // AUTO-SETTLEMENT: evaluación inteligente por mercado
    // =====================================================

    @Scheduled(fixedDelay = 20_000, initialDelay = 15_000)
    public void autoSettlePendingBets() {
        List<PaperBet> pending = paperBetRepo.findByStatusOrderByPlacedAtDesc(PaperBet.Status.PENDING);
        if (pending.isEmpty()) return;

        Map<Long, LiveMatch> liveMatches = oddsFetcher.getAllLiveMatches();
        int settled = 0;

        for (PaperBet bet : pending) {
            LiveMatch match = liveMatches.get(bet.getMatchId());
            if (match == null) continue;

            Boolean won = evaluateBetOutcome(bet, match);
            if (won != null) {
                settleInternal(bet, won);
                settled++;
                log.info("AUTO-SETTLED #{} {} | {} {} @ {} | {} | Score: {}",
                        bet.getId(), won ? "WON" : "LOST",
                        bet.getMarket(), bet.getSelectionLabel(), bet.getOddsAtBet(),
                        bet.getHomeTeam() + " vs " + bet.getAwayTeam(),
                        match.getHomeGoals() + "-" + match.getAwayGoals());
            }
        }

        if (settled > 0) {
            log.info("Auto-settled {} bets this cycle", settled);
        }
    }

    /**
     * Evalúa si una bet se puede resolver con el estado actual del partido.
     * Retorna true=WON, false=LOST, null=no se puede determinar aún.
     */
    public Boolean evaluateBetOutcome(PaperBet bet, LiveMatch match) {
        int totalGoals = match.getHomeGoals() + match.getAwayGoals();
        int homeGoals = match.getHomeGoals();
        int awayGoals = match.getAwayGoals();
        boolean isFinished = "FINISHED".equals(match.getStatus()) || "FT".equals(match.getStatus());
        String market = bet.getMarket();
        String selection = bet.getSelection();

        return switch (market) {
            case "Over/Under" -> evaluateOverUnder(selection, totalGoals, isFinished);
            case "1X2" -> evaluate1X2(selection, homeGoals, awayGoals, isFinished);
            case "Ambos Marcan" -> evaluateBTTS(selection, homeGoals, awayGoals, isFinished);
            case "Doble Oportunidad" -> evaluateDoubleChance(selection, homeGoals, awayGoals, isFinished);
            default -> isFinished ? false : null;
        };
    }

    private Boolean evaluateOverUnder(String selection, int totalGoals, boolean isFinished) {
        if (selection.startsWith("over_")) {
            double line = Double.parseDouble(selection.replace("over_", ""));
            if (totalGoals > line) return true;
            if (isFinished) return false;
            return null;
        }
        if (selection.startsWith("under_")) {
            double line = Double.parseDouble(selection.replace("under_", ""));
            if (totalGoals > line) return false;
            if (isFinished) return true;
            return null;
        }
        return null;
    }

    private Boolean evaluate1X2(String selection, int homeGoals, int awayGoals, boolean isFinished) {
        if (!isFinished) return null;
        return switch (selection) {
            case "1" -> homeGoals > awayGoals;
            case "X" -> homeGoals == awayGoals;
            case "2" -> awayGoals > homeGoals;
            default -> null;
        };
    }

    private Boolean evaluateBTTS(String selection, int homeGoals, int awayGoals, boolean isFinished) {
        boolean bothScored = homeGoals > 0 && awayGoals > 0;
        if (selection.equals("yes")) {
            if (bothScored) return true;
            if (isFinished) return false;
            return null;
        }
        if (selection.equals("no")) {
            if (bothScored) return false;
            if (isFinished) return true;
            return null;
        }
        return null;
    }

    private Boolean evaluateDoubleChance(String selection, int homeGoals, int awayGoals, boolean isFinished) {
        if (!isFinished) return null;
        return switch (selection) {
            case "1X" -> homeGoals >= awayGoals;
            case "X2" -> awayGoals >= homeGoals;
            case "12" -> homeGoals != awayGoals;
            default -> null;
        };
    }

    /**
     * Corrige bets históricas mal clasificadas.
     * Se ejecuta una vez al iniciar y puede invocarse manualmente.
     */
    public int correctMisclassifiedBets() {
        List<PaperBet> allSettled = paperBetRepo.findAllByOrderByPlacedAtDesc().stream()
                .filter(b -> b.getStatus() == PaperBet.Status.WON || b.getStatus() == PaperBet.Status.LOST)
                .toList();

        Map<Long, LiveMatch> liveMatches = oddsFetcher.getAllLiveMatches();
        int corrected = 0;

        for (PaperBet bet : allSettled) {
            LiveMatch match = liveMatches.get(bet.getMatchId());
            if (match == null) continue;

            Boolean shouldBeWon = evaluateBetOutcome(bet, match);
            if (shouldBeWon == null) continue;

            boolean currentlyWon = bet.getStatus() == PaperBet.Status.WON;
            if (shouldBeWon != currentlyWon) {
                PaperBet.Status oldStatus = bet.getStatus();
                if (shouldBeWon) {
                    bet.setStatus(PaperBet.Status.WON);
                    bet.setProfitLoss(bet.getStakeAmount() * (bet.getOddsAtBet() - 1));
                } else {
                    bet.setStatus(PaperBet.Status.LOST);
                    bet.setProfitLoss(-bet.getStakeAmount());
                }
                bet.setBankrollAfter(getCurrentBankroll() + bet.getProfitLoss());
                paperBetRepo.save(bet);
                corrected++;
                log.warn("CORRECTED #{} {} -> {} | {} {} | P/L: ${}",
                        bet.getId(), oldStatus, bet.getStatus(),
                        bet.getMarket(), bet.getSelectionLabel(), (long) bet.getProfitLoss());
            }
        }

        if (corrected > 0) {
            log.info("Corrected {} misclassified bets", corrected);
        }
        return corrected;
    }

    // =====================================================
    // Colocación y settlement manual
    // =====================================================

    public PaperBet placePaperBet(ValueBetAlert alert) {
        if (paperBetRepo.existsByMatchIdAndMarketAndSelectionAndStatus(
                alert.getEventId(), alert.getMarket(), alert.getSelection(), PaperBet.Status.PENDING)) {
            return null;
        }

        double stakePercent = calculateKellyStakePercent(
                alert.getModelProbability(), alert.getBookmakerOdds());
        if (stakePercent <= 0) return null;

        double currentBankroll = getCurrentBankroll();
        double stakeAmount = Math.min(Math.round(currentBankroll * stakePercent), currentBankroll);

        PaperBet bet = new PaperBet();
        bet.setMatchId(alert.getEventId());
        bet.setHomeTeam(alert.getHomeTeam());
        bet.setAwayTeam(alert.getAwayTeam());
        bet.setLeague(alert.getLeague());
        bet.setMarket(alert.getMarket());
        bet.setSelection(alert.getSelection());
        bet.setSelectionLabel(alert.getSelectionLabel());
        bet.setOddsAtBet(alert.getBookmakerOdds());
        bet.setImpliedProbability(alert.getImpliedProbability());
        bet.setMonteCarloProbability(alert.getModelProbability());
        bet.setExpectedValue(alert.getExpectedValue());
        bet.setEdgePercent(alert.getEdgePercent());
        bet.setKellyFraction(stakePercent);
        bet.setConfidenceScore(alert.getConfidenceScore());
        bet.setRank(alert.getRank().name());
        bet.setStakeAmount(stakeAmount);
        bet.setStakePercent(stakePercent * 100);
        bet.setBankrollBefore(currentBankroll);
        bet.setMatchMinute(alert.getMinute());
        bet.setMatchScore(alert.getScore());
        bet.setStatus(PaperBet.Status.PENDING);
        bet.setPlacedAt(Instant.now());

        paperBetRepo.save(bet);

        log.info("PAPER BET #{} | {} vs {} | {} {} @ {} | Stake: ${} COP ({}%)",
                bet.getId(), bet.getHomeTeam(), bet.getAwayTeam(),
                bet.getMarket(), bet.getSelectionLabel(), bet.getOddsAtBet(),
                (long) bet.getStakeAmount(), String.format("%.1f", bet.getStakePercent()));

        return bet;
    }

    private void settleInternal(PaperBet bet, boolean won) {
        if (won) {
            bet.setStatus(PaperBet.Status.WON);
            bet.setProfitLoss(bet.getStakeAmount() * (bet.getOddsAtBet() - 1));
        } else {
            bet.setStatus(PaperBet.Status.LOST);
            bet.setProfitLoss(-bet.getStakeAmount());
        }
        bet.setBankrollAfter(getCurrentBankroll() + bet.getProfitLoss());
        bet.setSettledAt(Instant.now());
        paperBetRepo.save(bet);
    }

    public void settleBet(Long betId, boolean won) {
        PaperBet bet = paperBetRepo.findById(betId).orElse(null);
        if (bet == null || bet.getStatus() != PaperBet.Status.PENDING) return;
        settleInternal(bet, won);
        log.info("MANUAL SETTLED #{} {} | P/L: ${} COP | Bankroll: ${} COP",
                bet.getId(), bet.getStatus(),
                (long) bet.getProfitLoss(), (long) bet.getBankrollAfter());
    }

    public void voidBet(Long betId) {
        PaperBet bet = paperBetRepo.findById(betId).orElse(null);
        if (bet == null || bet.getStatus() != PaperBet.Status.PENDING) return;
        bet.setStatus(PaperBet.Status.VOID);
        bet.setProfitLoss(0);
        bet.setBankrollAfter(getCurrentBankroll());
        bet.setSettledAt(Instant.now());
        paperBetRepo.save(bet);
    }

    // =====================================================
    // Performance stats y historial
    // =====================================================

    public Map<String, Object> getPerformanceStats() {
        long totalBets = paperBetRepo.count();
        long won = paperBetRepo.countWon();
        long lost = paperBetRepo.countLost();
        long pending = paperBetRepo.countPending();
        double totalPL = paperBetRepo.totalProfitLoss();
        double totalStaked = paperBetRepo.totalStaked();
        double currentBankroll = getCurrentBankroll();

        double roi = totalStaked > 0 ? (totalPL / totalStaked) * 100 : 0;
        double yield = (won + lost) > 0 ? (totalPL / totalStaked) * 100 : 0;
        double winRate = (won + lost) > 0 ? ((double) won / (won + lost)) * 100 : 0;
        double initBankroll = getInitialBankroll();
        double growthPercent = ((currentBankroll - initBankroll) / initBankroll) * 100;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("initialBankroll", initBankroll);
        stats.put("currentBankroll", currentBankroll);
        stats.put("totalProfitLoss", totalPL);
        stats.put("growthPercent", Math.round(growthPercent * 100.0) / 100.0);
        stats.put("totalBets", totalBets);
        stats.put("won", won);
        stats.put("lost", lost);
        stats.put("pending", pending);
        stats.put("winRate", Math.round(winRate * 100.0) / 100.0);
        stats.put("roi", Math.round(roi * 100.0) / 100.0);
        stats.put("yield", Math.round(yield * 100.0) / 100.0);
        stats.put("totalStaked", totalStaked);
        stats.put("maxStakePercent", MAX_STAKE_PERCENT * 100);
        stats.put("kellyFraction", kellyFractionMultiplier);

        // ROI split: antes vs después del minuto 80
        double plBefore80 = paperBetRepo.profitLossBefore80();
        double stakedBefore80 = paperBetRepo.stakedBefore80();
        long settledBefore80 = paperBetRepo.settledCountBefore80();
        long wonBefore80 = paperBetRepo.wonBefore80();
        double roiBefore80 = stakedBefore80 > 0 ? (plBefore80 / stakedBefore80) * 100 : 0;
        double winRateBefore80 = settledBefore80 > 0 ? ((double) wonBefore80 / settledBefore80) * 100 : 0;

        double plAfter80 = paperBetRepo.profitLossAfter80();
        double stakedAfter80 = paperBetRepo.stakedAfter80();
        long settledAfter80 = paperBetRepo.settledCountAfter80();
        long wonAfter80 = paperBetRepo.wonAfter80();
        double roiAfter80 = stakedAfter80 > 0 ? (plAfter80 / stakedAfter80) * 100 : 0;
        double winRateAfter80 = settledAfter80 > 0 ? ((double) wonAfter80 / settledAfter80) * 100 : 0;

        Map<String, Object> before80 = new LinkedHashMap<>();
        before80.put("bets", settledBefore80);
        before80.put("won", wonBefore80);
        before80.put("profitLoss", plBefore80);
        before80.put("staked", stakedBefore80);
        before80.put("roi", Math.round(roiBefore80 * 100.0) / 100.0);
        before80.put("winRate", Math.round(winRateBefore80 * 100.0) / 100.0);

        Map<String, Object> after80 = new LinkedHashMap<>();
        after80.put("bets", settledAfter80);
        after80.put("won", wonAfter80);
        after80.put("profitLoss", plAfter80);
        after80.put("staked", stakedAfter80);
        after80.put("roi", Math.round(roiAfter80 * 100.0) / 100.0);
        after80.put("winRate", Math.round(winRateAfter80 * 100.0) / 100.0);

        stats.put("before80", before80);
        stats.put("after80", after80);

        return stats;
    }

    public List<Map<String, Object>> getBankrollHistory() {
        List<PaperBet> settled = paperBetRepo.findAllByOrderByPlacedAtDesc()
                .stream()
                .filter(b -> b.getStatus() == PaperBet.Status.WON || b.getStatus() == PaperBet.Status.LOST)
                .sorted(Comparator.comparing(PaperBet::getSettledAt))
                .toList();

        double initBankroll = getInitialBankroll();
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of(
                "timestamp", Instant.now().minusSeconds(86400).toString(),
                "bankroll", initBankroll,
                "label", "Inicio"
        ));

        double running = initBankroll;
        for (PaperBet bet : settled) {
            running += bet.getProfitLoss();
            history.add(Map.of(
                    "timestamp", bet.getSettledAt().toString(),
                    "bankroll", running,
                    "label", bet.getHomeTeam() + " vs " + bet.getAwayTeam(),
                    "profitLoss", bet.getProfitLoss(),
                    "status", bet.getStatus().name()
            ));
        }
        return history;
    }
}

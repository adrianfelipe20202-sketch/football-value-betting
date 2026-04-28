package com.valuebetting.service;

import com.valuebetting.model.LiveMatch;
import com.valuebetting.model.MonteCarloResult;
import com.valuebetting.model.ValueBetAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ValueBetAgent {

    private static final Logger log = LoggerFactory.getLogger(ValueBetAgent.class);

    private final OddsFetcherService oddsFetcher;
    private final SimpMessagingTemplate messagingTemplate;
    private final BankrollService bankrollService;

    @Value("${value.engine.min-ev-threshold}")
    private double minEvThreshold;

    @Value("${value.engine.elite-ev-threshold}")
    private double eliteEvThreshold;

    @Value("${value.engine.elite-confidence}")
    private double eliteConfidence;

    @Value("${value.engine.kelly-fraction}")
    private double kellyFraction;

    private final Map<String, ValueBetAlert> activeAlerts = new ConcurrentHashMap<>();
    private final List<ValueBetAlert> alertHistory = Collections.synchronizedList(new ArrayList<>());

    private static final int MAX_HISTORY = 500;
    private static final int SIMULATIONS = 10_000;
    private static final double LATE_GAME_EV_THRESHOLD = 0.12;
    private static final int LATE_GAME_MINUTE = 80;

    public ValueBetAgent(OddsFetcherService oddsFetcher, SimpMessagingTemplate messagingTemplate,
                         BankrollService bankrollService) {
        this.oddsFetcher = oddsFetcher;
        this.messagingTemplate = messagingTemplate;
        this.bankrollService = bankrollService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void evaluate() {
        Map<Long, LiveMatch> matches = oddsFetcher.getAllLiveMatches();
        if (matches.isEmpty()) return;

        log.info("Evaluating {} live matches for value bets...", matches.size());
        int alertCount = 0;

        for (LiveMatch match : matches.values()) {
            if (match.getOdds().isEmpty()) continue;

            double effectiveEvThreshold = getEffectiveEvThreshold(match);

            MonteCarloResult mcResult = runMonteCarlo(match);
            List<ValueBetAlert> alerts = compareOddsVsProbabilities(match, mcResult, effectiveEvThreshold);

            for (ValueBetAlert alert : alerts) {
                String dedupKey = alert.getEventId() + ":" + alert.getMarket() + ":" + alert.getSelection();
                boolean isNew = !activeAlerts.containsKey(dedupKey);

                double stakePct = bankrollService.calculateKellyStakePercent(
                        alert.getModelProbability(), alert.getBookmakerOdds());
                double stakeCOP = bankrollService.calculateStakeAmount(stakePct);
                alert.setSuggestedStakeCOP(Math.round(stakeCOP));
                alert.setRecommendedStakePct(stakePct * 100);

                if (isNew) {
                    if (!alert.isOddsStale()) {
                        var paperBet = bankrollService.placePaperBet(alert);
                        if (paperBet != null) {
                            alert.setPaperBetId(paperBet.getId());
                        }
                    }
                    addToHistory(alert);
                }

                alert.setId(dedupKey);
                activeAlerts.put(dedupKey, alert);
                messagingTemplate.convertAndSend("/topic/alerts", alert);
                messagingTemplate.convertAndSend("/topic/value-alerts", alert);
                alertCount++;

                log.info("ALERT [{}] {} | {} {} @ {} | EV={}% | Edge={}%{}",
                        alert.getRank(), alert.getHomeTeam() + " vs " + alert.getAwayTeam(),
                        alert.getMarket(), alert.getSelection(), alert.getBookmakerOdds(),
                        String.format("%.1f", alert.getExpectedValue() * 100),
                        String.format("%.1f", alert.getEdgePercent()),
                        match.getMinute() >= LATE_GAME_MINUTE ? " [LATE-GAME FILTER]" : "");
            }
        }

        purgeExpiredAlerts();
        if (alertCount > 0) {
            log.info("Generated {} value bet alerts", alertCount);
        }
    }

    // =====================================================
    // PRIORIDAD 4: Filtro Trampa Minuto 80
    // =====================================================

    private double getEffectiveEvThreshold(LiveMatch match) {
        if ("LIVE".equals(match.getStatus()) && match.getMinute() >= LATE_GAME_MINUTE) {
            log.debug("Late-game filter active for {} (min {}): EV threshold raised to {}%",
                    match.getMatchLabel(), match.getMinute(), LATE_GAME_EV_THRESHOLD * 100);
            return LATE_GAME_EV_THRESHOLD;
        }
        return minEvThreshold;
    }

    // =====================================================
    // MONTE CARLO con Time Decay + Red Cards
    // =====================================================

    private MonteCarloResult runMonteCarlo(LiveMatch match) {
        double homeStrength = estimateStrength(match, true);
        double awayStrength = estimateStrength(match, false);

        double baseLambdaHome = 1.35 * homeStrength;
        double baseLambdaAway = 1.10 * awayStrength;

        // Ajuste por tarjetas rojas: -30% ataque para el equipo con expulsado
        if (match.getRedCardsHome() > 0) {
            double redCardPenalty = 1.0 - (0.30 * match.getRedCardsHome());
            baseLambdaHome *= Math.max(0.3, redCardPenalty);
            baseLambdaAway *= 1.15; // el rival ataca más contra 10
        }
        if (match.getRedCardsAway() > 0) {
            double redCardPenalty = 1.0 - (0.30 * match.getRedCardsAway());
            baseLambdaAway *= Math.max(0.3, redCardPenalty);
            baseLambdaHome *= 1.15;
        }

        // Dangerous Attacks boost: si la tasa supera 1.5 ataques/min, partido abierto
        double attackRate = match.getDangerousAttackRate();
        if (attackRate > 1.5) {
            double attackBoost = 1.05;
            baseLambdaHome *= attackBoost;
            baseLambdaAway *= attackBoost;
            log.debug("Dangerous attacks boost for {} — rate {}/min > 1.5 — lambdas *1.05",
                    match.getMatchLabel(), String.format("%.2f", attackRate));
        }

        if ("LIVE".equals(match.getStatus()) && match.getMinute() > 0) {
            double remainingFraction = Math.max(0, (90.0 - match.getMinute()) / 90.0);
            baseLambdaHome *= remainingFraction;
            baseLambdaAway *= remainingFraction;
        }

        // Recalcular lambdas con Time Decay aplicado
        double finalLambdaHome = baseLambdaHome;
        double finalLambdaAway = baseLambdaAway;

        if ("LIVE".equals(match.getStatus()) && match.getMinute() >= 75) {
            double[] adjusted = applyTimeDecayFactors(baseLambdaHome, baseLambdaAway, match);
            finalLambdaHome = adjusted[0];
            finalLambdaAway = adjusted[1];
        }

        int[] homeGoalsDist = new int[11];
        int[] awayGoalsDist = new int[11];
        int homeWins = 0, draws = 0, awayWins = 0;
        int over25 = 0, under25 = 0;
        int bttsYes = 0, bttsNo = 0;
        int over15 = 0, over35 = 0;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < SIMULATIONS; i++) {
            int hg = poissonSample(finalLambdaHome, rng);
            int ag = poissonSample(finalLambdaAway, rng);

            if ("LIVE".equals(match.getStatus())) {
                hg += match.getHomeGoals();
                ag += match.getAwayGoals();
            }

            if (hg < homeGoalsDist.length) homeGoalsDist[hg]++;
            if (ag < awayGoalsDist.length) awayGoalsDist[ag]++;

            int totalGoals = hg + ag;
            if (hg > ag) homeWins++;
            else if (hg == ag) draws++;
            else awayWins++;

            if (totalGoals > 2) over25++;
            else under25++;

            if (totalGoals > 1) over15++;
            if (totalGoals > 3) over35++;

            if (hg > 0 && ag > 0) bttsYes++;
            else bttsNo++;
        }

        MonteCarloResult result = new MonteCarloResult();
        result.setEventId(match.getEventId());
        result.setHomeTeam(match.getHomeTeam());
        result.setAwayTeam(match.getAwayTeam());
        result.setExpectedHomeGoals(finalLambdaHome + ("LIVE".equals(match.getStatus()) ? match.getHomeGoals() : 0));
        result.setExpectedAwayGoals(finalLambdaAway + ("LIVE".equals(match.getStatus()) ? match.getAwayGoals() : 0));
        result.setSimulations(SIMULATIONS);

        Map<String, Map<String, Double>> probabilities = new LinkedHashMap<>();

        Map<String, Double> p1x2 = new LinkedHashMap<>();
        p1x2.put("1", (double) homeWins / SIMULATIONS);
        p1x2.put("X", (double) draws / SIMULATIONS);
        p1x2.put("2", (double) awayWins / SIMULATIONS);
        probabilities.put("1x2", p1x2);

        Map<String, Double> pOU = new LinkedHashMap<>();
        pOU.put("over_1.5", (double) over15 / SIMULATIONS);
        pOU.put("under_1.5", 1.0 - (double) over15 / SIMULATIONS);
        pOU.put("over_2.5", (double) over25 / SIMULATIONS);
        pOU.put("under_2.5", (double) under25 / SIMULATIONS);
        pOU.put("over_3.5", (double) over35 / SIMULATIONS);
        pOU.put("under_3.5", 1.0 - (double) over35 / SIMULATIONS);
        probabilities.put("over_under", pOU);

        Map<String, Double> pBtts = new LinkedHashMap<>();
        pBtts.put("yes", (double) bttsYes / SIMULATIONS);
        pBtts.put("no", (double) bttsNo / SIMULATIONS);
        probabilities.put("btts", pBtts);

        Map<String, Double> pDC = new LinkedHashMap<>();
        pDC.put("1X", (double) (homeWins + draws) / SIMULATIONS);
        pDC.put("12", (double) (homeWins + awayWins) / SIMULATIONS);
        pDC.put("X2", (double) (draws + awayWins) / SIMULATIONS);
        probabilities.put("double_chance", pDC);

        result.setProbabilities(probabilities);
        return result;
    }

    // =====================================================
    // PRIORIDAD 2: Time Decay no lineal (minuto 75+)
    // =====================================================

    private double[] applyTimeDecayFactors(double lambdaHome, double lambdaAway, LiveMatch match) {
        int goalDiff = match.getHomeGoals() - match.getAwayGoals();

        if (goalDiff == 0) {
            // Empate: ambos equipos se protegen, -15% producción ofensiva
            lambdaHome *= 0.85;
            lambdaAway *= 0.85;
            log.debug("Time Decay [DRAW]: {} min {} — lambdas reduced 15%",
                    match.getMatchLabel(), match.getMinute());
        } else if (Math.abs(goalDiff) == 1) {
            if (goalDiff > 0) {
                // Local gana por 1: local defiende (-20%), visitante empuja (+20%)
                lambdaHome *= 0.80;
                lambdaAway *= 1.20;
            } else {
                // Visitante gana por 1: visitante defiende (-20%), local empuja (+20%)
                lambdaHome *= 1.20;
                lambdaAway *= 0.80;
            }
            log.debug("Time Decay [1-GOAL DIFF]: {} min {} — losing team +20%, winning -20%",
                    match.getMatchLabel(), match.getMinute());
        }
        // Si la diferencia es >= 2, no se aplica ajuste especial (partido "muerto")

        return new double[]{lambdaHome, lambdaAway};
    }

    // =====================================================
    // PRIORIDAD 3: estimateStrength con Red Cards
    // =====================================================

    private double estimateStrength(LiveMatch match, boolean isHome) {
        Map<String, Map<String, Double>> odds = match.getOdds();
        Map<String, Double> market1x2 = odds.get("1x2");

        double baseStrength;

        if (market1x2 != null) {
            Double homeOdds = market1x2.get("1");
            Double awayOdds = market1x2.get("2");
            if (homeOdds != null && awayOdds != null && homeOdds > 1 && awayOdds > 1) {
                double homeImplied = 1.0 / homeOdds;
                double awayImplied = 1.0 / awayOdds;
                double total = homeImplied + awayImplied;
                baseStrength = isHome
                        ? 0.7 + (homeImplied / total) * 0.6
                        : 0.7 + (awayImplied / total) * 0.6;
            } else {
                baseStrength = isHome ? 1.05 : 0.95;
            }
        } else {
            baseStrength = isHome ? 1.05 : 0.95;
        }

        // Penalización por tarjetas rojas: -30% fuerza por cada roja
        int redCards = isHome ? match.getRedCardsHome() : match.getRedCardsAway();
        if (redCards > 0) {
            double penalty = 1.0 - (0.30 * redCards);
            baseStrength *= Math.max(0.3, penalty);
            log.debug("Red card penalty for {} ({}): strength *= {}",
                    isHome ? match.getHomeTeam() : match.getAwayTeam(),
                    redCards, Math.max(0.3, penalty));
        }

        return baseStrength;
    }

    private int poissonSample(double lambda, ThreadLocalRandom rng) {
        if (lambda <= 0) return 0;
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > L);
        return k - 1;
    }

    private List<ValueBetAlert> compareOddsVsProbabilities(LiveMatch match, MonteCarloResult mcResult,
                                                            double effectiveEvThreshold) {
        List<ValueBetAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, Map<String, Double>> marketEntry : match.getOdds().entrySet()) {
            String marketKey = marketEntry.getKey();
            Map<String, Double> bookmakerOdds = marketEntry.getValue();

            // Filtro de mercado suspendido: no emitir señales fantasma
            if (match.isMarketSuspended(marketKey)) {
                log.debug("Skipping suspended market '{}' for {}", marketKey, match.getMatchLabel());
                continue;
            }

            Map<String, Double> modelProbs = mcResult.getProbabilities().get(marketKey);
            if (modelProbs == null) continue;

            for (Map.Entry<String, Double> selectionEntry : bookmakerOdds.entrySet()) {
                String selectionKey = selectionEntry.getKey();
                double odds = selectionEntry.getValue();

                Double modelProb = modelProbs.get(selectionKey);
                if (modelProb == null || modelProb <= 0) continue;

                double impliedProb = 1.0 / odds;
                double ev = (modelProb * odds) - 1.0;

                if (ev > 1.0) {
                    log.debug("Rejecting suspicious EV {}% for {} {} @ {} — likely stale odds",
                            String.format("%.0f", ev * 100), marketKey, selectionKey, odds);
                    continue;
                }

                if (ev >= effectiveEvThreshold) {
                    ValueBetAlert alert = buildAlert(match, marketKey, selectionKey, odds, modelProb, impliedProb, ev);
                    alerts.add(alert);
                }
            }
        }

        alerts.sort(Comparator.comparingDouble(ValueBetAlert::getConfidenceScore).reversed());
        return alerts;
    }

    private ValueBetAlert buildAlert(LiveMatch match, String market, String selection,
                                     double odds, double modelProb, double impliedProb, double ev) {
        ValueBetAlert alert = new ValueBetAlert();
        alert.setId(match.getEventId() + "_" + market + "_" + selection + "_" + System.currentTimeMillis());
        alert.setEventId(match.getEventId());
        alert.setHomeTeam(match.getHomeTeam());
        alert.setAwayTeam(match.getAwayTeam());
        alert.setLeague(match.getLeague());
        alert.setStatus(match.getStatus());
        alert.setMinute(match.getMinute());
        alert.setScore(match.getHomeGoals() + " - " + match.getAwayGoals());

        alert.setMarket(formatMarketName(market));
        alert.setSelection(selection);
        alert.setSelectionLabel(formatSelectionLabel(market, selection, match));

        alert.setModelProbability(modelProb);
        alert.setImpliedProbability(impliedProb);
        alert.setBookmakerOdds(odds);
        alert.setFairOdds(1.0 / modelProb);
        alert.setExpectedValue(ev);
        alert.setEdgePercent((modelProb - impliedProb) * 100);

        double kelly = (modelProb * odds - 1.0) / (odds - 1.0);
        alert.setKellyFraction(Math.max(0, kelly));
        alert.setRecommendedStakePct(Math.max(0, kelly * kellyFraction * 100));
        alert.setBookmakerName(match.getBookmakerFor(market, selection));
        alert.setOddsStale(oddsFetcher.isOddsStale(match.getEventId()));

        double confidenceScore = calculateConfidence(ev, modelProb, impliedProb);
        alert.setConfidenceScore(confidenceScore);
        alert.setRank(classifyRank(confidenceScore, ev));
        alert.setTimestamp(Instant.now());

        return alert;
    }

    private double calculateConfidence(double ev, double modelProb, double impliedProb) {
        double evScore = Math.min(100, (ev / 0.30) * 100) * 0.40;
        double edgeScore = Math.min(100, ((modelProb - impliedProb) / 0.20) * 100) * 0.30;
        double probScore = Math.min(100, modelProb * 100) * 0.30;
        return Math.min(100, evScore + edgeScore + probScore);
    }

    private ValueBetAlert.Rank classifyRank(double confidence, double ev) {
        if (confidence >= eliteConfidence && ev >= eliteEvThreshold) return ValueBetAlert.Rank.ELITE;
        if (confidence >= 60) return ValueBetAlert.Rank.ALTA;
        if (confidence >= 35) return ValueBetAlert.Rank.MEDIA;
        return ValueBetAlert.Rank.BAJA;
    }

    private String formatMarketName(String key) {
        return switch (key) {
            case "1x2" -> "1X2";
            case "over_under" -> "Over/Under";
            case "btts" -> "Ambos Marcan";
            case "double_chance" -> "Doble Oportunidad";
            case "corners" -> "Córners";
            case "cards" -> "Tarjetas";
            default -> key;
        };
    }

    private String formatSelectionLabel(String market, String selection, LiveMatch match) {
        return switch (market) {
            case "1x2" -> switch (selection) {
                case "1" -> match.getHomeTeam();
                case "X" -> "Empate";
                case "2" -> match.getAwayTeam();
                default -> selection;
            };
            case "over_under" -> {
                if (selection.startsWith("over_")) yield "Más de " + selection.replace("over_", "");
                if (selection.startsWith("under_")) yield "Menos de " + selection.replace("under_", "");
                yield selection;
            }
            case "btts" -> selection.equals("yes") ? "Sí" : "No";
            case "double_chance" -> switch (selection) {
                case "1X" -> match.getHomeTeam() + " o Empate";
                case "12" -> match.getHomeTeam() + " o " + match.getAwayTeam();
                case "X2" -> "Empate o " + match.getAwayTeam();
                default -> selection;
            };
            default -> selection;
        };
    }

    private void addToHistory(ValueBetAlert alert) {
        alertHistory.add(alert);
        while (alertHistory.size() > MAX_HISTORY) {
            alertHistory.remove(0);
        }
    }

    private void purgeExpiredAlerts() {
        Instant cutoff = Instant.now().minusSeconds(600);
        activeAlerts.entrySet().removeIf(e -> e.getValue().getTimestamp().isBefore(cutoff));
    }

    // --- API pública ---

    public Map<String, ValueBetAlert> getActiveAlerts() {
        return Collections.unmodifiableMap(activeAlerts);
    }

    public List<ValueBetAlert> getAlertHistory() {
        return Collections.unmodifiableList(alertHistory);
    }

    public List<ValueBetAlert> getAlertsByRank(ValueBetAlert.Rank rank) {
        return activeAlerts.values().stream()
                .filter(a -> a.getRank() == rank)
                .sorted(Comparator.comparingDouble(ValueBetAlert::getConfidenceScore).reversed())
                .toList();
    }

    public void injectDemoData() {
        LiveMatch demo1 = new LiveMatch(99001, "Atl. Nacional", "Millonarios", "Liga BetPlay");
        demo1.setStatus("LIVE");
        demo1.setMinute(34);
        demo1.setHomeGoals(1);
        demo1.setAwayGoals(0);
        Map<String, Double> odds1x2 = new LinkedHashMap<>();
        odds1x2.put("1", 1.55);
        odds1x2.put("X", 4.20);
        odds1x2.put("2", 5.50);
        demo1.getOdds().put("1x2", odds1x2);
        Map<String, Double> oddsOU = new LinkedHashMap<>();
        oddsOU.put("over_2.5", 2.10);
        oddsOU.put("under_2.5", 1.72);
        demo1.getOdds().put("over_under", oddsOU);
        Map<String, Double> oddsBtts = new LinkedHashMap<>();
        oddsBtts.put("yes", 1.85);
        oddsBtts.put("no", 1.95);
        demo1.getOdds().put("btts", oddsBtts);
        demo1.getOddsSource().put("1x2:1", "BetPlay");
        demo1.getOddsSource().put("1x2:X", "Wplay");
        demo1.getOddsSource().put("1x2:2", "Stake");
        demo1.getOddsSource().put("over_under:over_2.5", "BetPlay");
        demo1.getOddsSource().put("over_under:under_2.5", "Wplay");
        demo1.getOddsSource().put("btts:yes", "Stake");
        demo1.getOddsSource().put("btts:no", "BetPlay");
        oddsFetcher.injectMatch(demo1);

        // Demo 2: Minuto 67, score 2-1 (Over 2.5 ya se cumplió = auto-settle WON)
        LiveMatch demo2 = new LiveMatch(99002, "América de Cali", "Dep. Cali", "Liga BetPlay");
        demo2.setStatus("LIVE");
        demo2.setMinute(67);
        demo2.setHomeGoals(2);
        demo2.setAwayGoals(1);
        Map<String, Double> odds2_1x2 = new LinkedHashMap<>();
        odds2_1x2.put("1", 1.30);
        odds2_1x2.put("X", 5.00);
        odds2_1x2.put("2", 9.00);
        demo2.getOdds().put("1x2", odds2_1x2);
        Map<String, Double> odds2OU = new LinkedHashMap<>();
        odds2OU.put("over_2.5", 1.40);
        odds2OU.put("under_2.5", 2.90);
        odds2OU.put("over_3.5", 2.25);
        odds2OU.put("under_3.5", 1.60);
        demo2.getOdds().put("over_under", odds2OU);
        demo2.getOddsSource().put("1x2:1", "Wplay");
        demo2.getOddsSource().put("1x2:X", "BetPlay");
        demo2.getOddsSource().put("1x2:2", "Stake");
        demo2.getOddsSource().put("over_under:over_2.5", "Stake");
        demo2.getOddsSource().put("over_under:under_2.5", "BetPlay");
        demo2.getOddsSource().put("over_under:over_3.5", "BetPlay");
        demo2.getOddsSource().put("over_under:under_3.5", "Wplay");
        oddsFetcher.injectMatch(demo2);

        // Demo 3: Minuto 83, para probar filtro Trampa Minuto 80
        LiveMatch demo3 = new LiveMatch(99003, "Junior", "Santa Fe", "Liga BetPlay");
        demo3.setStatus("LIVE");
        demo3.setMinute(83);
        demo3.setHomeGoals(0);
        demo3.setAwayGoals(0);
        Map<String, Double> odds3_1x2 = new LinkedHashMap<>();
        odds3_1x2.put("1", 2.20);
        odds3_1x2.put("X", 3.30);
        odds3_1x2.put("2", 3.10);
        demo3.getOdds().put("1x2", odds3_1x2);
        Map<String, Double> odds3DC = new LinkedHashMap<>();
        odds3DC.put("1X", 1.28);
        odds3DC.put("12", 1.30);
        odds3DC.put("X2", 1.58);
        demo3.getOdds().put("double_chance", odds3DC);
        demo3.getOddsSource().put("1x2:1", "Stake");
        demo3.getOddsSource().put("1x2:X", "BetPlay");
        demo3.getOddsSource().put("1x2:2", "Wplay");
        demo3.getOddsSource().put("double_chance:1X", "BetPlay");
        demo3.getOddsSource().put("double_chance:12", "Wplay");
        demo3.getOddsSource().put("double_chance:X2", "Stake");
        oddsFetcher.injectMatch(demo3);

        // Demo 4: Tarjeta roja + dangerous attacks altos (partido abierto)
        LiveMatch demo4 = new LiveMatch(99004, "Dep. Pereira", "Once Caldas", "Liga BetPlay");
        demo4.setStatus("LIVE");
        demo4.setMinute(55);
        demo4.setHomeGoals(1);
        demo4.setAwayGoals(1);
        demo4.setRedCardsAway(1);
        demo4.setDangerousAttacks(95);
        demo4.setDangerousAttacksLast10Min(18); // 1.8/min > 1.5 threshold
        Map<String, Double> odds4_1x2 = new LinkedHashMap<>();
        odds4_1x2.put("1", 1.60);
        odds4_1x2.put("X", 3.80);
        odds4_1x2.put("2", 5.50);
        demo4.getOdds().put("1x2", odds4_1x2);
        Map<String, Double> odds4OU = new LinkedHashMap<>();
        odds4OU.put("over_2.5", 1.90);
        odds4OU.put("under_2.5", 1.85);
        demo4.getOdds().put("over_under", odds4OU);
        Map<String, Double> odds4Btts = new LinkedHashMap<>();
        odds4Btts.put("yes", 1.70);
        odds4Btts.put("no", 2.10);
        demo4.getOdds().put("btts", odds4Btts);
        demo4.getOddsSource().put("1x2:1", "BetPlay");
        demo4.getOddsSource().put("1x2:X", "Stake");
        demo4.getOddsSource().put("1x2:2", "Wplay");
        demo4.getOddsSource().put("over_under:over_2.5", "Wplay");
        demo4.getOddsSource().put("over_under:under_2.5", "BetPlay");
        demo4.getOddsSource().put("btts:yes", "Stake");
        demo4.getOddsSource().put("btts:no", "BetPlay");
        oddsFetcher.injectMatch(demo4);

        // Demo 5: Partido con mercado 1X2 SUSPENDIDO (señal fantasma)
        LiveMatch demo5 = new LiveMatch(99005, "Bucaramanga", "Tolima", "Liga BetPlay");
        demo5.setStatus("LIVE");
        demo5.setMinute(71);
        demo5.setHomeGoals(2);
        demo5.setAwayGoals(2);
        Map<String, Double> odds5_1x2 = new LinkedHashMap<>();
        odds5_1x2.put("1", 2.40);
        odds5_1x2.put("X", 3.10);
        odds5_1x2.put("2", 3.20);
        demo5.getOdds().put("1x2", odds5_1x2);
        demo5.getSuspendedMarkets().put("1x2", true); // 1X2 suspendido
        Map<String, Double> odds5OU = new LinkedHashMap<>();
        odds5OU.put("over_4.5", 2.10);
        odds5OU.put("under_4.5", 1.70);
        odds5OU.put("over_3.5", 1.25);
        odds5OU.put("under_3.5", 3.80);
        demo5.getOdds().put("over_under", odds5OU);
        // over_under NO suspendido
        demo5.getOddsSource().put("1x2:1", "Wplay");
        demo5.getOddsSource().put("1x2:X", "BetPlay");
        demo5.getOddsSource().put("1x2:2", "Stake");
        demo5.getOddsSource().put("over_under:over_4.5", "Stake");
        demo5.getOddsSource().put("over_under:under_4.5", "BetPlay");
        demo5.getOddsSource().put("over_under:over_3.5", "Wplay");
        demo5.getOddsSource().put("over_under:under_3.5", "Stake");
        oddsFetcher.injectMatch(demo5);

        evaluate();
    }
}

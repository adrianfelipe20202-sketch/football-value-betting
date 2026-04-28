package com.valuebetting.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en memoria de un partido en vivo o pre-match.
 * No se persiste en PostgreSQL para evitar saturación — vive en caché Caffeine.
 */
public class LiveMatch {

    private long eventId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String status;         // "NOT_STARTED", "LIVE", "HT", "FINISHED"
    private int minute;
    private int homeGoals;
    private int awayGoals;
    private int redCardsHome;
    private int redCardsAway;
    private double xgHome;
    private double xgAway;
    private int dangerousAttacks;
    private int dangerousAttacksLast10Min;
    private Instant lastUpdated;

    // Mercados suspendidos por la casa: "1x2" -> true significa suspendido
    private Map<String, Boolean> suspendedMarkets = new ConcurrentHashMap<>();

    // Cuotas por mercado: "1x2" -> {"1": 1.38, "X": 4.90, "2": 9.00}
    private Map<String, Map<String, Double>> odds = new ConcurrentHashMap<>();

    // Fuente de mejor cuota: "1x2:1" -> "Wplay", "over_under:over_2.5" -> "Bet365"
    private Map<String, String> oddsSource = new ConcurrentHashMap<>();

    public LiveMatch() {}

    public LiveMatch(long eventId, String homeTeam, String awayTeam, String league) {
        this.eventId = eventId;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.league = league;
        this.status = "NOT_STARTED";
        this.minute = 0;
        this.homeGoals = 0;
        this.awayGoals = 0;
        this.redCardsHome = 0;
        this.redCardsAway = 0;
        this.xgHome = 0;
        this.xgAway = 0;
        this.dangerousAttacks = 0;
        this.dangerousAttacksLast10Min = 0;
        this.lastUpdated = Instant.now();
    }

    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public int getHomeGoals() { return homeGoals; }
    public void setHomeGoals(int homeGoals) { this.homeGoals = homeGoals; }

    public int getAwayGoals() { return awayGoals; }
    public void setAwayGoals(int awayGoals) { this.awayGoals = awayGoals; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public int getRedCardsHome() { return redCardsHome; }
    public void setRedCardsHome(int redCardsHome) { this.redCardsHome = redCardsHome; }

    public int getRedCardsAway() { return redCardsAway; }
    public void setRedCardsAway(int redCardsAway) { this.redCardsAway = redCardsAway; }

    public double getXgHome() { return xgHome; }
    public void setXgHome(double xgHome) { this.xgHome = xgHome; }

    public double getXgAway() { return xgAway; }
    public void setXgAway(double xgAway) { this.xgAway = xgAway; }

    public int getDangerousAttacks() { return dangerousAttacks; }
    public void setDangerousAttacks(int dangerousAttacks) { this.dangerousAttacks = dangerousAttacks; }

    public int getDangerousAttacksLast10Min() { return dangerousAttacksLast10Min; }
    public void setDangerousAttacksLast10Min(int dangerousAttacksLast10Min) { this.dangerousAttacksLast10Min = dangerousAttacksLast10Min; }

    public Map<String, Boolean> getSuspendedMarkets() { return suspendedMarkets; }
    public void setSuspendedMarkets(Map<String, Boolean> suspendedMarkets) { this.suspendedMarkets = suspendedMarkets; }

    public boolean isMarketSuspended(String marketKey) {
        return Boolean.TRUE.equals(suspendedMarkets.get(marketKey));
    }

    public double getDangerousAttackRate() {
        if (dangerousAttacksLast10Min > 0) {
            return dangerousAttacksLast10Min / 10.0;
        }
        return minute > 0 ? (double) dangerousAttacks / minute : 0;
    }

    public Map<String, String> getOddsSource() { return oddsSource; }
    public void setOddsSource(Map<String, String> oddsSource) { this.oddsSource = oddsSource; }

    public String getBookmakerFor(String market, String selection) {
        return oddsSource.getOrDefault(market + ":" + selection, "Unknown");
    }

    public Map<String, Map<String, Double>> getOdds() { return odds; }
    public void setOdds(Map<String, Map<String, Double>> odds) { this.odds = odds; }

    public int getTotalGoals() { return homeGoals + awayGoals; }

    public int getGoalDifference() { return homeGoals - awayGoals; }

    public String getMatchLabel() {
        return homeTeam + " vs " + awayTeam;
    }
}

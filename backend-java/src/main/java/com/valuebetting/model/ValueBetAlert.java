package com.valuebetting.model;

import java.time.Instant;

/**
 * Alerta generada cuando el motor detecta una apuesta de valor.
 * Se envía al frontend por WebSocket.
 */
public class ValueBetAlert {

    public enum Rank { ELITE, ALTA, MEDIA, BAJA }

    private String id;
    private long eventId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String status;
    private int minute;
    private String score;

    // Mercado y selección
    private String market;          // "1X2", "Over/Under 2.5", "BTTS"
    private String selection;       // "1", "X", "2", "Over", "Under"
    private String selectionLabel;  // "Atl. Bucaramanga", "Empate", "Más 2.5"

    // Probabilidades y cuotas
    private double modelProbability;    // Del Monte Carlo: 0.65
    private double impliedProbability;  // De la cuota: 1/1.70 = 0.588
    private double bookmakerOdds;       // La cuota real: 1.70
    private double fairOdds;            // Cuota justa: 1/0.65 = 1.538
    private double expectedValue;       // EV: (0.65 * 1.70) - 1 = 0.105
    private double edgePercent;         // Edge: 65% - 58.8% = 6.2%
    private double kellyFraction;       // Kelly óptimo
    private double recommendedStakePct; // Kelly fraccionario (25%)
    private double suggestedStakeCOP;   // Monto sugerido en COP

    // Fuente
    private String bookmakerName;       // Casa de apuestas con mejor cuota
    private boolean oddsStale;          // true si cuotas > 5 min sin actualizar

    // Ranking
    private double confidenceScore;     // 0-100
    private Rank rank;
    private Instant timestamp;
    private Long paperBetId;

    public ValueBetAlert() {
        this.timestamp = Instant.now();
    }

    // --- Getters y Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getSelection() { return selection; }
    public void setSelection(String selection) { this.selection = selection; }

    public String getSelectionLabel() { return selectionLabel; }
    public void setSelectionLabel(String selectionLabel) { this.selectionLabel = selectionLabel; }

    public double getModelProbability() { return modelProbability; }
    public void setModelProbability(double modelProbability) { this.modelProbability = modelProbability; }

    public double getImpliedProbability() { return impliedProbability; }
    public void setImpliedProbability(double impliedProbability) { this.impliedProbability = impliedProbability; }

    public double getBookmakerOdds() { return bookmakerOdds; }
    public void setBookmakerOdds(double bookmakerOdds) { this.bookmakerOdds = bookmakerOdds; }

    public double getFairOdds() { return fairOdds; }
    public void setFairOdds(double fairOdds) { this.fairOdds = fairOdds; }

    public double getExpectedValue() { return expectedValue; }
    public void setExpectedValue(double expectedValue) { this.expectedValue = expectedValue; }

    public double getEdgePercent() { return edgePercent; }
    public void setEdgePercent(double edgePercent) { this.edgePercent = edgePercent; }

    public double getKellyFraction() { return kellyFraction; }
    public void setKellyFraction(double kellyFraction) { this.kellyFraction = kellyFraction; }

    public double getRecommendedStakePct() { return recommendedStakePct; }
    public void setRecommendedStakePct(double recommendedStakePct) { this.recommendedStakePct = recommendedStakePct; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Rank getRank() { return rank; }
    public void setRank(Rank rank) { this.rank = rank; }

    public double getSuggestedStakeCOP() { return suggestedStakeCOP; }
    public void setSuggestedStakeCOP(double suggestedStakeCOP) { this.suggestedStakeCOP = suggestedStakeCOP; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Long getPaperBetId() { return paperBetId; }
    public void setPaperBetId(Long paperBetId) { this.paperBetId = paperBetId; }

    public String getBookmakerName() { return bookmakerName; }
    public void setBookmakerName(String bookmakerName) { this.bookmakerName = bookmakerName; }

    public boolean isOddsStale() { return oddsStale; }
    public void setOddsStale(boolean oddsStale) { this.oddsStale = oddsStale; }
}

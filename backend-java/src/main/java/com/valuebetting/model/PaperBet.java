package com.valuebetting.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "paper_bets")
public class PaperBet {

    public enum Status { PENDING, WON, LOST, VOID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long matchId;
    private String homeTeam;
    private String awayTeam;
    private String league;
    private String market;
    private String selection;
    private String selectionLabel;

    private double oddsAtBet;
    private double impliedProbability;
    private double monteCarloProbability;
    private double expectedValue;
    private double edgePercent;
    private double kellyFraction;
    private double confidenceScore;
    private String rank;

    private double stakeAmount;
    private double stakePercent;
    private double profitLoss;
    private double bankrollBefore;
    private double bankrollAfter;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int matchMinute;
    private String matchScore;

    private Instant placedAt;
    private Instant settledAt;

    public PaperBet() {
        this.status = Status.PENDING;
        this.placedAt = Instant.now();
    }

    // --- Getters y Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getMatchId() { return matchId; }
    public void setMatchId(long matchId) { this.matchId = matchId; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getSelection() { return selection; }
    public void setSelection(String selection) { this.selection = selection; }

    public String getSelectionLabel() { return selectionLabel; }
    public void setSelectionLabel(String selectionLabel) { this.selectionLabel = selectionLabel; }

    public double getOddsAtBet() { return oddsAtBet; }
    public void setOddsAtBet(double oddsAtBet) { this.oddsAtBet = oddsAtBet; }

    public double getImpliedProbability() { return impliedProbability; }
    public void setImpliedProbability(double impliedProbability) { this.impliedProbability = impliedProbability; }

    public double getMonteCarloProbability() { return monteCarloProbability; }
    public void setMonteCarloProbability(double monteCarloProbability) { this.monteCarloProbability = monteCarloProbability; }

    public double getExpectedValue() { return expectedValue; }
    public void setExpectedValue(double expectedValue) { this.expectedValue = expectedValue; }

    public double getEdgePercent() { return edgePercent; }
    public void setEdgePercent(double edgePercent) { this.edgePercent = edgePercent; }

    public double getKellyFraction() { return kellyFraction; }
    public void setKellyFraction(double kellyFraction) { this.kellyFraction = kellyFraction; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public double getStakeAmount() { return stakeAmount; }
    public void setStakeAmount(double stakeAmount) { this.stakeAmount = stakeAmount; }

    public double getStakePercent() { return stakePercent; }
    public void setStakePercent(double stakePercent) { this.stakePercent = stakePercent; }

    public double getProfitLoss() { return profitLoss; }
    public void setProfitLoss(double profitLoss) { this.profitLoss = profitLoss; }

    public double getBankrollBefore() { return bankrollBefore; }
    public void setBankrollBefore(double bankrollBefore) { this.bankrollBefore = bankrollBefore; }

    public double getBankrollAfter() { return bankrollAfter; }
    public void setBankrollAfter(double bankrollAfter) { this.bankrollAfter = bankrollAfter; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getMatchMinute() { return matchMinute; }
    public void setMatchMinute(int matchMinute) { this.matchMinute = matchMinute; }

    public String getMatchScore() { return matchScore; }
    public void setMatchScore(String matchScore) { this.matchScore = matchScore; }

    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}

package com.valuebetting.model;

import java.util.Map;

/**
 * Resultado del modelo de Monte Carlo para un partido.
 * Viene del motor Python existente o se calcula localmente.
 */
public class MonteCarloResult {

    private long eventId;
    private String homeTeam;
    private String awayTeam;

    // Probabilidades por mercado: "1x2" -> {"1": 0.65, "X": 0.20, "2": 0.15}
    private Map<String, Map<String, Double>> probabilities;

    private double expectedHomeGoals;
    private double expectedAwayGoals;
    private int simulations;

    public MonteCarloResult() {}

    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public Map<String, Map<String, Double>> getProbabilities() { return probabilities; }
    public void setProbabilities(Map<String, Map<String, Double>> probabilities) { this.probabilities = probabilities; }

    public double getExpectedHomeGoals() { return expectedHomeGoals; }
    public void setExpectedHomeGoals(double expectedHomeGoals) { this.expectedHomeGoals = expectedHomeGoals; }

    public double getExpectedAwayGoals() { return expectedAwayGoals; }
    public void setExpectedAwayGoals(double expectedAwayGoals) { this.expectedAwayGoals = expectedAwayGoals; }

    public int getSimulations() { return simulations; }
    public void setSimulations(int simulations) { this.simulations = simulations; }
}

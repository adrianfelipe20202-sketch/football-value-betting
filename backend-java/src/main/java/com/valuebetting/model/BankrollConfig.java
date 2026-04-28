package com.valuebetting.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bankroll_config")
public class BankrollConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double initialBankroll;
    private Instant createdAt;
    private Instant updatedAt;

    public BankrollConfig() {}

    public BankrollConfig(double initialBankroll) {
        this.initialBankroll = initialBankroll;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public double getInitialBankroll() { return initialBankroll; }
    public void setInitialBankroll(double initialBankroll) { this.initialBankroll = initialBankroll; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

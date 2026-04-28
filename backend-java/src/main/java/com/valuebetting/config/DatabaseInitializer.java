package com.valuebetting.config;

import com.valuebetting.model.BankrollConfig;
import com.valuebetting.repository.BankrollConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final BankrollConfigRepository bankrollConfigRepo;
    private final DataSource dataSource;

    @Value("${bankroll.initial:1000000}")
    private double initialBankroll;

    public DatabaseInitializer(BankrollConfigRepository bankrollConfigRepo, DataSource dataSource) {
        this.bankrollConfigRepo = bankrollConfigRepo;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        verifyConnection();
        initializeBankroll();
    }

    private void verifyConnection() {
        try (Connection conn = dataSource.getConnection()) {
            var meta = conn.getMetaData();
            log.info("========================================");
            log.info("DB CONNECTION OK");
            log.info("  Database: {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
            log.info("  URL: {}", meta.getURL());
            log.info("  User: {}", meta.getUserName());
            log.info("========================================");
        } catch (Exception e) {
            log.error("DB CONNECTION FAILED: {}", e.getMessage());
            throw new RuntimeException("Cannot connect to database", e);
        }
    }

    private void initializeBankroll() {
        if (bankrollConfigRepo.count() == 0) {
            BankrollConfig config = new BankrollConfig(initialBankroll);
            bankrollConfigRepo.save(config);
            log.info("Bankroll initialized: ${} COP (first run)", (long) initialBankroll);
        } else {
            BankrollConfig existing = bankrollConfigRepo.findAll().getFirst();
            log.info("Bankroll loaded from DB: ${} COP (existing data respected)", (long) existing.getInitialBankroll());
        }
    }
}

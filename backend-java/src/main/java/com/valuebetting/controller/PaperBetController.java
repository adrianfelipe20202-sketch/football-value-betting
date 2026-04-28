package com.valuebetting.controller;

import com.valuebetting.model.PaperBet;
import com.valuebetting.repository.PaperBetRepository;
import com.valuebetting.service.BankrollService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paper")
public class PaperBetController {

    private final PaperBetRepository paperBetRepo;
    private final BankrollService bankrollService;

    public PaperBetController(PaperBetRepository paperBetRepo, BankrollService bankrollService) {
        this.paperBetRepo = paperBetRepo;
        this.bankrollService = bankrollService;
    }

    @GetMapping("/bets")
    public List<PaperBet> getAllBets() {
        return paperBetRepo.findAllByOrderByPlacedAtDesc();
    }

    @GetMapping("/bets/pending")
    public List<PaperBet> getPendingBets() {
        return paperBetRepo.findByStatusOrderByPlacedAtDesc(PaperBet.Status.PENDING);
    }

    @GetMapping("/stats")
    public Map<String, Object> getPerformanceStats() {
        return bankrollService.getPerformanceStats();
    }

    @GetMapping("/bankroll/history")
    public List<Map<String, Object>> getBankrollHistory() {
        return bankrollService.getBankrollHistory();
    }

    @GetMapping("/bankroll/current")
    public Map<String, Object> getCurrentBankroll() {
        return Map.of(
                "bankroll", bankrollService.getCurrentBankroll(),
                "initial", bankrollService.getInitialBankroll()
        );
    }

    @PostMapping("/bets/{id}/settle")
    public ResponseEntity<Map<String, String>> settleBet(
            @PathVariable Long id,
            @RequestParam boolean won) {
        bankrollService.settleBet(id, won);
        return ResponseEntity.ok(Map.of("status", "settled", "result", won ? "WON" : "LOST"));
    }

    @PostMapping("/bets/{id}/void")
    public ResponseEntity<Map<String, String>> voidBet(@PathVariable Long id) {
        bankrollService.voidBet(id);
        return ResponseEntity.ok(Map.of("status", "voided"));
    }

    @PostMapping("/correct-misclassified")
    public ResponseEntity<Map<String, Object>> correctMisclassified() {
        int corrected = bankrollService.correctMisclassifiedBets();
        return ResponseEntity.ok(Map.of(
                "status", "done",
                "corrected", corrected
        ));
    }
}

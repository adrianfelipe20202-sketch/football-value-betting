package com.valuebetting.controller;

import com.valuebetting.model.LiveMatch;
import com.valuebetting.model.ValueBetAlert;
import com.valuebetting.service.OddsFetcherService;
import com.valuebetting.service.ValueBetAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final ValueBetAgent valueBetAgent;
    private final OddsFetcherService oddsFetcher;

    public AlertController(ValueBetAgent valueBetAgent, OddsFetcherService oddsFetcher) {
        this.valueBetAgent = valueBetAgent;
        this.oddsFetcher = oddsFetcher;
    }

    @GetMapping("/alerts")
    public Collection<ValueBetAlert> getActiveAlerts() {
        return valueBetAgent.getActiveAlerts().values();
    }

    @GetMapping("/alerts/history")
    public List<ValueBetAlert> getAlertHistory() {
        return valueBetAgent.getAlertHistory();
    }

    @GetMapping("/alerts/rank/{rank}")
    public List<ValueBetAlert> getAlertsByRank(@PathVariable String rank) {
        ValueBetAlert.Rank r = ValueBetAlert.Rank.valueOf(rank.toUpperCase());
        return valueBetAgent.getAlertsByRank(r);
    }

    @GetMapping("/matches/live")
    public Collection<LiveMatch> getLiveMatches() {
        return oddsFetcher.getAllLiveMatches().values();
    }

    @GetMapping("/matches/live/count")
    public Map<String, Integer> getLiveCount() {
        return Map.of("count", oddsFetcher.getLiveCount());
    }

    @PostMapping("/demo/inject")
    public ResponseEntity<Map<String, String>> injectDemo() {
        valueBetAgent.injectDemoData();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Demo data injected, evaluation triggered"
        ));
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("liveMatches", oddsFetcher.getLiveCount());
        status.put("activeAlerts", valueBetAgent.getActiveAlerts().size());
        status.put("historySize", valueBetAgent.getAlertHistory().size());
        status.put("rateLimited", oddsFetcher.isRateLimited());
        status.put("rateLimitSecondsRemaining", oddsFetcher.getRateLimitSecondsRemaining());
        status.put("apiConfigured", oddsFetcher.isConfigured());
        status.put("requestsUsed", oddsFetcher.getDailyRequestsUsed());
        status.put("requestsLimit", oddsFetcher.getDailyRequestLimit());
        return status;
    }
}

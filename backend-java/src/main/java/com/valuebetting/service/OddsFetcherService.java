package com.valuebetting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valuebetting.model.LiveMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OddsFetcherService {

    private static final Logger log = LoggerFactory.getLogger(OddsFetcherService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<Long, LiveMatch> liveMatchCache = new ConcurrentHashMap<>();

    // Budget tracking
    private final AtomicInteger dailyRequestCount = new AtomicInteger(0);
    private volatile LocalDate requestCountDate = LocalDate.now();

    // Smart polling state
    private volatile boolean hasLiveMatches = false;
    private volatile Instant lastFixtureFetch = Instant.EPOCH;
    private volatile Instant lastOddsFetch = Instant.EPOCH;
    private volatile Instant lastEnrichmentFetch = Instant.EPOCH;
    private final Map<Long, Instant> oddsLastFetchedAt = new ConcurrentHashMap<>();
    private final Map<Long, Instant> oddsLastUpdated = new ConcurrentHashMap<>();
    private int enrichmentIndex = 0;
    private int oddsRoundRobinIndex = 0;

    // Whitelist: solo casas accesibles en Colombia
    private static final Map<String, String> BOOKMAKER_WHITELIST = new LinkedHashMap<>();
    static {
        BOOKMAKER_WHITELIST.put("unibet", "BetPlay");
        BOOKMAKER_WHITELIST.put("betplay", "BetPlay");
        BOOKMAKER_WHITELIST.put("kambi", "BetPlay");
        BOOKMAKER_WHITELIST.put("betcris", "Wplay");
        BOOKMAKER_WHITELIST.put("betsson", "Wplay");
        BOOKMAKER_WHITELIST.put("wplay", "Wplay");
        BOOKMAKER_WHITELIST.put("betano", "Stake");
        BOOKMAKER_WHITELIST.put("stake", "Stake");
        BOOKMAKER_WHITELIST.put("stake.com", "Stake");
    }

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    @Value("${api.football.key:}")
    private String apiKey;

    @Value("${api.football.base-url:https://v3.football.api-sports.io}")
    private String baseUrl;

    @Value("${api.football.daily-limit:100}")
    private int dailyLimit;

    private static final Duration DISCOVERY_INTERVAL = Duration.ofMinutes(15);
    private static final Duration ENRICHMENT_INTERVAL = Duration.ofMinutes(10);

    public OddsFetcherService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // =====================================================
    // SMART POLL: scheduler checks every 30s, acts on intervals
    // =====================================================

    @Scheduled(fixedDelay = 30_000, initialDelay = 5_000)
    public void smartPoll() {
        resetDailyCountIfNeeded();

        if (apiKey == null || apiKey.isBlank() || "YOUR_KEY_HERE".equals(apiKey)) {
            return;
        }

        int remaining = getRemainingRequests();
        if (remaining <= 2) {
            log.warn("[API-Football] Budget exhausted ({}/{}). Waiting for midnight reset.",
                    dailyRequestCount.get(), dailyLimit);
            return;
        }

        Instant now = Instant.now();

        // Phase 1: fixture discovery/refresh
        Duration fixtureInterval = hasLiveMatches ? getDynamicFixtureInterval() : DISCOVERY_INTERVAL;
        if (Duration.between(lastFixtureFetch, now).compareTo(fixtureInterval) >= 0) {
            fetchLiveFixtures();
            lastFixtureFetch = now;
        }

        if (!hasLiveMatches) return;

        // Phase 2: odds (round-robin, Colombian whitelist only)
        Duration oddsInterval = getDynamicOddsInterval();
        if (remaining > 10 && Duration.between(lastOddsFetch, now).compareTo(oddsInterval) >= 0) {
            fetchOddsForLiveMatches();
            lastOddsFetch = now;
        }

        // Phase 3: enrichment (red cards, dangerous attacks, xG) — round-robin
        if (remaining > 20 && Duration.between(lastEnrichmentFetch, now).compareTo(ENRICHMENT_INTERVAL) >= 0) {
            enrichNextMatch();
            lastEnrichmentFetch = now;
        }

        // Phase 4: memory cleanup — evict finished matches after 10 min
        evictFinishedMatches();
    }

    private void evictFinishedMatches() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        int before = liveMatchCache.size();
        liveMatchCache.entrySet().removeIf(e -> {
            LiveMatch m = e.getValue();
            if (isDemoMatch(e.getKey())) return false;
            boolean finished = "FINISHED".equals(m.getStatus());
            boolean stale = m.getLastUpdated() != null && m.getLastUpdated().isBefore(cutoff);
            return finished && stale;
        });
        int evicted = before - liveMatchCache.size();
        if (evicted > 0) {
            oddsLastUpdated.keySet().removeIf(id -> !liveMatchCache.containsKey(id));
            oddsLastFetchedAt.keySet().removeIf(id -> !liveMatchCache.containsKey(id));
            log.info("Memory cleanup: evicted {} finished matches, {} remain in cache", evicted, liveMatchCache.size());
        }
    }

    private Duration getDynamicFixtureInterval() {
        int remaining = getRemainingRequests();
        if (remaining > 70) return Duration.ofSeconds(120);
        if (remaining > 50) return Duration.ofMinutes(3);
        if (remaining > 30) return Duration.ofMinutes(5);
        return Duration.ofMinutes(10);
    }

    // =====================================================
    // PHASE 1: Live fixtures discovery
    // =====================================================

    private void fetchLiveFixtures() {
        JsonNode data = apiGet("/fixtures", Map.of("live", "all"));
        if (data == null || !data.isArray()) {
            hasLiveMatches = false;
            return;
        }

        Set<Long> currentLiveIds = new HashSet<>();

        for (JsonNode item : data) {
            try {
                LiveMatch match = parseFixture(item);
                if (match == null) continue;

                currentLiveIds.add(match.getEventId());
                LiveMatch existing = liveMatchCache.get(match.getEventId());

                if (existing != null) {
                    preserveEnrichmentData(match, existing);
                }

                filterFulfilledAndSuspiciousOdds(match);
                liveMatchCache.put(match.getEventId(), match);
                messagingTemplate.convertAndSend("/topic/live-matches", match);
            } catch (Exception e) {
                log.debug("Error parsing fixture: {}", e.getMessage());
            }
        }

        liveMatchCache.entrySet().removeIf(e ->
                !isDemoMatch(e.getKey()) && !currentLiveIds.contains(e.getKey()));

        hasLiveMatches = !currentLiveIds.isEmpty();
        log.info("[API-Football] {} live matches | Budget: {}/{} used",
                currentLiveIds.size(), dailyRequestCount.get(), dailyLimit);

        for (LiveMatch m : liveMatchCache.values()) {
            if (!isDemoMatch(m.getEventId())) {
                log.info("  {} | {} {} min {} | {}-{}",
                        m.getLeague(), m.getMatchLabel(), m.getStatus(),
                        m.getMinute(), m.getHomeGoals(), m.getAwayGoals());
            }
        }
    }

    private LiveMatch parseFixture(JsonNode item) {
        JsonNode fixture = item.path("fixture");
        JsonNode league = item.path("league");
        JsonNode teams = item.path("teams");
        JsonNode goals = item.path("goals");

        long fixtureId = fixture.path("id").asLong(0);
        if (fixtureId == 0) return null;

        String homeTeam = teams.path("home").path("name").asText("");
        String awayTeam = teams.path("away").path("name").asText("");
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) return null;

        String leagueName = league.path("name").asText("Unknown");
        String country = league.path("country").asText("");
        if (!country.isEmpty()) leagueName = leagueName + " (" + country + ")";

        LiveMatch match = new LiveMatch(fixtureId, homeTeam, awayTeam, leagueName);

        String shortStatus = fixture.path("status").path("short").asText("NS");
        int elapsed = fixture.path("status").path("elapsed").asInt(0);
        match.setStatus(mapStatus(shortStatus));
        match.setMinute(elapsed);

        match.setHomeGoals(goals.path("home").asInt(0));
        match.setAwayGoals(goals.path("away").asInt(0));
        match.setLastUpdated(Instant.now());

        return match;
    }

    private String mapStatus(String shortStatus) {
        return switch (shortStatus) {
            case "1H", "2H", "ET", "LIVE", "HT", "BT" -> "LIVE";
            case "FT", "AET", "PEN" -> "FINISHED";
            case "NS" -> "NOT_STARTED";
            default -> shortStatus;
        };
    }

    private void filterFulfilledAndSuspiciousOdds(LiveMatch match) {
        int totalGoals = match.getHomeGoals() + match.getAwayGoals();
        boolean bothScored = match.getHomeGoals() > 0 && match.getAwayGoals() > 0;
        int minute = match.getMinute();
        double maxOdds = getMaxLiveOdds(minute);

        Map<String, Map<String, Double>> odds = match.getOdds();

        // Remove over_under selections already fulfilled + apply progressive cap
        Map<String, Double> ouMarket = odds.get("over_under");
        if (ouMarket != null) {
            ouMarket.entrySet().removeIf(e -> {
                String sel = e.getKey();
                double odd = e.getValue();
                if (sel.startsWith("over_")) {
                    double line = parseDoubleSafe(sel.replace("over_", ""));
                    if (totalGoals > line) return true;
                }
                if (sel.startsWith("under_")) {
                    double line = parseDoubleSafe(sel.replace("under_", ""));
                    if (totalGoals > line) return true;
                }
                return odd > maxOdds;
            });
            if (ouMarket.isEmpty()) odds.remove("over_under");
        }

        // Remove BTTS if already determined
        Map<String, Double> bttsMarket = odds.get("btts");
        if (bttsMarket != null) {
            if (bothScored) {
                bttsMarket.remove("yes");
                bttsMarket.remove("no");
            }
            bttsMarket.entrySet().removeIf(e -> e.getValue() > maxOdds);
            if (bttsMarket.isEmpty()) odds.remove("btts");
        }

        // Progressive cap for 1x2 and double_chance
        for (String mkt : List.of("1x2", "double_chance")) {
            Map<String, Double> market = odds.get(mkt);
            if (market != null) {
                market.entrySet().removeIf(e -> e.getValue() > maxOdds);
                if (market.isEmpty()) odds.remove(mkt);
            }
        }
    }

    private double getMaxLiveOdds(int minute) {
        if (minute >= 75) return 4.0;
        if (minute >= 45) return 5.0;
        if (minute >= 20) return 6.0;
        return 8.0;
    }

    private void preserveEnrichmentData(LiveMatch fresh, LiveMatch existing) {
        fresh.setOdds(existing.getOdds());
        fresh.setSuspendedMarkets(existing.getSuspendedMarkets());
        fresh.setOddsSource(existing.getOddsSource());
        if (fresh.getRedCardsHome() == 0) fresh.setRedCardsHome(existing.getRedCardsHome());
        if (fresh.getRedCardsAway() == 0) fresh.setRedCardsAway(existing.getRedCardsAway());
        if (fresh.getDangerousAttacks() == 0) fresh.setDangerousAttacks(existing.getDangerousAttacks());
        if (fresh.getXgHome() == 0) fresh.setXgHome(existing.getXgHome());
        if (fresh.getXgAway() == 0) fresh.setXgAway(existing.getXgAway());
    }

    // =====================================================
    // PHASE 2: Multi-bookmaker odds (best price across all)
    // =====================================================

    private void fetchOddsForLiveMatches() {
        List<LiveMatch> liveMatches = liveMatchCache.values().stream()
                .filter(m -> !isDemoMatch(m.getEventId()))
                .filter(m -> "LIVE".equals(m.getStatus()))
                .toList();

        if (liveMatches.isEmpty()) return;
        if (getRemainingRequests() <= 10) {
            log.info("[ODDS] Budget guard ({} remaining)", getRemainingRequests());
            return;
        }

        LiveMatch target = liveMatches.get(oddsRoundRobinIndex % liveMatches.size());
        oddsRoundRobinIndex++;

        log.info("[ODDS] Round-robin {}/{} → {}",
                oddsRoundRobinIndex % liveMatches.size() + 1, liveMatches.size(), target.getMatchLabel());

        fetchOddsForFixture(target);
        oddsLastFetchedAt.put(target.getEventId(), Instant.now());
    }

    private Duration getDynamicOddsInterval() {
        int remaining = getRemainingRequests();
        if (remaining > 70) return Duration.ofSeconds(90);
        if (remaining > 50) return Duration.ofSeconds(120);
        if (remaining > 30) return Duration.ofSeconds(180);
        return Duration.ofSeconds(300);
    }

    private void fetchOddsForFixture(LiveMatch match) {
        JsonNode data = apiGet("/odds", Map.of(
                "fixture", String.valueOf(match.getEventId())
        ));

        if (data == null || !data.isArray() || data.isEmpty()) {
            log.debug("[ODDS] No odds available for {}", match.getMatchLabel());
            return;
        }

        parseMultiBookmakerOdds(match, data.get(0));
        filterFulfilledAndSuspiciousOdds(match);
    }

    private void parseMultiBookmakerOdds(LiveMatch match, JsonNode oddsData) {
        JsonNode bookmakers = oddsData.path("bookmakers");
        if (!bookmakers.isArray() || bookmakers.isEmpty()) return;

        Map<String, Map<String, Double>> bestOdds = new ConcurrentHashMap<>();
        Map<String, String> bestSource = new ConcurrentHashMap<>();
        Set<String> acceptedNames = new LinkedHashSet<>();
        Set<String> skippedNames = new LinkedHashSet<>();

        for (JsonNode bm : bookmakers) {
            String rawName = bm.path("name").asText("Unknown");
            String displayName = resolveBookmaker(rawName);
            if (displayName == null) {
                skippedNames.add(rawName);
                continue;
            }
            acceptedNames.add(displayName + " (" + rawName + ")");

            JsonNode bets = bm.path("bets");
            if (!bets.isArray()) continue;

            for (JsonNode bet : bets) {
                String betName = bet.path("name").asText("");
                String marketKey = mapApiMarketToKey(betName);
                if (marketKey == null) continue;

                JsonNode values = bet.path("values");
                if (!values.isArray()) continue;

                for (JsonNode val : values) {
                    String valueName = val.path("value").asText("");
                    String selectionKey = mapApiSelectionToKey(marketKey, valueName);
                    if (selectionKey == null) continue;

                    double odd = parseDoubleSafe(val.path("odd").asText("0"));
                    if (odd <= 1.0) continue;

                    boolean suspended = val.path("suspended").asBoolean(false);
                    if (suspended) {
                        match.getSuspendedMarkets().put(marketKey, true);
                        continue;
                    }

                    bestOdds.computeIfAbsent(marketKey, k -> new ConcurrentHashMap<>());
                    Double currentBest = bestOdds.get(marketKey).get(selectionKey);
                    if (currentBest == null || odd > currentBest) {
                        bestOdds.get(marketKey).put(selectionKey, odd);
                        bestSource.put(marketKey + ":" + selectionKey, displayName);
                    }
                }
            }
        }

        if (!bestOdds.isEmpty()) {
            match.setOdds(bestOdds);
            match.setOddsSource(bestSource);
            oddsLastUpdated.put(match.getEventId(), Instant.now());

            log.info("[ODDS] {} | {} markets from {} | Skipped: {}",
                    match.getMatchLabel(), bestOdds.size(), acceptedNames, skippedNames);

            for (var entry : bestOdds.entrySet()) {
                String market = entry.getKey();
                for (var sel : entry.getValue().entrySet()) {
                    String source = bestSource.getOrDefault(market + ":" + sel.getKey(), "?");
                    log.info("  {} {} @ {} ({})", market, sel.getKey(),
                            String.format("%.2f", sel.getValue()), source);
                }
            }
        } else {
            log.info("[ODDS] {} | No whitelisted bookmaker data. Accepted: {} Skipped: {}",
                    match.getMatchLabel(), acceptedNames, skippedNames);
        }
    }

    private String resolveBookmaker(String rawName) {
        if (rawName == null) return null;
        return BOOKMAKER_WHITELIST.get(rawName.toLowerCase().trim());
    }

    // =====================================================
    // PHASE 3: Enrichment (events + statistics) — round-robin
    // =====================================================

    private void enrichNextMatch() {
        List<LiveMatch> liveMatches = liveMatchCache.values().stream()
                .filter(m -> !isDemoMatch(m.getEventId()) && "LIVE".equals(m.getStatus()))
                .toList();

        if (liveMatches.isEmpty()) return;

        LiveMatch target = liveMatches.get(enrichmentIndex % liveMatches.size());
        enrichmentIndex++;

        log.info("[ENRICH] Enriching {} (round-robin {}/{})",
                target.getMatchLabel(), (enrichmentIndex - 1) % liveMatches.size() + 1, liveMatches.size());

        if (getRemainingRequests() > 5) {
            fetchMatchEvents(target);
        }
        if (getRemainingRequests() > 5) {
            fetchMatchStatistics(target);
        }
    }

    private void fetchMatchEvents(LiveMatch match) {
        JsonNode data = apiGet("/fixtures/events",
                Map.of("fixture", String.valueOf(match.getEventId())));
        if (data == null || !data.isArray()) return;

        int redHome = 0, redAway = 0;
        String homeTeam = match.getHomeTeam();

        for (JsonNode event : data) {
            String type = event.path("type").asText("");
            String detail = event.path("detail").asText("");
            String teamName = event.path("team").path("name").asText("");

            if ("Card".equals(type) && detail.toLowerCase().contains("red")) {
                if (fuzzyMatch(teamName, homeTeam)) redHome++;
                else redAway++;
            }
        }

        match.setRedCardsHome(redHome);
        match.setRedCardsAway(redAway);

        if (redHome > 0 || redAway > 0) {
            log.info("[EVENTS] {} | Red cards: Home={} Away={}", match.getMatchLabel(), redHome, redAway);
        }
    }

    private void fetchMatchStatistics(LiveMatch match) {
        JsonNode data = apiGet("/fixtures/statistics",
                Map.of("fixture", String.valueOf(match.getEventId())));
        if (data == null || !data.isArray()) return;

        int totalDangerousAttacks = 0;

        for (JsonNode teamStats : data) {
            String teamName = teamStats.path("team").path("name").asText("");
            boolean isHome = fuzzyMatch(teamName, match.getHomeTeam());
            JsonNode stats = teamStats.path("statistics");
            if (!stats.isArray()) continue;

            for (JsonNode stat : stats) {
                String statType = stat.path("type").asText("");
                String valStr = Objects.toString(stat.path("value").asText(null), "0");
                if ("null".equals(valStr)) valStr = "0";

                switch (statType) {
                    case "Dangerous Attacks" -> totalDangerousAttacks += parseIntSafe(valStr);
                    case "expected_goals" -> {
                        double val = parseDoubleSafe(valStr);
                        if (isHome) match.setXgHome(val);
                        else match.setXgAway(val);
                    }
                }
            }
        }

        match.setDangerousAttacks(totalDangerousAttacks);

        if (totalDangerousAttacks > 0 || match.getXgHome() > 0) {
            log.info("[STATS] {} | DA={} xG={}-{}",
                    match.getMatchLabel(), totalDangerousAttacks,
                    String.format("%.2f", match.getXgHome()),
                    String.format("%.2f", match.getXgAway()));
        }
    }

    // =====================================================
    // Market & selection mapping (API-Football -> our format)
    // =====================================================

    private String mapApiMarketToKey(String betName) {
        if (betName == null) return null;
        String lower = betName.toLowerCase();
        if (lower.equals("match winner") || lower.contains("1x2")
                || lower.contains("full time result") || lower.equals("fulltime result")) return "1x2";
        if (lower.contains("goals over/under") || lower.contains("over/under")
                || lower.contains("total goals") || lower.equals("goals")
                || lower.equals("match goals")) return "over_under";
        if (lower.contains("both teams score") || lower.contains("btts")
                || lower.equals("both teams to score")) return "btts";
        if (lower.contains("double chance")) return "double_chance";
        return null;
    }

    private String mapApiSelectionToKey(String market, String value) {
        if (value == null) return null;
        String lower = value.toLowerCase().trim();
        return switch (market) {
            case "1x2" -> {
                if (lower.equals("home") || lower.equals("1")) yield "1";
                if (lower.equals("draw") || lower.equals("x")) yield "X";
                if (lower.equals("away") || lower.equals("2")) yield "2";
                yield null;
            }
            case "over_under" -> {
                if (lower.startsWith("over")) yield "over_" + extractNumber(lower);
                if (lower.startsWith("under")) yield "under_" + extractNumber(lower);
                yield null;
            }
            case "btts" -> {
                if (lower.equals("yes")) yield "yes";
                if (lower.equals("no")) yield "no";
                yield null;
            }
            case "double_chance" -> {
                if (lower.contains("home/draw") || lower.equals("1x") || lower.contains("home or draw")) yield "1X";
                if (lower.contains("home/away") || lower.equals("12") || lower.contains("home or away")) yield "12";
                if (lower.contains("draw/away") || lower.equals("x2") || lower.contains("away or draw")) yield "X2";
                yield null;
            }
            default -> null;
        };
    }

    private String extractNumber(String s) {
        var matcher = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)").matcher(s);
        return matcher.find() ? matcher.group(1) : "2.5";
    }

    private boolean fuzzyMatch(String apiName, String ourName) {
        if (apiName == null || ourName == null) return false;
        return apiName.equalsIgnoreCase(ourName)
                || apiName.toLowerCase().contains(ourName.toLowerCase())
                || ourName.toLowerCase().contains(apiName.toLowerCase());
    }

    // =====================================================
    // HTTP client for API-Football
    // =====================================================

    private JsonNode apiGet(String endpoint, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(endpoint);
        if (!params.isEmpty()) {
            url.append("?");
            params.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
            url.setLength(url.length() - 1);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("x-apisports-key", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            dailyRequestCount.incrementAndGet();

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());

                JsonNode errors = root.path("errors");
                if (errors.isObject() && errors.size() > 0) {
                    log.warn("[API-Football] API error on {}: {}", endpoint, errors);
                    return null;
                }

                int results = root.path("results").asInt(0);
                log.debug("[API-Football] {} -> {} results | Budget: {}/{}",
                        endpoint, results, dailyRequestCount.get(), dailyLimit);

                return root.path("response");
            }

            if (response.statusCode() == 429) {
                log.warn("[API-Football] 429 Rate limited on {} — backing off", endpoint);
                return null;
            }

            log.warn("[API-Football] HTTP {} on {}", response.statusCode(), endpoint);
            return null;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("[API-Football] Request error on {}: {}", endpoint, e.getMessage());
            return null;
        }
    }

    // =====================================================
    // Utility
    // =====================================================

    private void resetDailyCountIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(requestCountDate)) {
            int previousCount = dailyRequestCount.getAndSet(0);
            requestCountDate = today;
            log.info("[API-Football] Daily budget reset (yesterday used: {}/{})", previousCount, dailyLimit);
        }
    }

    private int getRemainingRequests() {
        return Math.max(0, dailyLimit - dailyRequestCount.get());
    }

    private boolean isDemoMatch(long eventId) {
        return eventId >= 99000 && eventId <= 99999;
    }

    private int parseIntSafe(String s) {
        try { return (int) Double.parseDouble(s.replace("%", "")); }
        catch (Exception e) { return 0; }
    }

    private double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.replace("%", "")); }
        catch (Exception e) { return 0; }
    }

    // =====================================================
    // Public API (contract preserved for ValueBetAgent/BankrollService)
    // =====================================================

    public Map<Long, LiveMatch> getAllLiveMatches() {
        return Collections.unmodifiableMap(liveMatchCache);
    }

    public Optional<LiveMatch> getMatch(long eventId) {
        return Optional.ofNullable(liveMatchCache.get(eventId));
    }

    public int getLiveCount() {
        return liveMatchCache.size();
    }

    public boolean isRateLimited() {
        return getRemainingRequests() <= 2;
    }

    public long getRateLimitSecondsRemaining() {
        if (!isRateLimited()) return 0;
        return 86400 - (System.currentTimeMillis() % 86400000) / 1000;
    }

    public void injectMatch(LiveMatch match) {
        match.setLastUpdated(Instant.now());
        liveMatchCache.put(match.getEventId(), match);
        oddsLastUpdated.put(match.getEventId(), Instant.now());
    }

    public int getDailyRequestsUsed() {
        return dailyRequestCount.get();
    }

    public int getDailyRequestLimit() {
        return dailyLimit;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"YOUR_KEY_HERE".equals(apiKey);
    }

    public boolean isOddsStale(long fixtureId) {
        Instant lastUpdate = oddsLastUpdated.get(fixtureId);
        if (lastUpdate == null) return true;
        return Duration.between(lastUpdate, Instant.now()).compareTo(STALE_THRESHOLD) > 0;
    }
}

package com.valuebetting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class KeepAlive {

    private static final Logger log = LoggerFactory.getLogger(KeepAlive.class);

    @Value("${RENDER_EXTERNAL_URL:}")
    private String renderUrl;

    private final HttpClient client = HttpClient.newHttpClient();

    @Scheduled(fixedRate = 600_000, initialDelay = 300_000)
    public void ping() {
        if (renderUrl == null || renderUrl.isBlank()) return;
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(renderUrl + "/api/status"))
                    .GET().build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-alive ping OK");
        } catch (Exception ignored) {}
    }
}

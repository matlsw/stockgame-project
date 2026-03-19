package com.stockgame.service;

import com.stockgame.dto.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lädt alle populären Aktien beim Start in den Cache
 * und aktualisiert sie alle 55 Sekunden automatisch.
 */
@Service
public class StockPreloadService {

    private static final Logger log = LoggerFactory.getLogger(StockPreloadService.class);

    private static final List<String> POPULAR_SYMBOLS = List.of(
            "AAPL", "TSLA", "MSFT", "GOOGL", "NVDA",
            "META", "AMZN", "NFLX", "AMD", "JPM"
    );

    // Delay zwischen Requests in ms (Finnhub: 60 req/min → 1000ms sicher)
    private static final long REQUEST_DELAY_MS = 1100;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockCacheService cacheService;

    /**
     * Startet nach App-Start im Hintergrund — Cache vorwärmen.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void preloadOnStartup() {
        log.info("Cache-Preload gestartet für {} Symbole", POPULAR_SYMBOLS.size());
        loadAll();
        log.info("Cache-Preload abgeschlossen. {} Quotes gecacht.", cacheService.cachedQuoteCount());
    }

    /**
     * Automatische Cache-Auffrischung alle 55 Sekunden.
     * Läuft nur wenn mindestens 1 Quote im Cache ist (= App wurde bereits verwendet).
     */
    @Scheduled(fixedDelay = 55_000, initialDelay = 70_000)
    public void refreshCache() {
        if (cacheService.cachedQuoteCount() == 0) return;
        log.info("Automatische Cache-Auffrischung gestartet");
        loadAll();
        log.info("Cache-Auffrischung abgeschlossen");
    }

    private void loadAll() {
        for (String symbol : POPULAR_SYMBOLS) {
            try {
                StockQuote quote = stockService.getQuote(symbol);
                cacheService.putQuote(symbol, quote);
                log.debug("Gecacht: {} @ ${}", symbol, quote.getPrice());
            } catch (Exception e) {
                log.warn("Fehler beim Laden von {}: {}", symbol, e.getMessage());
            }

            try {
                Thread.sleep(REQUEST_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
package com.stockgame.service;

import com.stockgame.dto.StockQuote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class StockCacheService {

    @Autowired
    private StockService stockService;

    // Cache: symbol → (quote, timestamp)
    private final Map<String, CachedQuote> cache = new HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 Minuten

    public StockQuote getQuote(String symbol) {
        CachedQuote cached = cache.get(symbol.toUpperCase());
        if (cached != null && !cached.isExpired()) {
            return cached.quote;
        }
        StockQuote fresh = stockService.getQuote(symbol);
        cache.put(symbol.toUpperCase(), new CachedQuote(fresh));
        return fresh;
    }

    public void invalidate(String symbol) {
        cache.remove(symbol.toUpperCase());
    }

    public boolean isCached(String symbol) {
        CachedQuote c = cache.get(symbol.toUpperCase());
        return c != null && !c.isExpired();
    }

    private static class CachedQuote {
        final StockQuote quote;
        final long timestamp;

        CachedQuote(StockQuote quote) {
            this.quote = quote;
            this.timestamp = Instant.now().toEpochMilli();
        }

        boolean isExpired() {
            return Instant.now().toEpochMilli() - timestamp > CACHE_TTL_MS;
        }
    }
}

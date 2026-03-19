package com.stockgame.service;

import com.stockgame.dto.StockQuote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockCacheService {

    @Autowired
    private StockService stockService;

    // Cache-Einträge mit Timestamp
    private record CacheEntry<T>(T data, long timestamp) {}

    private final Map<String, CacheEntry<StockQuote>>       quoteCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<Double>>>     intradayCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<StockService.CandleData>>> candleCache = new ConcurrentHashMap<>();

    // TTL-Einstellungen
    private static final long QUOTE_TTL    = 60_000L;        // 1 Minute  – Kurse
    private static final long INTRADAY_TTL = 300_000L;       // 5 Minuten – Intraday-Linie
    private static final long CANDLE_TTL   = 600_000L;       // 10 Minuten – Kerzen (Tagesdaten)

    // ===== QUOTE =====

    public StockQuote getQuote(String symbol) {
        CacheEntry<StockQuote> entry = quoteCache.get(symbol);
        if (entry != null && !isExpired(entry, QUOTE_TTL)) {
            return entry.data();
        }
        StockQuote fresh = stockService.getQuote(symbol);
        quoteCache.put(symbol, new CacheEntry<>(fresh, now()));
        return fresh;
    }

    /** Direkt in Cache schreiben – z.B. vom PreloadService */
    public void putQuote(String symbol, StockQuote quote) {
        quoteCache.put(symbol, new CacheEntry<>(quote, now()));
    }

    /** Prüfen ob ein Quote bereits frisch im Cache liegt */
    public boolean hasQuote(String symbol) {
        CacheEntry<StockQuote> entry = quoteCache.get(symbol);
        return entry != null && !isExpired(entry, QUOTE_TTL);
    }

    // ===== INTRADAY =====

    public List<Double> getIntraday(String symbol) {
        CacheEntry<List<Double>> entry = intradayCache.get(symbol);
        if (entry != null && !isExpired(entry, INTRADAY_TTL)) {
            return entry.data();
        }
        List<Double> fresh = stockService.getIntradayPrices(symbol);
        intradayCache.put(symbol, new CacheEntry<>(fresh, now()));
        return fresh;
    }

    // ===== CANDLES =====

    public List<StockService.CandleData> getCandles(String symbol) {
        CacheEntry<List<StockService.CandleData>> entry = candleCache.get(symbol);
        if (entry != null && !isExpired(entry, CANDLE_TTL)) {
            return entry.data();
        }
        List<StockService.CandleData> fresh = stockService.getCandleData(symbol);
        candleCache.put(symbol, new CacheEntry<>(fresh, now()));
        return fresh;
    }

    // ===== CACHE MANAGEMENT =====

    /** Gesamten Cache leeren – z.B. nach Key-Wechsel */
    public void clearAll() {
        quoteCache.clear();
        intradayCache.clear();
        candleCache.clear();
    }

    /** Einzelnen Quote-Eintrag ungültig machen */
    public void invalidateQuote(String symbol) {
        quoteCache.remove(symbol);
    }

    /** Anzahl gecachter Quotes – für Monitoring */
    public int cachedQuoteCount() {
        return quoteCache.size();
    }

    // ===== HELPERS =====

    private boolean isExpired(CacheEntry<?> entry, long ttl) {
        return now() - entry.timestamp() > ttl;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
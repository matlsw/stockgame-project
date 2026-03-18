package com.stockgame.controller;

import com.stockgame.dto.StockQuote;
import com.stockgame.service.StockCacheService;
import com.stockgame.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    @Autowired
    private StockCacheService cacheService;

    @Autowired
    private StockService stockService;

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<?> getQuote(@PathVariable String symbol) {
        try {
            StockQuote quote = cacheService.getQuote(symbol.toUpperCase());
            return ResponseEntity.ok(quote);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/intraday/{symbol}")
    public ResponseEntity<?> getIntraday(@PathVariable String symbol) {
        try {
            List<Double> prices = stockService.getIntradayPrices(symbol.toUpperCase());
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/candles/{symbol}")
    public ResponseEntity<?> getCandles(@PathVariable String symbol) {
        try {
            List<StockService.CandleData> candles = stockService.getCandleData(symbol.toUpperCase());
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/dividends/{symbol}")
    public ResponseEntity<?> getDividends(@PathVariable String symbol) {
        try {
            Map<String, Object> data = stockService.getDividends(symbol.toUpperCase());
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        try {
            List<StockQuote> results = stockService.searchStocks(q);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
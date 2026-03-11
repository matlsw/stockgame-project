package com.stockgame.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockgame.dto.StockQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockService {

    @Value("${alphavantage.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL = "https://www.alphavantage.co/query";

    /**
     * Aktuellen Kurs einer Aktie abrufen (Global Quote)
     */
    public StockQuote getQuote(String symbol) {
        try {
            String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);
            JsonNode quote = root.get("Global Quote");

            if (quote == null || quote.isEmpty()) {
                throw new RuntimeException("Aktie nicht gefunden: " + symbol + " | Response: " + response);
            }

            double price = Double.parseDouble(quote.get("05. price").asText());
            double change = Double.parseDouble(quote.get("09. change").asText());
            double changePercent = Double.parseDouble(
                quote.get("10. change percent").asText().replace("%", "")
            );
            double open = Double.parseDouble(quote.get("02. open").asText());
            double high = Double.parseDouble(quote.get("03. high").asText());
            double low = Double.parseDouble(quote.get("04. low").asText());
            long volume = Long.parseLong(quote.get("06. volume").asText());

            // Firmenname über SYMBOL_SEARCH holen (oder hardcoded Map)
            String name = getCompanyName(symbol);

            return new StockQuote(symbol.toUpperCase(), name, price, change, changePercent, open, high, low, volume);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Abrufen der Aktie " + symbol + ": " + e.getMessage());
        }
    }


    /**
     * Intraday-Daten fuer Sparkline-Chart
     */
    public List<Double> getIntradayPrices(String symbol) {
        try {
            String url = BASE_URL + "?function=TIME_SERIES_INTRADAY&symbol=" + symbol
                    + "&interval=60min&outputsize=compact&apikey=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);
            JsonNode series = root.get("Time Series (60min)");

            if (series == null) return java.util.List.of();

            List<Double> prices = new ArrayList<>();
            int count = 0;
            for (JsonNode entry : series) {
                prices.add(0, entry.get("4. close").asDouble());
                if (++count >= 8) break;
            }
            return prices;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /**
     * Aktiensuche nach Name oder Symbol
     */
    public List<StockQuote> searchStocks(String query) {
        List<StockQuote> results = new ArrayList<>();
        try {
            String url = BASE_URL + "?function=SYMBOL_SEARCH&keywords=" + query + "&apikey=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);
            JsonNode matches = root.get("bestMatches");

            if (matches != null) {
                for (JsonNode match : matches) {
                    String symbol = match.get("1. symbol").asText();
                    String name = match.get("2. name").asText();
                    // Nur US-Aktien (region = United States)
                    String region = match.get("4. region").asText();
                    if ("United States".equals(region)) {
                        StockQuote sq = new StockQuote();
                        sq.setSymbol(symbol);
                        sq.setName(name);
                        results.add(sq);
                    }
                    if (results.size() >= 8) break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Suchfehler: " + e.getMessage());
        }
        return results;
    }

    /**
     * Firmenname aus bekannten Symbolen — Fallback wenn keine extra API-Calls nötig
     */
    private String getCompanyName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL" -> "Apple Inc.";
            case "MSFT" -> "Microsoft Corp.";
            case "GOOGL" -> "Alphabet Inc.";
            case "AMZN" -> "Amazon.com Inc.";
            case "TSLA" -> "Tesla Inc.";
            case "META" -> "Meta Platforms Inc.";
            case "NVDA" -> "NVIDIA Corp.";
            case "NFLX" -> "Netflix Inc.";
            case "AMD" -> "Advanced Micro Devices";
            case "INTC" -> "Intel Corp.";
            case "JPM" -> "JPMorgan Chase";
            case "BAC" -> "Bank of America";
            case "DIS" -> "The Walt Disney Co.";
            case "SPOT" -> "Spotify Technology";
            case "UBER" -> "Uber Technologies";
            default -> symbol;
        };
    }
}

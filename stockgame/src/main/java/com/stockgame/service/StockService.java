package com.stockgame.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockgame.dto.StockQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockService {

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL = "https://finnhub.io/api/v1";

    public StockQuote getQuote(String symbol) {
        try {
            String url = BASE_URL + "/quote?symbol=" + symbol + "&token=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode q = mapper.readTree(response);

            if (q.get("c") == null || q.get("c").asDouble() == 0) {
                throw new RuntimeException("Aktie nicht gefunden: " + symbol);
            }

            double price = q.get("c").asDouble();
            double change = q.get("d").asDouble();
            double changePercent = q.get("dp").asDouble();
            double open = q.get("o").asDouble();
            double high = q.get("h").asDouble();
            double low = q.get("l").asDouble();
            double prevClose = q.get("pc").asDouble();
            String name = getCompanyName(symbol);

            return new StockQuote(symbol.toUpperCase(), name, price, change, changePercent, open, high, low, 0L);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Abrufen der Aktie " + symbol + ": " + e.getMessage());
        }
    }

    public List<Double> getIntradayPrices(String symbol) {
        try {
            long to = Instant.now().getEpochSecond();
            long from = to - 24 * 60 * 60;
            String url = BASE_URL + "/stock/candle?symbol=" + symbol
                    + "&resolution=60&from=" + from + "&to=" + to + "&token=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);

            if (!"ok".equals(root.path("s").asText())) return List.of();

            List<Double> prices = new ArrayList<>();
            for (JsonNode val : root.get("c")) {
                prices.add(val.asDouble());
            }
            return prices;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<StockQuote> searchStocks(String query) {
        List<StockQuote> results = new ArrayList<>();
        try {
            String url = BASE_URL + "/search?q=" + query + "&token=" + apiKey;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(response);
            JsonNode matches = root.get("result");

            if (matches != null) {
                for (JsonNode match : matches) {
                    if (!"Common Stock".equals(match.path("type").asText())) continue;
                    StockQuote sq = new StockQuote();
                    sq.setSymbol(match.get("symbol").asText());
                    sq.setName(match.get("description").asText());
                    results.add(sq);
                    if (results.size() >= 8) break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Suchfehler: " + e.getMessage());
        }
        return results;
    }

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
            case "JPM" -> "JPMorgan Chase";
            default -> symbol;
        };
    }
}
package com.stockgame.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockgame.dto.StockQuote;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=";
    private static final String INTRADAY_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    public StockQuote getQuote(String symbol) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + symbol, HttpMethod.GET, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode result = root.path("quoteResponse").path("result");

            if (result.isEmpty()) {
                throw new RuntimeException("Aktie nicht gefunden: " + symbol);
            }

            JsonNode q = result.get(0);
            double price = q.path("regularMarketPrice").asDouble();
            double change = q.path("regularMarketChange").asDouble();
            double changePercent = q.path("regularMarketChangePercent").asDouble();
            double open = q.path("regularMarketOpen").asDouble();
            double high = q.path("regularMarketDayHigh").asDouble();
            double low = q.path("regularMarketDayLow").asDouble();
            long volume = q.path("regularMarketVolume").asLong();
            String name = q.path("longName").asText(symbol);

            return new StockQuote(symbol.toUpperCase(), name, price, change, changePercent, open, high, low, volume);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Abrufen der Aktie " + symbol + ": " + e.getMessage());
        }
    }

    public List<Double> getIntradayPrices(String symbol) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    INTRADAY_URL + symbol + "?interval=60m&range=1d", HttpMethod.GET, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode closes = root.path("chart").path("result").get(0)
                    .path("indicators").path("quote").get(0).path("close");

            List<Double> prices = new ArrayList<>();
            for (JsonNode val : closes) {
                if (!val.isNull()) prices.add(val.asDouble());
            }
            return prices;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<StockQuote> searchStocks(String query) {
        List<StockQuote> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://query1.finance.yahoo.com/v1/finance/search?q=" + query + "&quotesCount=8&lang=en-US",
                    HttpMethod.GET, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode quotes = root.path("quotes");

            for (JsonNode match : quotes) {
                if ("EQUITY".equals(match.path("quoteType").asText())) {
                    StockQuote sq = new StockQuote();
                    sq.setSymbol(match.path("symbol").asText());
                    sq.setName(match.path("longname").asText(match.path("shortname").asText()));
                    results.add(sq);
                }
                if (results.size() >= 8) break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Suchfehler: " + e.getMessage());
        }
        return results;
    }
}
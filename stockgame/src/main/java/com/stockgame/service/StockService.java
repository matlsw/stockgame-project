package com.stockgame.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockgame.dto.StockQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class StockService {

    @Value("${finnhub.api.key}")
    private String finnhubKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public record CandleData(long time, double open, double high, double low, double close) {}

    // ===== QUOTE =====

    public StockQuote getQuote(String symbol) {
        try {
            if (!symbol.contains(".")) {
                return getQuoteFinnhub(symbol);
            } else {
                return getQuoteStooq(symbol);
            }
        } catch (Exception e) {
            try {
                return getQuoteStooq(symbol);
            } catch (Exception e2) {
                throw new RuntimeException("Fehler beim Abrufen der Aktie " + symbol + ": " + e2.getMessage());
            }
        }
    }

    private StockQuote getQuoteFinnhub(String symbol) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", finnhubKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> quoteRes = restTemplate.exchange(
                "https://finnhub.io/api/v1/quote?symbol=" + symbol,
                HttpMethod.GET, entity, String.class);
        JsonNode q = mapper.readTree(quoteRes.getBody());

        double price = q.path("c").asDouble();
        if (price == 0) throw new RuntimeException("Aktie nicht gefunden: " + symbol);

        double change = q.path("d").asDouble();
        double changePercent = q.path("dp").asDouble();
        double open = q.path("o").asDouble();
        double high = q.path("h").asDouble();
        double low = q.path("l").asDouble();

        String name = getCompanyNameFinnhub(symbol, entity);
        return new StockQuote(symbol.toUpperCase(), name, price, change, changePercent, open, high, low, 0L);
    }

    private String getCompanyNameFinnhub(String symbol, HttpEntity<String> entity) {
        try {
            ResponseEntity<String> profileRes = restTemplate.exchange(
                    "https://finnhub.io/api/v1/stock/profile2?symbol=" + symbol,
                    HttpMethod.GET, entity, String.class);
            JsonNode profile = mapper.readTree(profileRes.getBody());
            String name = profile.path("name").asText("");
            return name.isEmpty() ? symbol : name;
        } catch (Exception e) {
            return symbol;
        }
    }

    private StockQuote getQuoteStooq(String symbol) throws Exception {
        String url = "https://stooq.com/q/l/?s=" + symbol.toLowerCase() + "&f=sd2t2ohlcv&h&e=csv";
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String csv = response.getBody();
        if (csv == null || csv.trim().isEmpty()) throw new RuntimeException("Keine Daten für: " + symbol);

        String[] lines = csv.trim().split("\n");
        if (lines.length < 2) throw new RuntimeException("Aktie nicht gefunden: " + symbol);

        String[] parts = lines[1].trim().split(",");
        if (parts.length < 7) throw new RuntimeException("Ungültige Daten für: " + symbol);

        double open  = parseDouble(parts[3]);
        double high  = parseDouble(parts[4]);
        double low   = parseDouble(parts[5]);
        double close = parseDouble(parts[6]);
        long volume  = parts.length > 7 ? parseLong(parts[7]) : 0L;

        if (close == 0) throw new RuntimeException("Kein Kurs verfügbar für: " + symbol);

        double change = close - open;
        double changePercent = open > 0 ? (change / open) * 100 : 0;

        return new StockQuote(symbol.toUpperCase(), getCompanyName(symbol), close, change, changePercent, open, high, low, volume);
    }

    // ===== INTRADAY (Line Chart) =====

    public List<Double> getIntradayPrices(String symbol) {
        try {
            if (!symbol.contains(".")) return getIntradayFinnhub(symbol);
            else return getIntradayStooq(symbol);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Double> getIntradayFinnhub(String symbol) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", finnhubKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        long to = System.currentTimeMillis() / 1000;
        long from = to - 86400;

        ResponseEntity<String> response = restTemplate.exchange(
                "https://finnhub.io/api/v1/stock/candle?symbol=" + symbol + "&resolution=60&from=" + from + "&to=" + to,
                HttpMethod.GET, entity, String.class);

        JsonNode root = mapper.readTree(response.getBody());
        if (!"ok".equals(root.path("s").asText())) return List.of();

        List<Double> prices = new ArrayList<>();
        for (JsonNode val : root.path("c")) prices.add(val.asDouble());
        return prices;
    }

    private List<Double> getIntradayStooq(String symbol) throws Exception {
        String url = "https://stooq.com/q/d/l/?s=" + symbol.toLowerCase() + "&i=d";
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String csv = response.getBody();
        if (csv == null) return List.of();

        String[] lines = csv.trim().split("\n");
        List<Double> prices = new ArrayList<>();
        int start = Math.max(1, lines.length - 8);
        for (int i = start; i < lines.length; i++) {
            String[] parts = lines[i].trim().split(",");
            if (parts.length >= 5) {
                double close = parseDouble(parts[4]);
                if (close > 0) prices.add(close);
            }
        }
        return prices;
    }

    // ===== CANDLES (Candlestick Chart) =====

    public List<CandleData> getCandleData(String symbol) {
        try {
            if (!symbol.contains(".")) return getCandlesFinnhub(symbol);
            else return getCandlesStooq(symbol);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<CandleData> getCandlesFinnhub(String symbol) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Finnhub-Token", finnhubKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        long to = System.currentTimeMillis() / 1000;
        long from = to - (30L * 86400);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://finnhub.io/api/v1/stock/candle?symbol=" + symbol + "&resolution=D&from=" + from + "&to=" + to,
                HttpMethod.GET, entity, String.class);

        JsonNode root = mapper.readTree(response.getBody());
        if (!"ok".equals(root.path("s").asText())) return List.of();

        JsonNode opens  = root.path("o");
        JsonNode highs  = root.path("h");
        JsonNode lows   = root.path("l");
        JsonNode closes = root.path("c");
        JsonNode times  = root.path("t");

        List<CandleData> candles = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            candles.add(new CandleData(
                    times.get(i).asLong(),
                    opens.get(i).asDouble(),
                    highs.get(i).asDouble(),
                    lows.get(i).asDouble(),
                    closes.get(i).asDouble()
            ));
        }
        return candles;
    }

    private List<CandleData> getCandlesStooq(String symbol) throws Exception {
        String url = "https://stooq.com/q/d/l/?s=" + symbol.toLowerCase() + "&i=d";
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String csv = response.getBody();
        if (csv == null) return List.of();

        String[] lines = csv.trim().split("\n");
        List<CandleData> candles = new ArrayList<>();
        int start = Math.max(1, lines.length - 30);
        for (int i = start; i < lines.length; i++) {
            String[] parts = lines[i].trim().split(",");
            if (parts.length >= 5) {
                try {
                    LocalDate date = LocalDate.parse(parts[0].trim());
                    long ts = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
                    double open  = parseDouble(parts[1]);
                    double high  = parseDouble(parts[2]);
                    double low   = parseDouble(parts[3]);
                    double close = parseDouble(parts[4]);
                    if (close > 0) candles.add(new CandleData(ts, open, high, low, close));
                } catch (Exception ignored) {}
            }
        }
        return candles;
    }

    // ===== DIVIDENDS =====

    public Map<String, Object> getDividends(String symbol) {
        Map<String, Object> result = new HashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Finnhub-Token", finnhubKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Dividenden
            ResponseEntity<String> divRes = restTemplate.exchange(
                    "https://finnhub.io/api/v1/stock/dividend2?symbol=" + symbol,
                    HttpMethod.GET, entity, String.class);
            JsonNode divRoot = mapper.readTree(divRes.getBody());
            JsonNode divData = divRoot.path("data");

            List<Map<String, Object>> dividends = new ArrayList<>();
            if (divData.isArray()) {
                int count = 0;
                for (JsonNode d : divData) {
                    if (count++ >= 5) break;
                    Map<String, Object> div = new HashMap<>();
                    div.put("amount", d.path("amount").asDouble());
                    div.put("exDate", d.path("exDate").asText());
                    div.put("payDate", d.path("payDate").asText());
                    dividends.add(div);
                }
            }
            result.put("dividends", dividends);

            // Fundamentalkennzahlen
            ResponseEntity<String> metricRes = restTemplate.exchange(
                    "https://finnhub.io/api/v1/stock/metric?symbol=" + symbol + "&metric=all",
                    HttpMethod.GET, entity, String.class);
            JsonNode metrics = mapper.readTree(metricRes.getBody()).path("metric");

            result.put("dividendYield", metrics.path("currentDividendYieldTTM").asDouble());
            result.put("peRatio", metrics.path("peBasicExclExtraTTM").asDouble());
            result.put("eps", metrics.path("epsBasicExclExtraItemsTTM").asDouble());
            result.put("marketCap", metrics.path("marketCapitalization").asDouble());
            result.put("weekHigh52", metrics.path("52WeekHigh").asDouble());
            result.put("weekLow52", metrics.path("52WeekLow").asDouble());

        } catch (Exception e) {
            result.put("error", "Keine Daten verfügbar");
        }
        return result;
    }

    // ===== SEARCH =====

    public List<StockQuote> searchStocks(String query) {
        List<StockQuote> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Finnhub-Token", finnhubKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://finnhub.io/api/v1/search?q=" + query,
                    HttpMethod.GET, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());
            for (JsonNode match : root.path("result")) {
                String type = match.path("type").asText("");
                if ("Common Stock".equals(type) || "EQS".equals(type)) {
                    StockQuote sq = new StockQuote();
                    sq.setSymbol(match.path("symbol").asText());
                    sq.setName(match.path("description").asText());
                    results.add(sq);
                }
                if (results.size() >= 8) break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Suchfehler: " + e.getMessage());
        }
        return results;
    }

    // ===== HELPERS =====

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private String getCompanyName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL"    -> "Apple Inc.";
            case "MSFT"    -> "Microsoft Corp.";
            case "GOOGL"   -> "Alphabet Inc.";
            case "AMZN"    -> "Amazon.com Inc.";
            case "TSLA"    -> "Tesla Inc.";
            case "META"    -> "Meta Platforms Inc.";
            case "NVDA"    -> "NVIDIA Corp.";
            case "NFLX"    -> "Netflix Inc.";
            case "AMD"     -> "Advanced Micro Devices";
            case "JPM"     -> "JPMorgan Chase";
            case "PAL.VI"  -> "Palfinger AG";
            case "EBS.VI"  -> "Erste Group Bank AG";
            case "OMV.VI"  -> "OMV AG";
            case "VOE.VI"  -> "Voestalpine AG";
            case "SAP.DE"  -> "SAP SE";
            case "SIE.DE"  -> "Siemens AG";
            case "BMW.DE"  -> "BMW AG";
            case "VOW3.DE" -> "Volkswagen AG";
            case "ADS.DE"  -> "Adidas AG";
            default -> symbol;
        };
    }
}
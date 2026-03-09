package com.stockgame.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockQuote {
    private String symbol;
    private String name;
    private double price;
    private double change;
    private double changePercent;
    private double open;
    private double high;
    private double low;
    private long volume;
}

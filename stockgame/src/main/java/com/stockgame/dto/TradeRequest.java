package com.stockgame.dto;
import lombok.Data;
@Data
public class TradeRequest {
    private String symbol;
    private Integer quantity;
}

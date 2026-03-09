package com.stockgame.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "symbol"}))
@Data
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String symbol;      // z.B. "AAPL"

    @Column(nullable = false)
    private String companyName; // z.B. "Apple Inc."

    @Column(nullable = false)
    private Integer quantity;   // Anzahl der Aktien

    @Column(nullable = false)
    private Double avgBuyPrice; // Durchschnittlicher Kaufpreis

    public Portfolio(User user, String symbol, String companyName, int quantity, double avgBuyPrice) {
        this.user = user;
        this.symbol = symbol;
        this.companyName = companyName;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }
}

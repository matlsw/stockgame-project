package com.stockgame.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type; // BUY oder SELL

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Double price; // Preis zum Zeitpunkt der Transaktion

    @Column(nullable = false)
    private Double total; // quantity * price

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public enum TransactionType {
        BUY, SELL
    }

    public Transaction(User user, String symbol, String companyName,
                       TransactionType type, int quantity, double price) {
        this.user = user;
        this.symbol = symbol;
        this.companyName = companyName;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.total = quantity * price;
        this.timestamp = LocalDateTime.now();
    }
}

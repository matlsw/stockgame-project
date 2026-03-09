package com.stockgame.service;

import com.stockgame.dto.StockQuote;
import com.stockgame.model.Portfolio;
import com.stockgame.model.Transaction;
import com.stockgame.model.User;
import com.stockgame.repository.PortfolioRepository;
import com.stockgame.repository.TransactionRepository;
import com.stockgame.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PortfolioService {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockService stockService;

    /**
     * Aktie kaufen
     */
    @Transactional
    public String buyStock(User user, String symbol, int quantity) {
        if (quantity <= 0) throw new RuntimeException("Ungültige Menge");

        StockQuote quote = stockService.getQuote(symbol);
        double totalCost = quote.getPrice() * quantity;

        if (user.getBalance() < totalCost) {
            throw new RuntimeException(String.format(
                "Nicht genug Kapital. Benötigt: $%.2f, Verfügbar: $%.2f",
                totalCost, user.getBalance()
            ));
        }

        // Guthaben abziehen
        user.setBalance(user.getBalance() - totalCost);
        userRepository.save(user);

        // Portfolio aktualisieren
        Optional<Portfolio> existing = portfolioRepository.findByUserAndSymbol(user, symbol.toUpperCase());
        if (existing.isPresent()) {
            Portfolio p = existing.get();
            double newAvgPrice = ((p.getAvgBuyPrice() * p.getQuantity()) + totalCost)
                                 / (p.getQuantity() + quantity);
            p.setQuantity(p.getQuantity() + quantity);
            p.setAvgBuyPrice(newAvgPrice);
            portfolioRepository.save(p);
        } else {
            Portfolio p = new Portfolio(user, symbol.toUpperCase(), quote.getName(), quantity, quote.getPrice());
            portfolioRepository.save(p);
        }

        // Transaktion speichern
        Transaction tx = new Transaction(user, symbol.toUpperCase(), quote.getName(),
                Transaction.TransactionType.BUY, quantity, quote.getPrice());
        transactionRepository.save(tx);

        return String.format("✅ %dx %s gekauft für $%.2f", quantity, symbol.toUpperCase(), totalCost);
    }

    /**
     * Aktie verkaufen
     */
    @Transactional
    public String sellStock(User user, String symbol, int quantity) {
        if (quantity <= 0) throw new RuntimeException("Ungültige Menge");

        Portfolio portfolio = portfolioRepository.findByUserAndSymbol(user, symbol.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Du besitzt keine Aktien von " + symbol));

        if (portfolio.getQuantity() < quantity) {
            throw new RuntimeException(String.format(
                "Nicht genug Aktien. Du besitzt: %d, Verkauf: %d",
                portfolio.getQuantity(), quantity
            ));
        }

        StockQuote quote = stockService.getQuote(symbol);
        double totalRevenue = quote.getPrice() * quantity;

        // Guthaben gutschreiben
        user.setBalance(user.getBalance() + totalRevenue);
        userRepository.save(user);

        // Portfolio aktualisieren
        if (portfolio.getQuantity().equals(quantity)) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolio.setQuantity(portfolio.getQuantity() - quantity);
            portfolioRepository.save(portfolio);
        }

        // Transaktion speichern
        Transaction tx = new Transaction(user, symbol.toUpperCase(), quote.getName(),
                Transaction.TransactionType.SELL, quantity, quote.getPrice());
        transactionRepository.save(tx);

        return String.format("✅ %dx %s verkauft für $%.2f", quantity, symbol.toUpperCase(), totalRevenue);
    }

    public List<Portfolio> getPortfolio(User user) {
        return portfolioRepository.findByUser(user);
    }

    public List<Transaction> getTransactions(User user) {
        return transactionRepository.findByUserOrderByTimestampDesc(user);
    }
}

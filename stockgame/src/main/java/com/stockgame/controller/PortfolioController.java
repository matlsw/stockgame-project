package com.stockgame.controller;

import com.stockgame.dto.TradeRequest;
import com.stockgame.model.Portfolio;
import com.stockgame.model.Transaction;
import com.stockgame.model.User;
import com.stockgame.repository.UserRepository;
import com.stockgame.service.PortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    @Autowired private PortfolioService portfolioService;
    @Autowired private UserRepository userRepository;

    private User getCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<List<Portfolio>> getPortfolio(Authentication auth) {
        return ResponseEntity.ok(portfolioService.getPortfolio(getCurrentUser(auth)));
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(Authentication auth) {
        User user = getCurrentUser(auth);
        return ResponseEntity.ok(Map.of(
            "balance", user.getBalance(),
            "username", user.getUsername()
        ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(Authentication auth) {
        return ResponseEntity.ok(portfolioService.getTransactions(getCurrentUser(auth)));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody TradeRequest req, Authentication auth) {
        try {
            String msg = portfolioService.buyStock(getCurrentUser(auth), req.getSymbol(), req.getQuantity());
            return ResponseEntity.ok(Map.of("message", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody TradeRequest req, Authentication auth) {
        try {
            String msg = portfolioService.sellStock(getCurrentUser(auth), req.getSymbol(), req.getQuantity());
            return ResponseEntity.ok(Map.of("message", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

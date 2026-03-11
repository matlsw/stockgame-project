package com.stockgame.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Render liefert postgres://user:pass@host/db
        // Spring braucht jdbc:postgresql://host/db
        if (databaseUrl != null && databaseUrl.startsWith("postgres://")) {
            String jdbcUrl = databaseUrl
                    .replace("postgres://", "jdbc:postgresql://");

            // User und Passwort aus URL extrahieren
            // Format: jdbc:postgresql://user:pass@host/db
            try {
                java.net.URI uri = new java.net.URI(databaseUrl);
                String[] userInfo = uri.getUserInfo().split(":");
                String username = userInfo[0];
                String password = userInfo.length > 1 ? userInfo[1] : "";
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath();
                String cleanJdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

                return DataSourceBuilder.create()
                        .url(cleanJdbcUrl)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Parsen der DATABASE_URL: " + e.getMessage());
            }
        }

        // Fallback: H2 für lokale Entwicklung
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:stockgamedb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }
}

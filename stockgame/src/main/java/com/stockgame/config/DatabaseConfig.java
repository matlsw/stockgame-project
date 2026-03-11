package com.stockgame.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() throws Exception {

        // Render liefert: postgres://user:pass@host:port/dbname
        if (databaseUrl != null && !databaseUrl.isBlank() && databaseUrl.startsWith("postgres")) {

            URI uri = new URI(databaseUrl.replace("postgres://", "http://"));
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath(); // /dbname
            String[] userInfo = uri.getUserInfo().split(":");
            String username = userInfo[0];
            String password = userInfo[1];

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path
                    + "?sslmode=require";

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setMaximumPoolSize(5);
            return ds;
        }

        // Fallback: H2 für lokale Entwicklung
        return DataSourceBuilder.create()
                .url("jdbc:h2:mem:stockgamedb;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
    }
}

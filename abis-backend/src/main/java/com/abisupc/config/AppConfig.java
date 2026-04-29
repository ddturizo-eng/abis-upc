package com.abisupc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class AppConfig {

    private static final HikariDataSource dataSource;

    static {
        String dbUrl  = System.getenv("ABIS_DB_URL");
        String dbUser = System.getenv("ABIS_DB_USER");
        String dbPass = System.getenv("ABIS_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPass == null) {
            throw new IllegalStateException(
                    "Variables de entorno requeridas: ABIS_DB_URL, ABIS_DB_USER, ABIS_DB_PASSWORD"
            );
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPass);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30_000);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
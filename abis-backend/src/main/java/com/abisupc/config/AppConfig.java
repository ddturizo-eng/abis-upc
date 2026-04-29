package com.abisupc.config;

import java.sql.Connection;
import java.sql.SQLException;

public class AppConfig {

    private static final HikariDataSource dataSource;

    static {
        String dbUrl  = System.getenv("ABIS_DB_URL");
        String dbPass = System.getenv("ABIS_DB_PASSWORD");

        if (dbUrl == null || dbPass == null)
            throw new IllegalStateException(
                    "Variables ABIS_DB_URL y ABIS_DB_PASSWORD requeridas");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setPassword(dbPass);
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
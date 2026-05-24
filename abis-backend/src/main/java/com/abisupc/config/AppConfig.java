package com.abisupc.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000L;
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

        dataSource = createDataSource(dbUrl, dbUser, dbPass);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static HikariDataSource createDataSource(String dbUrl, String dbUser, String dbPass) {
        List<String> candidateUrls = new ArrayList<>();
        candidateUrls.add(dbUrl);
        candidateUrls.addAll(buildFallbackUrls(dbUrl));

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_CONNECTION_ATTEMPTS; attempt++) {
            for (String candidateUrl : candidateUrls) {
                try {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(candidateUrl);
                    config.setUsername(dbUser);
                    config.setPassword(dbPass);
                    config.setMaximumPoolSize(10);
                    config.setConnectionTimeout(30_000);
                    return new HikariDataSource(config);
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                }
            }

            if (attempt < MAX_CONNECTION_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrumpido mientras se esperaba Oracle", ex);
                }
            }
        }

        throw lastFailure != null ? lastFailure : new IllegalStateException("No fue posible crear el pool de Oracle");
    }

    private static List<String> buildFallbackUrls(String dbUrl) {
        List<String> fallbacks = new ArrayList<>();

        if (dbUrl.endsWith("/XEPDB1")) {
            fallbacks.add(dbUrl.substring(0, dbUrl.length() - "/XEPDB1".length()) + "/XE");
        }

        if (dbUrl.endsWith(":XEPDB1")) {
            fallbacks.add(dbUrl.substring(0, dbUrl.length() - ":XEPDB1".length()) + ":XE");
        }

        if (dbUrl.endsWith("/XE")) {
            fallbacks.add(dbUrl.substring(0, dbUrl.length() - "/XE".length()) + "/XEPDB1");
        }

        if (dbUrl.endsWith(":XE")) {
            fallbacks.add(dbUrl.substring(0, dbUrl.length() - ":XE".length()) + ":XEPDB1");
        }

        return fallbacks;
    }
}

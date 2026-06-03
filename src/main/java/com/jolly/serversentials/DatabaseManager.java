package com.jolly.serversentials;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.concurrent.CompletableFuture;

/**
 * Universal DatabaseManager for Serversentials
 * Supports SQLite and MySQL via HikariCP connection pooling
 * Automatically closes resources and provides async helpers
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    private final boolean useMySQL;
    private final String host, database, username, password;
    private final int port;
    private final File sqliteFile;

    public DatabaseManager(JavaPlugin plugin, boolean useMySQL,
                           String host, int port, String database,
                           String username, String password,
                           File sqliteFile) {
        this.plugin = plugin;
        this.useMySQL = useMySQL;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.sqliteFile = sqliteFile;
        initializePool();
    }

    private void initializePool() {
        HikariConfig config = new HikariConfig();
        if (useMySQL) {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            if (!sqliteFile.exists()) {
                try {
                    plugin.getDataFolder().mkdirs();
                    sqliteFile.createNewFile();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            config.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1); // SQLite is single-threaded for writes; pool size of 1 avoids write lockouts
        }
        this.dataSource = new HikariDataSource(config);
    }

    public boolean isMySQL() {
        return useMySQL;
    }

    // ================================
    // 🔹 Connection Handling
    // ================================
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initializePool();
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ================================
    // 🔹 Parameter Utility
    // ================================
    private void setParameters(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // ================================
    // 🔹 Safe Query Helpers (Auto-close)
    // ================================
    public <T> T querySafe(String sql, ResultProcessor<T> processor, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return processor.process(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int updateSafe(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // ================================
    // 🔹 Async Helpers (Folia-safe)
    // ================================
    public <T> CompletableFuture<T> querySafeAsync(String sql, ResultProcessor<T> processor, Object... params) {
        return CompletableFuture.supplyAsync(() -> querySafe(sql, processor, params));
    }

    public CompletableFuture<Integer> updateSafeAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> updateSafe(sql, params));
    }

    // ================================
    // 🔹 Functional Interface
    // ================================
    @FunctionalInterface
    public interface ResultProcessor<T> {
        T process(ResultSet rs) throws SQLException;
    }
}

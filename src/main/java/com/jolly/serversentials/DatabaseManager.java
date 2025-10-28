package com.jolly.serversentials;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.concurrent.CompletableFuture;

/**
 * Universal DatabaseManager for Serversentials
 * Supports SQLite and MySQL
 * Automatically closes resources and provides async helpers
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    private final boolean useMySQL;
    private final String host, database, username, password;
    private final int port;
    private final File sqliteFile;

    public DatabaseManager(JavaPlugin plugin, boolean useMySQL,
                           String host, int port, String database,
                           String username, String password) {
        this.plugin = plugin;
        this.useMySQL = useMySQL;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.sqliteFile = new File(plugin.getDataFolder(), "data.db");
    }

    // ================================
    // 🔹 Connection Handling
    // ================================
    public Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        if (useMySQL) {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
        } else {
            if (!sqliteFile.exists()) {
                try {
                    plugin.getDataFolder().mkdirs();
                    sqliteFile.createNewFile();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile);
        }

        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
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

    /**
     * Run a SELECT query safely and process the ResultSet using a callback.
     * Auto-closes resources.
     */
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

    /**
     * Run an UPDATE / INSERT / DELETE query safely (auto-closing).
     */
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

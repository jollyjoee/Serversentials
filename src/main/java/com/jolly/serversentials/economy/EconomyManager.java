package com.jolly.serversentials.economy;

import com.jolly.serversentials.DatabaseManager;
import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EconomyManager implements Economy {

    private final DatabaseManager db;
    private final Scheduler scheduler;
    private final VaultHook vaultHook;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private String currencySymbol;
    private final Serversentials plugin;
    // Cache for top balances
    private List<Map.Entry<UUID, Double>> cachedTopBalances = new ArrayList<>();
    private long cacheExpire = 0L; // timestamp in millis

    public EconomyManager(DatabaseManager db, Scheduler scheduler, VaultHook vaultHook, String currencySymbol, Serversentials plugin) {
        this.db = db;
        this.scheduler = scheduler;
        this.vaultHook = vaultHook;
        this.currencySymbol = currencySymbol;
        this.plugin = plugin;

        // Create economy table
        scheduler.runAsync(() -> db.updateSafe("""
            CREATE TABLE IF NOT EXISTS economy (
                uuid VARCHAR(36) PRIMARY KEY,
                balance DOUBLE NOT NULL DEFAULT 0
            )
        """));
    }

    // ======================================================
    // Async Balance Methods
    // ======================================================
    public CompletableFuture<Double> getBalanceAsync(UUID uuid) {
        if (vaultHook.isHooked()) {
            return CompletableFuture.supplyAsync(() -> vaultHook.getEconomy().getBalance(Bukkit.getOfflinePlayer(uuid)));
        } else {
            return db.querySafeAsync(
                    "SELECT balance FROM economy WHERE uuid = ?",
                    rs -> rs.next() ? rs.getDouble("balance") : 0.0,
                    uuid.toString()
            );
        }
    }

    public CompletableFuture<Void> setBalanceAsync(UUID uuid, double amount) {
        if (vaultHook.isHooked()) {
            return CompletableFuture.runAsync(() -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                double current = vaultHook.getEconomy().getBalance(player);
                vaultHook.getEconomy().depositPlayer(player, -current + amount);
            });
        } else {
            return db.updateSafeAsync("""
                    INSERT INTO economy (uuid, balance) VALUES (?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance
                    """, uuid.toString(), amount).thenApply(i -> null);
        }
    }

    public CompletableFuture<Void> addBalanceAsync(UUID uuid, double amount) {
        if (vaultHook.isHooked()) {
            return CompletableFuture.runAsync(() -> vaultHook.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), amount));
        } else {
            return getBalanceAsync(uuid).thenCompose(balance -> setBalanceAsync(uuid, balance + amount));
        }
    }

    public CompletableFuture<Void> deductBalanceAsync(UUID uuid, double amount) {
        if (vaultHook.isHooked()) {
            return CompletableFuture.runAsync(() -> vaultHook.getEconomy().withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount));
        } else {
            return getBalanceAsync(uuid).thenCompose(balance -> setBalanceAsync(uuid, balance - amount));
        }
    }

    public CompletableFuture<List<Map.Entry<UUID, Double>>> getTopBalancesAsync(int page, int perPage) {
        long now = System.currentTimeMillis();
        if (cacheExpire > now && !cachedTopBalances.isEmpty()) {
            // return cached page
            int start = Math.max(0, (page - 1) * perPage);
            int end = Math.min(start + perPage, cachedTopBalances.size());
            return CompletableFuture.completedFuture(cachedTopBalances.subList(start, end));
        }

        return db.querySafeAsync(
                "SELECT uuid, balance FROM economy ORDER BY balance DESC",
                rs -> {
                    List<Map.Entry<UUID, Double>> all = new ArrayList<>();
                    while (rs.next()) {
                        all.add(new AbstractMap.SimpleEntry<>(UUID.fromString(rs.getString("uuid")), rs.getDouble("balance")));
                    }
                    cachedTopBalances = all;
                    cacheExpire = System.currentTimeMillis() + 30_000L; // cache for 30 seconds
                    int start = Math.max(0, (page - 1) * perPage);
                    int end = Math.min(start + perPage, all.size());
                    return all.subList(start, end);
                }
        );
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setStartingBalance(Player player) {
        // Has this player ever joined before?
        if (!player.hasPlayedBefore()) {
            double start = plugin.getConfig().getDouble("economy.starting-balance");
            if (start == 0) return;
            setBalanceAsync(player.getUniqueId(), start)
                    .thenRun(() -> {
                        // Notify the player in actionbar
                        String msg = plugin.getConfig().getString("messages.starting-balance")
                                .replace("{symbol}", getCurrencySymbol())
                                .replace("{amount}", String.format("%.2f", start));
                        player.sendActionBar(mm.deserialize(msg));
                    });
        }
    }

    // ======================================================
    // Vault API Methods
    // ======================================================
    @Override
    public boolean isEnabled() { return true; }

    @Override
    public String getName() { return "Serversentials-Economy"; }

    @Override
    public boolean hasBankSupport() { return false; }

    @Override
    public int fractionalDigits() { return 2; }

    @Override
    public String format(double amount) { return currencySymbol + String.format("%.2f", amount); }

    @Override
    public String currencyNamePlural() { return "Coins"; }

    @Override
    public String currencyNameSingular() { return "Coin"; }

    // --------------------------
    // Accounts
    // --------------------------
    @Override
    public boolean hasAccount(String playerName) { return true; }
    @Override
    public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override
    public boolean hasAccount(String playerName, String worldName) { return true; }
    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }

    @Override
    public boolean createPlayerAccount(String playerName) { return true; }
    @Override
    public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) { return true; }
    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }


    // --------------------------
    // World-specific methods (must return EconomyResponse)
    // --------------------------
    @Override
    public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override
    public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override
    public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }
    @Override
    public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        deductBalanceAsync(player.getUniqueId(), amount).join();
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "Withdraw successful");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        deductBalanceAsync(player.getUniqueId(), amount).join();
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "Withdraw successful");
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        addBalanceAsync(player.getUniqueId(), amount).join();
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "Deposit successful");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        addBalanceAsync(player.getUniqueId(), amount).join();
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "Deposit successful");
    }

    // --------------------------
    // Balance
    // --------------------------
    @Override
    public double getBalance(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return vaultHook.isHooked() ? vaultHook.getEconomy().getBalance(p) : getBalanceAsync(p.getUniqueId()).join();
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return vaultHook.isHooked() ? vaultHook.getEconomy().getBalance(player) : getBalanceAsync(player.getUniqueId()).join();
    }

    @Override
    public boolean has(String playerName, double amount) { return getBalance(playerName) >= amount; }

    @Override
    public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        deductBalanceAsync(Bukkit.getOfflinePlayer(playerName).getUniqueId(), amount).join();
        return null;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        deductBalanceAsync(player.getUniqueId(), amount).join();
        return null;
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        addBalanceAsync(Bukkit.getOfflinePlayer(playerName).getUniqueId(), amount).join();
        return null;
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        addBalanceAsync(player.getUniqueId(), amount).join();
        return null;
    }


    // --------------------------
    // Banks (unsupported)
    // --------------------------
    @Override
    public EconomyResponse createBank(String name, String player) { return null; }
    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) { return null; }
    @Override
    public EconomyResponse deleteBank(String name) { return null; }

    @Override
    public EconomyResponse bankBalance(String name) {
        return null;
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) { return null; }
    @Override
    public EconomyResponse bankWithdraw(String name, double amount) { return null; }
    @Override
    public EconomyResponse bankDeposit(String name, double amount) { return null; }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return null;
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return null;
    }

    public double getBankBalance(String name) { return 0; }
    @Override
    public List<String> getBanks() { return Collections.emptyList(); }
}

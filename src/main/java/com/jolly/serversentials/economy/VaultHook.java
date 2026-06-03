package com.jolly.serversentials.economy;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.Scheduler;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private Economy economy;
    private net.milkbowl.vault.chat.Chat chat;
    private boolean hooked = false;
    private final Serversentials plugin;
    private final Scheduler scheduler;

    public VaultHook(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /** Attempt Vault hook after a tick, fallback silently if unavailable */
    public void hook() {
        scheduler.runLater(() -> {
            try {
                // 1. Hook Economy
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    hooked = true;
                    plugin.getLogger().info("✅ Vault Economy hooked successfully! Using " + economy.getName());
                } else {
                    plugin.getLogger().warning("⚠️ Vault found but no Economy provider registered! Falling back to internal economy.");
                }

                // 2. Hook Chat (for prefixes/suffixes)
                RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rspChat = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
                if (rspChat != null) {
                    chat = rspChat.getProvider();
                    plugin.getLogger().info("✅ Vault Chat hooked successfully! Using " + chat.getName());
                } else {
                    plugin.getLogger().info("ℹ️ Vault Chat provider not found. Prefixes/suffixes will be empty.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("⚠️ Vault hook failed: " + e.getMessage());
            }
        }, 1L);
    }

    public boolean isHooked() {
        return hooked && economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isChatHooked() {
        return chat != null;
    }

    public net.milkbowl.vault.chat.Chat getChat() {
        return chat;
    }
}

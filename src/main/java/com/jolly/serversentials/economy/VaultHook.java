package com.jolly.serversentials.economy;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.Scheduler;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private Economy economy;
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
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    hooked = true;
                    plugin.getLogger().info("✅ Vault hooked successfully! Using " + economy.getName());
                } else {
                    plugin.getLogger().warning("⚠️ Vault found but no Economy provider registered! Falling back to internal economy.");
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
}

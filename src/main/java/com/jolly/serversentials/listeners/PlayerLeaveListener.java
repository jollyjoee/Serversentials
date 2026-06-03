package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.commands.utilities.Generic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerLeaveListener implements Listener {

    private final Serversentials plugin;
    private final Scheduler scheduler;

    public PlayerLeaveListener(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String worldName = player.getWorld().getName();
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        
        boolean hasPersistentGM = player.hasPermission("serversentials.persistentgamemode");
        String gamemodeName = player.getGameMode().name();

        // 1. Remove player from active session caches to prevent memory leaks
        Generic.godUsers.remove(uuid);

        // 2. Perform asynchronous database persistence
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "REPLACE INTO leave_data (uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?)",
                    uuid.toString(),
                    worldName,
                    x, y, z
            );
            if (hasPersistentGM) {
                plugin.getDatabase().updateSafe(
                        "REPLACE INTO gamemode_data (uuid, gamemode) VALUES (?, ?)",
                        uuid.toString(),
                        gamemodeName
                );
            }
        });
    }
}

package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class TeleportListener implements Listener {

    private final Serversentials plugin;
    private final Scheduler scheduler;

    public TeleportListener(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.isModuleEnabled("teleport.back")) return;
        saveBackLocation(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.isModuleEnabled("teleport.back")) return;
        Player player = event.getPlayer();

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || 
            cause == PlayerTeleportEvent.TeleportCause.PLUGIN ||
            cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {

            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > 4.0) {
                saveBackLocation(player, from);
            }
        }
    }

    private void saveBackLocation(Player player, Location loc) {
        String serverName = plugin.getConfig().getString("server-name", "unknown");
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "REPLACE INTO back_data (uuid, world, x, y, z, yaw, pitch, server) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    player.getUniqueId().toString(),
                    loc.getWorld().getName(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch(),
                    serverName
            );
        });
    }
}

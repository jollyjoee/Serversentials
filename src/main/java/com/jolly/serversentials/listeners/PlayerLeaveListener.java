package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "INSERT INTO leave_data (uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z",
                    player.getUniqueId().toString(),
                    player.getWorld().getName(),
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
            );
        });
    }
}

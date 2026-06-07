package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.commands.teleports.TpaManager;
import com.jolly.serversentials.commands.utilities.*;
import com.jolly.serversentials.economy.EconomyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final Serversentials plugin;
    private final Fly flyCommand;
    private final Nick nickCommand;
    private final TpaManager tptoggle;
    private final Vanish vanish;
    private final Hide hide;
    private final Generic generic;
    private final Scheduler scheduler;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerJoinListener(Serversentials plugin, Fly flyCommand, Nick nickCommand, TpaManager tptoggle, Vanish vanish, Hide hide, Generic generic, Scheduler scheduler, EconomyManager economy) {
        this.plugin = plugin;
        this.flyCommand = flyCommand;
        this.nickCommand = nickCommand;
        this.tptoggle = tptoggle;
        this.vanish = vanish;
        this.hide = hide;
        this.generic = generic;
        this.scheduler = scheduler;
        this.economy = economy;
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        plugin.getNetworkManager().onPlayerJoinServer(player);
        org.bukkit.Location pendingLoc = com.jolly.serversentials.NetworkPacketHandler.pendingTeleports.remove(player.getUniqueId());
        if (pendingLoc != null) {
            scheduler.runLater(() -> player.teleportAsync(pendingLoc), 5L);
        }

        org.bukkit.Location monitorLoc = com.jolly.serversentials.NetworkPacketHandler.pendingMonitorTeleports.remove(player.getUniqueId());
        if (monitorLoc != null) {
            scheduler.runLater(() -> {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.teleportAsync(monitorLoc);
            }, 5L);
        }

        // Restore from monitor_data if returning from spectator monitor
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            String currentServer = plugin.getConfig().getString("server-name", "unknown");
            plugin.getDatabase().querySafe(
                    "SELECT world, x, y, z, yaw, pitch, gamemode FROM monitor_data WHERE uuid = ? AND server = ?",
                    rs -> {
                        if (rs.next()) {
                            String worldName = rs.getString("world");
                            double x = rs.getDouble("x");
                            double y = rs.getDouble("y");
                            double z = rs.getDouble("z");
                            float yaw = rs.getFloat("yaw");
                            float pitch = rs.getFloat("pitch");
                            String gmName = rs.getString("gamemode");

                            scheduler.run(player, () -> {
                                org.bukkit.World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z, yaw, pitch);
                                    player.teleportAsync(loc);
                                }
                                try {
                                    player.setGameMode(org.bukkit.GameMode.valueOf(gmName));
                                } catch (Exception ignored) {}
                                player.sendActionBar(mm.deserialize("<yellow>Stopped monitoring. Location and gamemode restored."));
                            });

                            // Delete entry
                            scheduler.runAsync(() -> {
                                plugin.getDatabase().updateSafe("DELETE FROM monitor_data WHERE uuid = ?", uuid.toString());
                            });
                        }
                        return null;
                    },
                    uuid.toString(), currentServer
            );
        });

        economy.setStartingBalance(player);
        flyCommand.loadFlyStateAsync(player);
        scheduler.runLater(() -> {
            generic.loadGodStatus(player);
            nickCommand.loadNicknameAsync(player);
            tptoggle.loadToggleState(player);
            vanish.loadVanishStatus(player);
            
            if (player.hasPermission("serversentials.persistentgamemode")) {
                loadPersistentGameMode(player);
            }
        }, 1L);
    }

    private void loadPersistentGameMode(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            String gmName = plugin.getDatabase().querySafe(
                    "SELECT gamemode FROM gamemode_data WHERE uuid = ?",
                    rs -> rs.next() ? rs.getString("gamemode") : null,
                    uuid.toString()
            );
            if (gmName != null) {
                scheduler.run(player, () -> {
                    try {
                        org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gmName);
                        player.setGameMode(gm);
                        player.sendActionBar(mm.deserialize("<green>GameMode restored to <yellow>" + gm.name().toLowerCase()));
                    } catch (IllegalArgumentException ex) {
                        // Ignore invalid/unsupported gamemode names saved
                    }
                });
            }
        });
    }
}

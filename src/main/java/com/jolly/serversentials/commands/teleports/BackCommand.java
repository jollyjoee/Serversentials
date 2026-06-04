package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BackCommand implements CommandExecutor {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BackCommand(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!plugin.isModuleEnabled("teleport.back")) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (!player.hasPermission("serversentials.back")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        UUID uuid = player.getUniqueId();
        String currentServer = plugin.getConfig().getString("server-name", "unknown");
        scheduler.runAsync(() -> {
            Location backLoc = plugin.getDatabase().querySafe(
                    "SELECT world, x, y, z, yaw, pitch, server FROM back_data WHERE uuid = ?",
                    rs -> {
                        if (rs.next()) {
                            String targetServer = rs.getString("server");
                            if (targetServer != null && !targetServer.equalsIgnoreCase(currentServer)) {
                                return null;
                            }
                            World world = Bukkit.getWorld(rs.getString("world"));
                            if (world != null) {
                                return new Location(
                                        world,
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        rs.getFloat("yaw"),
                                        rs.getFloat("pitch")
                                );
                            }
                        }
                        return null;
                    },
                    uuid.toString()
            );

            if (backLoc == null) {
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>No previous location found on this server!")));
                return;
            }

            scheduler.run(player, () -> {
                player.teleportAsync(backLoc).thenRun(() -> {
                    player.sendActionBar(mm.deserialize("<green>Teleported to your previous location!"));
                });
            });
        });

        return true;
    }
}

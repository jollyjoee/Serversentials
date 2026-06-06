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

    private record BackLocation(String server, String world, double x, double y, double z, float yaw, float pitch) {}

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
            BackLocation backLoc = plugin.getDatabase().querySafe(
                    "SELECT world, x, y, z, yaw, pitch, server FROM back_data WHERE uuid = ?",
                    rs -> {
                        if (rs.next()) {
                            return new BackLocation(
                                    rs.getString("server"),
                                    rs.getString("world"),
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch")
                            );
                        }
                        return null;
                    },
                    uuid.toString()
            );

            if (backLoc == null) {
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>No previous location found!")));
                return;
            }

            scheduler.run(player, () -> {
                if (backLoc.server() != null && !backLoc.server().equalsIgnoreCase(currentServer)) {
                    plugin.getNetworkManager().sendPluginMessage(player, "FORWARD_TO_SERVER", backLoc.server(), "TELEPORT_JOIN_REG", player.getUniqueId().toString(), backLoc.world(), backLoc.x(), backLoc.y(), backLoc.z(), backLoc.yaw(), backLoc.pitch());
                    plugin.getNetworkManager().requestPlayerTransfer(player, player.getName(), backLoc.server());
                    player.sendActionBar(mm.deserialize("<green>Teleporting to your previous location on <yellow>" + backLoc.server() + "</yellow>..."));
                } else {
                    World world = Bukkit.getWorld(backLoc.world());
                    if (world == null) {
                        player.sendActionBar(mm.deserialize("<red>World not found on this server: " + backLoc.world()));
                        return;
                    }
                    Location loc = new Location(world, backLoc.x(), backLoc.y(), backLoc.z(), backLoc.yaw(), backLoc.pitch());
                    player.teleportAsync(loc).thenRun(() -> {
                        player.sendActionBar(mm.deserialize("<green>Teleported to your previous location!"));
                    });
                }
            });
        });

        return true;
    }
}

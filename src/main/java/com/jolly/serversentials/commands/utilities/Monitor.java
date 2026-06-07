package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Monitor implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Track per-player state locally
    private final Map<UUID, Location> startLocations = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> startGamemodes = new ConcurrentHashMap<>();
    private final Set<UUID> monitorUsers = ConcurrentHashMap.newKeySet();

    public Monitor(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!plugin.isModuleEnabled("monitor")) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (!player.hasPermission("serversentials.monitor")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        UUID uuid = player.getUniqueId();

        scheduler.runAsync(() -> {
            boolean isAlreadyMonitoring = monitorUsers.contains(uuid) || plugin.getDatabase().querySafe(
                    "SELECT COUNT(*) FROM monitor_data WHERE uuid = ?",
                    rs -> rs.next() && rs.getInt(1) > 0,
                    uuid.toString()
            );

            if (isAlreadyMonitoring) {
                handleStopMonitoring(player);
            } else {
                if (args.length == 0) {
                    player.sendActionBar(mm.deserialize("<yellow>Usage:</yellow> /monitor <player>"));
                    return;
                }

                String targetName = args[0];

                scheduler.run(player, () -> {
                    Player localTarget = Bukkit.getPlayerExact(targetName);
                    if (localTarget != null && localTarget.isOnline()) {
                        startLocalMonitor(player, localTarget);
                    } else {
                        scheduler.runAsync(() -> {
                            if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                                startCrossServerMonitor(player, targetName);
                            } else {
                                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                            }
                        });
                    }
                });
            }
        });

        return true;
    }

    private void startLocalMonitor(Player player, Player target) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation().clone();
        GameMode gm = player.getGameMode();

        monitorUsers.add(uuid);
        startLocations.put(uuid, loc);
        startGamemodes.put(uuid, gm);

        String currentServer = plugin.getConfig().getString("server-name", "unknown");
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "REPLACE INTO monitor_data (uuid, world, x, y, z, yaw, pitch, server, gamemode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    uuid.toString(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), currentServer, gm.name()
            );
        });

        player.setGameMode(GameMode.SPECTATOR);
        player.teleportAsync(target.getLocation().add(0, 2, 0));
        player.sendActionBar(mm.deserialize("<green>You are now monitoring " + target.getName() + "!"));
    }

    private void startCrossServerMonitor(Player player, String targetName) {
        UUID uuid = player.getUniqueId();
        scheduler.run(player, () -> {
            Location loc = player.getLocation().clone();
            GameMode gm = player.getGameMode();
            String currentServer = plugin.getConfig().getString("server-name", "unknown");

            scheduler.runAsync(() -> {
                plugin.getDatabase().updateSafe(
                        "REPLACE INTO monitor_data (uuid, world, x, y, z, yaw, pitch, server, gamemode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        uuid.toString(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), currentServer, gm.name()
                );

                plugin.getNetworkManager().forwardToPlayer(player, targetName, "MONITOR_REQUEST", player.getName(), targetName, uuid.toString());
            });

            player.setGameMode(GameMode.SPECTATOR);
            player.sendActionBar(mm.deserialize("<green>Teleporting to monitor <yellow>" + targetName + "</yellow> (cross-server)..."));
        });
    }

    private void handleStopMonitoring(Player player) {
        UUID uuid = player.getUniqueId();
        String currentServer = plugin.getConfig().getString("server-name", "unknown");

        scheduler.runAsync(() -> {
            plugin.getDatabase().querySafe(
                    "SELECT world, x, y, z, yaw, pitch, server, gamemode FROM monitor_data WHERE uuid = ?",
                    rs -> {
                        if (rs.next()) {
                            String worldName = rs.getString("world");
                            double x = rs.getDouble("x");
                            double y = rs.getDouble("y");
                            double z = rs.getDouble("z");
                            float yaw = rs.getFloat("yaw");
                            float pitch = rs.getFloat("pitch");
                            String server = rs.getString("server");
                            String gmName = rs.getString("gamemode");

                            if (server.equalsIgnoreCase(currentServer)) {
                                scheduler.run(player, () -> {
                                    monitorUsers.remove(uuid);
                                    startLocations.remove(uuid);
                                    startGamemodes.remove(uuid);

                                    org.bukkit.World world = Bukkit.getWorld(worldName);
                                    if (world != null) {
                                        player.teleportAsync(new Location(world, x, y, z, yaw, pitch));
                                    }
                                    try {
                                        player.setGameMode(GameMode.valueOf(gmName));
                                    } catch (Exception ignored) {}
                                    player.sendActionBar(mm.deserialize("<yellow>Stopped monitoring."));
                                });
                                plugin.getDatabase().updateSafe("DELETE FROM monitor_data WHERE uuid = ?", uuid.toString());
                            } else {
                                scheduler.run(player, () -> {
                                    monitorUsers.remove(uuid);
                                    startLocations.remove(uuid);
                                    startGamemodes.remove(uuid);
                                    plugin.getNetworkManager().requestPlayerTransfer(player, player.getName(), server);
                                });
                            }
                        } else {
                            scheduler.run(player, () -> {
                                stopMonitoring(player);
                            });
                        }
                        return null;
                    },
                    uuid.toString()
            );
        });
    }

    public void stopMonitoring(Player player) {
        UUID uuid = player.getUniqueId();
        monitorUsers.remove(uuid);
        Location loc = startLocations.remove(uuid);
        if (loc == null) {
            loc = player.getWorld().getSpawnLocation();
        }
        GameMode gm = startGamemodes.remove(uuid);
        if (gm == null) {
            gm = GameMode.SURVIVAL;
        }
        player.setGameMode(gm);
        player.teleportAsync(loc);
        player.sendActionBar(mm.deserialize("<yellow>Stopped monitoring."));

        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe("DELETE FROM monitor_data WHERE uuid = ?", uuid.toString());
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("monitor")) return null;
        if (args.length == 1) {
            return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[0]).stream()
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }
}

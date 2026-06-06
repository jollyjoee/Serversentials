package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, Map<String, HomeLocation>> homeCache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> teleportTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> moveCheck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();

    private record HomeLocation(String server, double x, double y, double z, float yaw, float pitch, String world) {}

    public HomeManager(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }


    // ======================================
    // Commands
    // ======================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> handleSetHome(player, args);
            case "home" -> handleHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes" -> handleListHomes(player);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    // ======================================
    // /sethome
    // ======================================
    private void handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("serversentials.home")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /sethome <name>"));
            return;
        }

        String homeName = args[0].toLowerCase(Locale.ROOT);
        int maxHomes = getHomeLimit(player);

        scheduler.runAsync(() -> {
            Map<String, HomeLocation> homes = getHomes(player);
            if (homes.size() >= maxHomes && !homes.containsKey(homeName)) {
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>You have reached your home limit (" + maxHomes + ").")));
                return;
            }

            Location loc = player.getLocation();
            String server = plugin.getConfig().getString("server-name", "unknown");

            boolean isMySQL = plugin.getDatabase().isMySQL();

            String sql;
            Object[] params;

            if (isMySQL) {
                // ✅ MySQL version
                sql = """
                INSERT INTO homes (uuid, name, world, x, y, z, yaw, pitch, server)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                world=VALUES(world),
                x=VALUES(x),
                y=VALUES(y),
                z=VALUES(z),
                yaw=VALUES(yaw),
                pitch=VALUES(pitch),
                server=VALUES(server)
            """;
                params = new Object[]{
                        player.getUniqueId().toString(), homeName,
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), server
                };
            } else {
                // ✅ SQLite version
                sql = """
                INSERT INTO homes (uuid, name, world, x, y, z, yaw, pitch, server)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, name) DO UPDATE SET
                world=excluded.world,
                x=excluded.x,
                y=excluded.y,
                z=excluded.z,
                yaw=excluded.yaw,
                pitch=excluded.pitch,
                server=excluded.server
            """;
                params = new Object[]{
                        player.getUniqueId().toString(), homeName,
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), server
                };
            }

            plugin.getDatabase().updateSafe(sql, params);

            homes.put(homeName, new HomeLocation(server, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), loc.getWorld().getName()));
            homeCache.put(player.getUniqueId(), homes);

            scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<green>Home <yellow>" + homeName + "</yellow> set!")));
        });
    }


    // ======================================
    // /home
    // ======================================
    private void handleHome(Player player, String[] args) {
        if (!player.hasPermission("serversentials.home")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        // Support admin override: /home <player> <home>
        if (args.length >= 2 && player.hasPermission("serversentials.home.others")) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
            String homeName = args[1].toLowerCase(Locale.ROOT);
            scheduler.runAsync(() -> {
                Map<String, HomeLocation> homes = getHomes(targetPlayer.getUniqueId());
                HomeLocation home = homes.get(homeName);
                if (home == null) {
                    scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Home <yellow>" + homeName + "</yellow> not found for player <yellow>" + targetPlayer.getName() + "</yellow>.")));
                    return;
                }
                String currentServer = plugin.getConfig().getString("server-name", "unknown");
                if (!currentServer.equalsIgnoreCase(home.server)) {
                    plugin.getNetworkManager().sendPluginMessage(player, "FORWARD_TO_SERVER", home.server, "TELEPORT_JOIN_REG", player.getUniqueId().toString(), home.world, home.x, home.y, home.z, home.yaw, home.pitch);
                    plugin.getNetworkManager().requestPlayerTransfer(player, player.getName(), home.server);
                    player.sendActionBar(mm.deserialize("<green>Teleporting to <yellow>" + targetPlayer.getName() + "</yellow>'s home <yellow>" + homeName + "</yellow> (cross-server)..."));
                    return;
                }
                scheduler.run(player, () -> {
                    Location loc = new Location(Bukkit.getWorld(home.world), home.x, home.y, home.z, home.yaw, home.pitch);
                    player.teleportAsync(loc).thenRun(() -> {
                        player.sendActionBar(mm.deserialize("<green>Teleported to <yellow>" + targetPlayer.getName() + "</yellow>'s home <yellow>" + homeName + "</yellow>!"));
                    });
                });
            });
            return;
        }

        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /home <name>"));
            return;
        }

        long cooldownSeconds = plugin.getConfig().getLong("modules.home.cooldown", 30);
        long now = System.currentTimeMillis();
        Long lastUsed = homeCooldowns.get(player.getUniqueId());
        if (lastUsed != null) {
            long elapsed = (now - lastUsed) / 1000;
            if (elapsed < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsed;
                player.sendActionBar(mm.deserialize("<red>You must wait <yellow>" + remaining + "s <red>before using /home again."));
                return;
            }
        }

        String homeName = args[0].toLowerCase(Locale.ROOT);
        scheduler.runAsync(() -> {
            Map<String, HomeLocation> homes = getHomes(player.getUniqueId());
            HomeLocation home = homes.get(homeName);

            if (home == null) {
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Home <yellow>" + homeName + "</yellow> not found.")));
                return;
            }

            startTeleportCountdown(player, homeName, home);
        });
    }

    // ======================================
    // Teleport Countdown w/ Movement Cancel
    // ======================================
    private void startTeleportCountdown(Player player, String homeName, HomeLocation home) {
        int countdownSeconds = getHomeCountdown(player);
        Location startLoc = player.getLocation().clone();
        moveCheck.put(player.getUniqueId(), startLoc);

        scheduler.run(player, () ->
                player.sendActionBar(mm.deserialize("<gray>Teleporting to <yellow>" + homeName + "</yellow> in <green>" + countdownSeconds + "</green> seconds..."))
        );

        // Create repeating countdown
        for (int i = 1; i <= countdownSeconds; i++) {
            int remaining = countdownSeconds - i;

            scheduler.runLater(player, () -> {
                Location current = player.getLocation();
                Location initial = moveCheck.get(player.getUniqueId());
                if (hasMoved(initial, current)) {
                    teleportTasks.remove(player.getUniqueId());
                    moveCheck.remove(player.getUniqueId());
                    player.sendActionBar(mm.deserialize("<red>Teleport cancelled because you moved!"));
                    return;
                }

                if (remaining > 0) {
                    player.sendActionBar(mm.deserialize("<gray>Teleporting in <green>" + remaining + "</green>..."));
                } else {
                    performTeleport(player, homeName, home);
                    moveCheck.remove(player.getUniqueId());
                    teleportTasks.remove(player.getUniqueId());
                }
            }, i * 20L);
        }
    }

    private void performTeleport(Player player, String homeName, HomeLocation home) {
        long now = System.currentTimeMillis();
        long lastUsed = homeCooldowns.getOrDefault(player.getUniqueId(), 0L);
        int cooldown = getHomeCooldown(player); // cooldown in seconds
        long cooldownMillis = cooldown * 1000L;

        // Check if player has cooldown bypass permission
        if (!player.hasPermission("serversentials.home.bypasscooldown")) {
            // 🔹 Check cooldown
            if ((now - lastUsed) < cooldownMillis) {
                long remaining = (cooldownMillis - (now - lastUsed)) / 1000;
                player.sendActionBar(mm.deserialize("<red>You must wait <yellow>" + remaining + "s</yellow> before teleporting again."));
                return;
            }
        }
        scheduler.run(player, () -> {
            String currentServer = plugin.getConfig().getString("server-name", "unknown");
            if (!currentServer.equalsIgnoreCase(home.server)) {
                plugin.getNetworkManager().sendPluginMessage(player, "FORWARD_TO_SERVER", home.server, "TELEPORT_JOIN_REG", player.getUniqueId().toString(), home.world, home.x, home.y, home.z, home.yaw, home.pitch);
                plugin.getNetworkManager().requestPlayerTransfer(player, player.getName(), home.server);
                player.sendActionBar(mm.deserialize("<green>Teleporting to home <yellow>" + homeName + "</yellow> (cross-server)..."));
                homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                return;
            }

            Location loc = new Location(
                    Bukkit.getWorld(home.world),
                    home.x, home.y, home.z,
                    home.yaw, home.pitch
            );
            player.teleportAsync(loc).thenRun(() -> {
                player.sendActionBar(mm.deserialize("<green>Teleported to home <yellow>" + homeName + "</yellow>!"));
                homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            });
        });
    }


    private boolean hasMoved(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.distanceSquared(to) > 0.1;
                //|| Math.abs(from.getYaw() - to.getYaw()) > 5
               //|| Math.abs(from.getPitch() - to.getPitch()) > 5;
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /delhome <name>"));
            return;
        }

        String homeName = args[0].toLowerCase(Locale.ROOT);

        scheduler.runAsync(() -> {
            Map<String, HomeLocation> homes = getHomes(player);

            if (!homes.containsKey(homeName)) {
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Home <yellow>" + homeName + "</yellow> does not exist.")));
                return;
            }

            plugin.getDatabase().updateSafe("DELETE FROM homes WHERE uuid=? AND name=?", player.getUniqueId().toString(), homeName);

            homes.remove(homeName);
            homeCache.put(player.getUniqueId(), homes);

            scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<green>Home <yellow>" + homeName + "</yellow> deleted.")));
        });
    }

    private void handleListHomes(Player player) {
        scheduler.runAsync(() -> {
            Map<String, HomeLocation> homes = getHomes(player);

            if (homes.isEmpty()) {
                scheduler.run(player, () -> player.sendMessage(mm.deserialize("<gray>You have no homes set.")));
                return;
            }

            StringBuilder list = new StringBuilder();
            for (Map.Entry<String, HomeLocation> entry : homes.entrySet()) {
                String name = entry.getKey();
                HomeLocation loc = entry.getValue();
                list.append("<yellow>")
                        .append(name)
                        .append("</yellow> - <gray>")
                        //.append(loc.world()).append(" ")
                        .append(String.format("(x: %.1f, z: %.1f) || ", loc.x(), loc.z()));
            }
            scheduler.run(player, () -> player.sendActionBar(mm.deserialize(list.toString())));
        });
    }



    // ======================================
    // Permission-Based Home Limit
    // ======================================
    private int getHomeLimit(Player player) {
        int result = 1;
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String p = perm.getPermission();
            if (p.startsWith("serversentials.sethome.")) {
                String[] parts = p.split("\\.");
                if (parts.length >= 3) {
                    String part = parts[2];
                    try {
                        int value = Integer.parseInt(part);
                        result = Math.max(result, value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return result;
    }

    private int getHomeCooldown(Player player) {
        int result = Integer.MAX_VALUE;
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String p = perm.getPermission();
            if (p.startsWith("serversentials.home.cooldown.")) {
                String[] parts = p.split("\\.");
                if (parts.length >= 4) {
                    try {
                        int value = Integer.parseInt(parts[3]);
                        result = Math.min(result, value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (result == Integer.MAX_VALUE) {
            result = 5;
        }
        return result;
    }

    private int getHomeCountdown(Player player) {
        int result = Integer.MAX_VALUE;
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            String p = perm.getPermission();
            if (p.startsWith("serversentials.home.countdown.")) {
                String[] parts = p.split("\\.");
                if (parts.length >= 4) {
                    try {
                        int value = Integer.parseInt(parts[3]);
                        result = Math.min(result, value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (result == Integer.MAX_VALUE) {
            result = 5;
        }
        return result;
    }


    // ======================================
    // DB Helpers
    // ======================================
    private Map<String, HomeLocation> getHomes(Player player) {
        return getHomes(player.getUniqueId());
    }

    private Map<String, HomeLocation> getHomes(UUID uuid) {
        return homeCache.computeIfAbsent(uuid, id -> {
            Map<String, HomeLocation> map = new HashMap<>();
            plugin.getDatabase().querySafe(
                    "SELECT name, world, x, y, z, yaw, pitch, server FROM homes WHERE uuid=?",
                    rs -> {
                        while (rs.next()) {
                            map.put(
                                    rs.getString("name").toLowerCase(Locale.ROOT),
                                    new HomeLocation(
                                            rs.getString("server"),
                                            rs.getDouble("x"),
                                            rs.getDouble("y"),
                                            rs.getDouble("z"),
                                            rs.getFloat("yaw"),
                                            rs.getFloat("pitch"),
                                            rs.getString("world")
                                    )
                            );
                        }
                        return null;
                    },
                    id.toString()
            );
            return map;
        });
    }

    // ======================================
    // Tab Completion
    // ======================================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (command.getName().equalsIgnoreCase("home") && args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            Map<String, HomeLocation> homes = homeCache.getOrDefault(player.getUniqueId(), Map.of());
            return homes.keySet().stream()
                    .filter(name -> name.startsWith(partial))
                    .sorted()
                    .toList();
        }

        return List.of();
    }
}

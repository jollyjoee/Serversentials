package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WarpManager
 *
 * Commands:
 *  - /warp <name>
 *  - /setwarp <name>
 *  - /delwarp <name>
 *  - /warps
 *
 * Permissions:
 *  - serversentials.warp                 (general warp permission)
 *  - serversentials.warp.<warpname>      (per-warp permission)
 *  - serversentials.setwarp              (create/edit a warp)
 *
 * Permission-based cooldowns/countdowns:
 *  - serversentials.warp.cooldown.<#>    (highest number wins)
 *  - serversentials.warp.countdown.<#>   (lowest number wins)
 *
 * DB tables used:
 *  - warps (name PRIMARY KEY, world, x, y, z, yaw, pitch, server)
 *  - warp_cooldowns (uuid, warp, last_used) unique on (uuid, warp)
 */
public class WarpManager implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // in-memory caches / trackers
    private final Map<String, Warp> warpCache = new ConcurrentHashMap<>(); // name -> warp
    private final Map<UUID, Object> teleportTasks = new ConcurrentHashMap<>(); // for cancellations if needed

    // Data holder
    private static record Warp(String name, String world, double x, double y, double z, float yaw, float pitch, String server) {}

    // defaults
    private long defaultCountdown; // ticks = seconds * 20 when scheduled, but store in seconds here
    private long defaultCooldown; // seconds

    public WarpManager(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        reload();

        createTables();
        loadAllWarpsAsync();
    }

    public void reload() {
        this.defaultCountdown = plugin.getConfig().getLong("modules.warp.default-countdown", 5L);
        this.defaultCooldown = plugin.getConfig().getLong("modules.warp.default-cooldown", 0L);
    }

    // -----------------------
    // Command wiring
    // -----------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!plugin.isModuleEnabled("warp")) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "warp" -> handleWarp(player, args);
            case "setwarp" -> handleSetWarp(player, args);
            case "delwarp" -> handleDelWarp(player, args);
            case "warps" -> handleListWarps(player, args);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    // -----------------------
    // Tab completion
    // -----------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1 && (command.getName().equalsIgnoreCase("warp") || command.getName().equalsIgnoreCase("delwarp"))) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String name : warpCache.keySet()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(partial)) out.add(name);
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }

    // -----------------------
    // Handlers
    // -----------------------
    private void handleListWarps(Player player, String[] args) {
        if (!player.hasPermission("serversentials.warp")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (warpCache.isEmpty()) {
            player.sendActionBar(mm.deserialize("<gray>No warps set."));
            return;
        }
        StringBuilder sb = new StringBuilder("<aqua>Warps: ");
        boolean first = true;
        for (String name : warpCache.keySet()) {
            if (!first) sb.append("<gray>, ");
            sb.append("<yellow>").append(name);
            first = false;
        }
        player.sendMessage(mm.deserialize(sb.toString()));
    }

    private void handleSetWarp(Player player, String[] args) {
        if (!player.hasPermission("serversentials.setwarp")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /setwarp <name>"));
            return;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        Location loc = player.getLocation();
        String server = plugin.getConfig().getString("server-name", "unknown");

        // Save async
        scheduler.runAsync(() -> {
            boolean isMySQL = plugin.getDatabase().isMySQL();
            if (isMySQL) {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO warps (name, world, x, y, z, yaw, pitch, server) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), server=VALUES(server)",
                        name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), server
                );
            } else {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO warps (name, world, x, y, z, yaw, pitch, server) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT(name) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch, server=excluded.server",
                        name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), server
                );
            }

            warpCache.put(name, new Warp(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), server));
            scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<green>Warp <yellow>" + name + "</yellow> has been set.")));
        });
    }

    private void handleDelWarp(Player player, String[] args) {
        if (!player.hasPermission("serversentials.setwarp")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /delwarp <name>"));
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe("DELETE FROM warps WHERE name = ?", name);
            warpCache.remove(name);
            scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<green>Warp <yellow>" + name + "</yellow> deleted.")));
        });
    }

    private void handleWarp(Player player, String[] args) {
        if (args.length == 0) {
            player.sendActionBar(mm.deserialize("<red>Usage: /warp <name>"));
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);

        // permission check: either general warp or specific warp permission
        if (!(player.hasPermission("serversentials.warp") || player.hasPermission("serversentials.warp." + name))) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        // load warp (from cache if available)
        Warp warp = warpCache.get(name);
        if (warp == null) {
            // try load from DB async then continue
            scheduler.runAsync(() -> {
                Warp loaded = plugin.getDatabase().querySafe(
                        "SELECT world, x, y, z, yaw, pitch, server FROM warps WHERE name = ?",
                        rs -> {
                            if (rs.next()) {
                                return new Warp(name,
                                        rs.getString("world"),
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        rs.getFloat("yaw"),
                                        rs.getFloat("pitch"),
                                        rs.getString("server")
                                );
                            }
                            return null;
                        },
                        name
                );
                if (loaded == null) {
                    scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Warp not found.")));
                    return;
                }
                warpCache.put(name, loaded);
                // continue on main thread logic by calling the warp routine
                scheduler.run(player, () -> beginWarpProcess(player, name, loaded));
                return;
            });
        } else {
            beginWarpProcess(player, name, warp);
        }
    }

    // -----------------------
    // Warp process: cooldown, countdown, movement-cancel, teleport & persist last-used
    // -----------------------
    private void beginWarpProcess(Player player, String warpName, Warp warp) {
        String currentServer = plugin.getConfig().getString("server-name", "unknown");
        if (!currentServer.equalsIgnoreCase(warp.server)) {
            player.sendActionBar(mm.deserialize("<red>This warp is set on another server: <yellow>" + warp.server));
            return;
        }

        UUID uuid = player.getUniqueId();

        // 1) get cooldown in seconds (permission-based)
        int cooldownSeconds = getNumericPermissionValue(player, "serversentials.warp.cooldown.", (int) defaultCooldown, /*chooseLower=*/ false, Integer.MAX_VALUE);

        // 2) check last used for this player+warp from DB (async)
        scheduler.runAsync(() -> {
            Long lastUsed = plugin.getDatabase().querySafe(
                    "SELECT last_used FROM warp_cooldowns WHERE uuid = ? AND warp = ?",
                    rs -> rs.next() ? rs.getLong("last_used") : null,
                    uuid.toString(), warpName
            );

            long now = System.currentTimeMillis() / 1000L; // seconds
            if (lastUsed != null && (now - lastUsed) < cooldownSeconds) {
                long remaining = cooldownSeconds - (now - lastUsed);
                scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Warp cooldown: <yellow>" + remaining + "s")));
                return;
            }

            // 3) get countdown seconds
            int countdown = getNumericPermissionValue(player, "serversentials.warp.countdown.", (int) defaultCountdown, /*chooseLower=*/ true, 0);

            // If countdown is 0 -> teleport immediately (still persist last_used)
            if (countdown <= 0) {
                // perform teleport right away on main thread
                scheduler.run(player, () -> {
                    performWarpTeleport(player, warpName, warp);
                });
                return;
            }

            // 4) start countdown (with movement cancel)
            Location startLoc = player.getLocation().clone();
            scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<gray>Warping to <yellow>" + warpName + "</yellow> in <green>" + countdown + "</green> seconds...")));

            // schedule each second notification and final teleport
            for (int i = 1; i <= countdown; i++) {
                int remaining = countdown - i;
                scheduler.runLater(player, () -> {
                    // check movement
                    Location nowLoc = player.getLocation();
                    if (hasMovedSignificantly(startLoc, nowLoc)) {
                        // canceled
                        scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<red>Warp cancelled because you moved!")));
                        teleportTasks.remove(uuid);
                        return;
                    }
                    if (remaining > 0) {
                        scheduler.run(player, () -> player.sendActionBar(mm.deserialize("<gray>Warping in <green>" + remaining + "</green>...")));
                    } else {
                        // final teleport
                        performWarpTeleport(player, warpName, warp);
                        teleportTasks.remove(uuid);
                    }
                }, i * 20L);
            }

            return;
        });
    }

    private boolean hasMovedSignificantly(Location a, Location b) {
        if (a == null || b == null) return true;
        if (!Objects.equals(a.getWorld(), b.getWorld())) return true;
        // squared distance small threshold (0.09 = 0.3 blocks)
        return a.distanceSquared(b) > 0.09;
    }

    private void performWarpTeleport(Player player, String warpName, Warp warp) {
        // build Location (main thread)
        Location loc = new Location(Bukkit.getWorld(warp.world), warp.x, warp.y, warp.z, warp.yaw, warp.pitch);
        player.teleportAsync(loc);
        player.sendActionBar(mm.deserialize("<green>Warped to <yellow>" + warpName + "</yellow>"));
        // persist last_used
        long now = System.currentTimeMillis() / 1000L;
        scheduler.runAsync(() -> {
            boolean isMySQL = plugin.getDatabase().isMySQL();
            if (isMySQL) {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO warp_cooldowns (uuid, warp, last_used) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE last_used = VALUES(last_used)",
                        player.getUniqueId().toString(), warpName, now
                );
            } else {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO warp_cooldowns (uuid, warp, last_used) VALUES (?, ?, ?) ON CONFLICT(uuid, warp) DO UPDATE SET last_used = excluded.last_used",
                        player.getUniqueId().toString(), warpName, now
                );
            }
        });
    }

    // -----------------------
    // Database helpers
    // -----------------------
    private void createTables() {
        // create warps table
        // Use generic types; both MySQL & SQLite accept these forms (VARCHAR, DOUBLE, etc.)
        plugin.getDatabase().updateSafe("""
                CREATE TABLE IF NOT EXISTS warps (
                    name VARCHAR(64) PRIMARY KEY,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    server VARCHAR(64) NOT NULL
                )
                """);

        // create warp cooldowns (uuid + warp composite unique)
        plugin.getDatabase().updateSafe("""
                CREATE TABLE IF NOT EXISTS warp_cooldowns (
                    uuid VARCHAR(36) NOT NULL,
                    warp VARCHAR(64) NOT NULL,
                    last_used BIGINT NOT NULL,
                    PRIMARY KEY (uuid, warp)
                )
                """);
    }

    private void loadAllWarpsAsync() {
        scheduler.runAsync(() -> {
            plugin.getDatabase().querySafe(
                    "SELECT name, world, x, y, z, yaw, pitch, server FROM warps",
                    rs -> {
                        while (rs.next()) {
                            String name = rs.getString("name");
                            warpCache.put(name, new Warp(
                                    name,
                                    rs.getString("world"),
                                    rs.getDouble("x"),
                                    rs.getDouble("y"),
                                    rs.getDouble("z"),
                                    rs.getFloat("yaw"),
                                    rs.getFloat("pitch"),
                                    rs.getString("server")
                            ));
                        }
                        return null;
                    }
            );
        });
    }

    // -----------------------
    // Permission helpers
    // -----------------------
    /**
     * Generic permission parser.
     *
     * base: permission prefix, e.g. "serversentials.warp.cooldown."
     * defaultValue: fallback integer
     * chooseLower: if true, choose the lowest numeric permission; if false, choose the highest
     * maxValueIfInfinite: value to return when a permission ends with "infinite"
     */
    private int getNumericPermissionValue(Player player, String base, int defaultValue, boolean chooseLower, int maxValueIfInfinite) {
        int result = chooseLower ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        boolean found = false;

        for (var permInfo : player.getEffectivePermissions()) {
            String p = permInfo.getPermission();
            if (p == null) continue;
            if (!p.startsWith(base)) continue;

            String[] parts = p.split("\\.");
            String last = parts[parts.length - 1];
            if (last.equalsIgnoreCase("infinite")) {
                return maxValueIfInfinite;
            }
            try {
                int v = Integer.parseInt(last);
                if (!found) {
                    result = v;
                    found = true;
                } else {
                    result = chooseLower ? Math.min(result, v) : Math.max(result, v);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!found) return defaultValue;
        return result;
    }
}

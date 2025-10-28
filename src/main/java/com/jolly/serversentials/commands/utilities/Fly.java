package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Fly implements CommandExecutor, TabCompleter {
    private final Scheduler scheduler;
    private final Serversentials plugin;
    private final Set<UUID> flying = new HashSet<>();

    public Fly(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    public Set<UUID> getFlying() {
        return flying;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!command.getName().equalsIgnoreCase("fly")) return false;

        // Check permission
        if (!player.hasPermission("serversentials.fly")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Handle /fly <player>
        if (args.length == 1) {
            if (!player.hasPermission("serversentials.fly.others")) {
                player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendActionBar(plugin.mm("<red>Player not found!"));
                return true;
            }

            toggleFly(target, true);
            player.sendActionBar(plugin.mm("<gold>Toggled fly for <gray>" + target.getName()));
            return true;
        }

        // Handle /fly (self)
        toggleFly(player, false);
        return true;
    }

    /**
     * Toggles flying state and saves it to the database.
     */
    private void toggleFly(Player player, boolean isTarget) {
        UUID uuid = player.getUniqueId();
        boolean currentlyFlying = flying.contains(uuid);

        if (currentlyFlying) {
            flying.remove(uuid);
            player.setAllowFlight(false);
            player.sendActionBar(plugin.mm("<gray>Disabled flying"));
            saveFlyStateAsync(uuid, false);
        } else {
            flying.add(uuid);
            player.setAllowFlight(true);
            player.sendActionBar(plugin.mm("<gold>Enabled flying"));
            saveFlyStateAsync(uuid, true);
        }
    }

    public boolean isFlying(Player player) {
        return flying.contains(player.getUniqueId());
    }

    /**
     * Saves a player's flying state asynchronously to the database.
     */
    private void saveFlyStateAsync(UUID uuid, boolean flying) {
        plugin.getDatabase().updateSafeAsync("""
            INSERT INTO fly_data (uuid, flying)
            VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET flying = excluded.flying
        """, uuid.toString(), flying);
    }

    /**
     * Loads flying state asynchronously on join.
     */
    public CompletableFuture<Void> loadFlyStateAsync(Player player) {
        Bukkit.getLogger().info("[Serversentials] Querying fly state for " + player.getName());
        UUID uuid = player.getUniqueId();

        return plugin.getDatabase().querySafeAsync(
                "SELECT flying FROM fly_data WHERE uuid = ?",
                rs -> {
                    Bukkit.getLogger().info("[Serversentials] Result handler called for " + player.getName());
                    if (!rs.next()) {
                        Bukkit.getLogger().info("[Serversentials] No DB record found for " + player.getName());
                        return null;
                    }

                    boolean isFlying = rs.getBoolean("flying");
                    Bukkit.getLogger().info("[Serversentials] Flying value in DB: " + isFlying);

                    if (isFlying) {
                        scheduler.run(player, () -> {
                            Bukkit.getLogger().info("[Serversentials] Setting flight for " + player.getName());
                            this.flying.add(uuid);
                            player.setAllowFlight(true);
                            boolean onAir = player.getLocation()
                                    .clone()
                                    .subtract(0, 0.1, 0)
                                    .getBlock()
                                    .isPassable();
                            if (onAir) {
                                player.setFlying(true);
                            }
                        });
                    }
                    return null;
                },
                uuid.toString()
        );
    }

    /**
     * Tab completion for /fly <player>
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("fly")) return null;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}

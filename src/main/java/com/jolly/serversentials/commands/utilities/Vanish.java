package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class Vanish implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> vanished = new HashSet<>();

    public Vanish(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length == 1) {
            handleVanishOthers(player, args[0]);
        } else {
            handleVanish(player);
        }
        return true;
    }

    // ===================================================
    // 🔹 Toggle self vanish
    // ===================================================
    private void handleVanish(Player player) {
        if (!player.hasPermission("serversentials.vanish")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        if (!vanished.contains(player.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    if (!other.hasPermission("serversentials.vanish.see")) {
                        other.hidePlayer(plugin, player);
                    }
                }
            }
            vanished.add(player.getUniqueId());
            player.sendActionBar(mm.deserialize("<gray>You have <green>vanished</green>."));
            addOrUpdateVanishStatus(player, true);
        } else {
            // Unvanish
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) other.showPlayer(plugin, player);
            }
            vanished.remove(player.getUniqueId());
            player.sendActionBar(mm.deserialize("<gray>You are now <red>visible</red>."));
            addOrUpdateVanishStatus(player, false);
        }
    }

    // ===================================================
    // 🔹 Toggle vanish for another player
    // ===================================================
    private void handleVanishOthers(Player player, String targetName) {
        if (!player.hasPermission("serversentials.vanish.others")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.player-not-found")));
            return;
        }

        UUID uuid = target.getUniqueId();

        if (!vanished.contains(uuid)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(target)) {
                    if (!other.hasPermission("serversentials.vanish.see")) {
                        other.hidePlayer(plugin, target);
                    }
                }
            }
            vanished.add(uuid);
            player.sendActionBar(mm.deserialize("<yellow>" + target.getName() + "</yellow> is now <green>vanished</green>."));
            target.sendActionBar(mm.deserialize("<gray>You have been vanished by <yellow>" + player.getName() + "</yellow>."));
            addOrUpdateVanishStatus(target, true);
        } else {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(target)) other.showPlayer(plugin, target);
            }
            vanished.remove(uuid);
            player.sendActionBar(mm.deserialize("<yellow>" + target.getName() + "</yellow> is now <red>visible</red>."));
            target.sendActionBar(mm.deserialize("<gray>You have been unvanished by <yellow>" + player.getName() + "</yellow>."));
            addOrUpdateVanishStatus(target, false);
        }
    }

    // ===================================================
    // 🔹 Cross-compatible vanish status updater
    // ===================================================
    private void addOrUpdateVanishStatus(Player player, boolean vanishedStatus) {
        scheduler.runAsync(() -> {
            String uuid = player.getUniqueId().toString();
            boolean isMySQL = plugin.getDatabase().isMySQL();

            String query;
            if (isMySQL) {
                query = """
                    INSERT INTO vanish_data (uuid, status)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status)
                """;
            } else {
                query = "INSERT OR REPLACE INTO vanish_data (uuid, status) VALUES (?, ?)";
            }

            plugin.getDatabase().updateSafe(query, uuid, vanishedStatus);
        });
    }

    public void loadVanishStatus(Player player) {
        UUID uuid = player.getUniqueId();

        scheduler.runAsync(() -> {
            Boolean vanishedStatus = plugin.getDatabase().querySafe(
                    "SELECT status FROM vanish_data WHERE uuid = ?",
                    rs -> rs.next() && rs.getBoolean("status"),
                    uuid.toString()
            );

            // Default to false if null
            if (vanishedStatus == null) vanishedStatus = false;

            boolean finalStatus = vanishedStatus;
            if (finalStatus) {
                vanished.add(uuid);
            } else {
                vanished.remove(uuid);
            }

            // Delay slightly to ensure all players are fully loaded
            scheduler.runLater(player, () -> {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(player)) continue;

                    Player target = player;
                    Player observer = other;

                    scheduler.run(observer, () -> {
                        if (finalStatus) {
                            if (!observer.hasPermission("serversentials.vanish.see")) {
                                observer.hidePlayer(plugin, target);
                            }
                        } else {
                            observer.showPlayer(plugin, target);
                        }
                    });
                }
            }, 40L); // 2 seconds delay
        });
    }



    public Boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }
    // ===================================================
    // 🔹 Tab completion
    // ===================================================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("vanish")) return null;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}

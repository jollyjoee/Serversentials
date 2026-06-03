package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;


import java.util.*;

public class Hide implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public Hide(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;

        // ✅ Create cross-compatible table
        scheduler.runAsync(() -> plugin.getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS hide_data (
                uuid VARCHAR(36) PRIMARY KEY,
                hidden BOOLEAN NOT NULL DEFAULT 0,
                previous_name TEXT,
                previous_team TEXT
            )
        """));
    }

    // ================================
    // 🔹 Command handling
    // ================================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!player.hasPermission("serversentials.hide")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        // /hide
        if (args.length == 0) {
            toggleHide(player, player);
            return true;
        }

        // /hide <target>
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return true;
            }

            if (!player.hasPermission("serversentials.hide.others")) {
                player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }

            toggleHide(player, target);
            return true;
        }

        // /hide <target> off
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return true;
            }

            if (!player.hasPermission("serversentials.hide.others")) {
                player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }

            unhide(target);
            player.sendActionBar(mm.deserialize("<yellow>You have unhidden <white>" + target.getName() + "</white>."));
            return true;
        }

        return true;
    }

    // ================================
    // 🔹 Toggle hide
    // ================================
    private void toggleHide(Player executor, Player target) {
        UUID uuid = target.getUniqueId();

        scheduler.runAsync(() -> {
            Boolean currentlyHidden = plugin.getDatabase().querySafe(
                    "SELECT hidden FROM hide_data WHERE uuid = ?",
                    rs -> rs.next() && rs.getBoolean("hidden"),
                    uuid.toString()
            );

            if (Boolean.TRUE.equals(currentlyHidden)) {
                scheduler.run(target, () -> unhide(target));
                executor.sendActionBar(mm.deserialize("<yellow>" + target.getName() + " is now visible again."));
                plugin.getDatabase().updateSafe("UPDATE hide_data SET hidden = 0 WHERE uuid = ?", uuid.toString());
            } else {
                // Store the player's current nickname
                String serializedCurrent = mm.serialize(
                        target.displayName() != null
                                ? target.displayName()
                                : Component.text(target.getName())
                );

                scheduler.run(target, () -> {
                    // Obfuscate the name
                    Component obfuscated = mm.deserialize("<obf>" + target.getName() + "</obf>");
                    applyNickname(target, obfuscated);

                    // Hide nametag
                    hideNametag(target);
                });

                // Save old scoreboard team
                String oldTeam = getPlayerCurrentTeam(target);
                plugin.getDatabase().updateSafe(
                    "REPLACE INTO hide_data (uuid, hidden, previous_name, previous_team) VALUES (?, ?, ?, ?)",
                    uuid.toString(), true, serializedCurrent, oldTeam
                );

                hiddenPlayers.add(uuid);
                executor.sendActionBar(mm.deserialize("<green>" + target.getName() + " is now hidden!"));
            }
        });
    }

    // ================================
    // 🔹 Unhide player
    // ================================
    private void unhide(Player player) {
        UUID uuid = player.getUniqueId();

        Map<String, String> result = plugin.getDatabase().querySafe(
                "SELECT previous_name, previous_team FROM hide_data WHERE uuid = ?",
                rs -> {
                    if (rs.next()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("previous_name", rs.getString("previous_name"));
                        map.put("previous_team", rs.getString("previous_team"));
                        return map;
                    }
                    return null;
                },
                uuid.toString()
        );

        scheduler.run(player, () -> {
            if (result != null) {
                String prevName = result.get("previous_name");
                String prevTeam = result.get("previous_team");

                // Restore display name
                if (prevName != null && !prevName.isEmpty()) {
                    Component restored = mm.deserialize(prevName);
                    player.displayName(restored);
                    player.playerListName(restored);
                } else {
                    player.displayName(Component.text(player.getName()));
                    player.playerListName(Component.text(player.getName()));
                }

                // Restore old team
                restoreNametag(player, prevTeam);
            }

            player.sendActionBar(mm.deserialize("<yellow>Your name is now visible."));
        });

        hiddenPlayers.remove(uuid);
        plugin.getDatabase().updateSafe("UPDATE hide_data SET hidden = 0 WHERE uuid = ?", uuid.toString());
    }

    // ================================
    // 🔹 Hide nametag via scoreboard
    // ================================
    private void hideNametag(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("serversentials_hidden");
        if (team == null) {
            team = board.registerNewTeam("serversentials_hidden");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
    }

    // ================================
    // 🔹 Restore nametag
    // ================================
    private void restoreNametag(Player player, String previousTeam) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team hiddenTeam = board.getTeam("serversentials_hidden");
        if (hiddenTeam != null) hiddenTeam.removeEntry(player.getName());

        if (previousTeam != null && !previousTeam.isEmpty() && board.getTeam(previousTeam) != null) {
            board.getTeam(previousTeam).addEntry(player.getName());
        }
    }

    // ================================
    // 🔹 Get current team name
    // ================================
    private String getPlayerCurrentTeam(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getEntryTeam(player.getName());
        return team != null ? team.getName() : null;
    }

    // ================================
    // 🔹 Load hidden status on join
    // ================================
    public void loadHiddenStatus(Player player) {
        UUID uuid = player.getUniqueId();

        scheduler.runAsync(() -> {
            plugin.getDatabase().querySafe(
                    "SELECT hidden, previous_name, previous_team FROM hide_data WHERE uuid = ?",
                    rs -> {
                        if (rs.next()) {
                            boolean isHidden = rs.getBoolean("hidden");
                            String previousName = rs.getString("previous_name");
                            String previousTeam = rs.getString("previous_team");

                            if (isHidden) hiddenPlayers.add(uuid);
                            else hiddenPlayers.remove(uuid);

                            scheduler.run(player, () -> {
                                if (isHidden) {
                                    Component obfuscated = mm.deserialize("<obf>" + player.getName() + "</obf>");
                                    applyNickname(player, obfuscated);
                                    hideNametag(player);
                                } else if (previousName != null && !previousName.isEmpty()) {
                                    Component restored = mm.deserialize(previousName);
                                    applyNickname(player, restored);
                                    restoreNametag(player, previousTeam);
                                }
                            });
                        }
                        return null;
                    },
                    uuid.toString()
            );
        });
    }

    // ================================
    // 🔹 State check
    // ================================
    public boolean isHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    // ================================
    // 🔹 Utility
    // ================================
    private void applyNickname(Player player, Component nickname) {
        player.displayName(nickname);
        player.playerListName(nickname);
    }

    // ================================
    // 🔹 Tab completion
    // ================================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("hide")) return null;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
        } else if (args.length == 2) {
            completions.add("off");
        }

        return completions;
    }
}

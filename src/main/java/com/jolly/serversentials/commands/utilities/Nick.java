package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Nick implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public Nick(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!plugin.isModuleEnabled("nick.enabled")) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (!player.hasPermission("serversentials.nick")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }
        int maxNickLength = plugin.getConfig().getInt("modules.nick.maxlength");
        // /nick  -> reset own nickname
        if (args.length == 0) {
            resetNickname(player);
            player.sendActionBar(mm.deserialize("<gold>Nickname reset"));
            return true;
        }

        // /nick reset -> reset own nickname
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            resetNickname(player);
            player.sendActionBar(mm.deserialize("<gold>Nickname reset"));
            return true;
        }

        // /nick <name> -> change own nickname
        if (args.length == 1) {
            String newNick = args[0];
            if (newNick.length() > maxNickLength) {
                player.sendActionBar(mm.deserialize("<red>Nickname must be " + maxNickLength + " characters or less!"));
                return true;
            }

            Component displayName = mm.deserialize(newNick);

            scheduler.runAsync(() -> {
                plugin.getDatabase().updateSafe(
                        "REPLACE INTO nick_data (uuid, name, nickname) VALUES (?, ?, ?)",
                        player.getUniqueId().toString(),
                        player.getName(),
                        newNick
                );

                scheduler.run(player, () -> applyNickname(player, displayName));
                plugin.getNetworkManager().broadcastNickSync(player.getName(), newNick);
            });

            player.sendActionBar(mm.deserialize("<green>Your nickname has been changed to <white>" + newNick));
            return true;
        }

        // /nick reset <player> -> reset another player's nickname
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            if (!player.hasPermission("serversentials.nick.others")) {
                player.sendActionBar(mm.deserialize("<red>You don't have permission to change others' nicknames!"));
                return true;
            }

            String targetName = args[1];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                resetNickname(target);
                player.sendActionBar(mm.deserialize("<green>You reset <white>" + target.getName() + "'s <green>nickname."));
            } else {
                UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                scheduler.runAsync(() -> {
                    plugin.getDatabase().updateSafe(
                            "DELETE FROM nick_data WHERE uuid = ?",
                            targetUuid.toString()
                    );
                    plugin.getNetworkManager().broadcastNickSync(targetName, targetName);
                    if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                        plugin.getNetworkManager().forwardToPlayer(player, targetName, "NICK_REFRESH", targetName);
                    }
                });
                player.sendActionBar(mm.deserialize("<green>You reset <white>" + targetName + "'s <green>nickname."));
            }
            return true;
        }

        // /nick <name> <player> -> change another player’s nickname
        if (args.length >= 2) {
            if (!player.hasPermission("serversentials.nick.others")) {
                player.sendActionBar(mm.deserialize("<red>You don't have permission to change others' nicknames!"));
                return true;
            }

            String newNick = args[0];
            String targetName = args[1];

            if (newNick.length() > maxNickLength) {
                player.sendActionBar(mm.deserialize("<red>Nickname must be " + maxNickLength + " characters or less!"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                Component displayName = mm.deserialize(newNick);
                scheduler.runAsync(() -> {
                    plugin.getDatabase().updateSafe(
                            "REPLACE INTO nick_data (uuid, name, nickname) VALUES (?, ?, ?)",
                            target.getUniqueId().toString(),
                            target.getName(),
                            newNick
                    );
                    scheduler.run(target, () -> applyNickname(target, displayName));
                    plugin.getNetworkManager().broadcastNickSync(target.getName(), newNick);
                });
                player.sendActionBar(mm.deserialize("<green>You changed <white>" + target.getName() + "'s <green>nickname to <white>" + newNick));
                target.sendActionBar(mm.deserialize("<yellow>Your nickname has been changed to <white>" + newNick + "<yellow> by " + player.getName()));
            } else {
                UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                scheduler.runAsync(() -> {
                    plugin.getDatabase().updateSafe(
                            "REPLACE INTO nick_data (uuid, name, nickname) VALUES (?, ?, ?)",
                            targetUuid.toString(),
                            targetName,
                            newNick
                    );
                    plugin.getNetworkManager().broadcastNickSync(targetName, newNick);
                    if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                        plugin.getNetworkManager().forwardToPlayer(player, targetName, "NICK_REFRESH", targetName);
                    }
                });
                player.sendActionBar(mm.deserialize("<green>You changed <white>" + targetName + "'s <green>nickname to <white>" + newNick));
            }
            return true;
        }

        return true;
    }


    private void resetNickname(Player player) {
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "DELETE FROM nick_data WHERE uuid = ?",
                    player.getUniqueId().toString()
            );

            scheduler.run(player, () -> {
                player.displayName(Component.text(player.getName()));
                player.playerListName(Component.text(player.getName()));
                player.sendMessage(mm.deserialize("<yellow>Your nickname has been reset."));
            });
            plugin.getNetworkManager().broadcastNickSync(player.getName(), player.getName());
        });
    }

    public String getNick(Player player) {
        UUID uuid = player.getUniqueId();
        return plugin.getDatabase().querySafe(
                "SELECT nickname FROM nick_data WHERE uuid = ?",
                rs -> {
                    if (rs.next()) {
                        String nickname = rs.getString("nickname");
                        if (nickname != null && !nickname.isEmpty()) {
                            return nickname;
                        }
                    }
                    return null;
                },
                uuid.toString()
        );
    }

    public void loadNicknameAsync(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            plugin.getDatabase().querySafe(
                    "SELECT nickname FROM nick_data WHERE uuid = ?",
                    rs -> {
                        if (rs.next()) {
                            String nickname = rs.getString("nickname");
                            if (nickname != null && !nickname.isEmpty()) {
                                scheduler.run(player, () ->
                                        applyNickname(player, mm.deserialize(nickname))
                                );
                                plugin.getNetworkManager().broadcastNickSync(player.getName(), nickname);
                                return null;
                            }
                        }
                        // Reset to vanilla if no entry in DB
                        scheduler.run(player, () -> {
                            player.displayName(Component.text(player.getName()));
                            player.playerListName(Component.text(player.getName()));
                        });
                        plugin.getNetworkManager().broadcastNickSync(player.getName(), player.getName());
                        return null;
                    },
                    uuid.toString()
            );
        });
    }

    private void applyNickname(Player player, Component nickname) {
        player.displayName(nickname);
        player.playerListName(nickname);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("nick")) return null;
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reset");
        }
        if (args.length == 2) {
            return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[1]);
        }
        return completions;
    }

}

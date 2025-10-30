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
                player.sendActionBar(mm.deserialize("<red>Nickname must be"+ maxNickLength + "characters or less!"));
                return true;
            }

            Component displayName = mm.deserialize(newNick);

            scheduler.runAsync(() -> {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO nick_data (uuid, nickname) VALUES (?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET nickname = ?",
                        player.getUniqueId().toString(),
                        newNick,
                        newNick
                );

                scheduler.run(player, () -> applyNickname(player, displayName));
            });

            player.sendActionBar(mm.deserialize("<green>Your nickname has been changed to <white>" + newNick));
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
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return true;
            }

            if (newNick.length() > maxNickLength) {
                player.sendActionBar(mm.deserialize("<red>Nickname must be" + maxNickLength + "characters or less!"));
                return true;
            }
            Component displayName = mm.deserialize(newNick);
            scheduler.runAsync(() -> {
                plugin.getDatabase().updateSafe(
                        "INSERT INTO nick_data (uuid, nickname) VALUES (?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET nickname = ?",
                        target.getUniqueId().toString(),
                        newNick,
                        newNick
                );
                scheduler.run(target, () -> applyNickname(target, displayName));
            });
            player.sendActionBar(mm.deserialize("<green>You changed <white>" + target.getName() + "'s <green>nickname to <white>" + newNick));
            target.sendActionBar(mm.deserialize("<yellow>Your nickname has been changed to <white>" + newNick + "<yellow> by " + player.getName()));
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
                            }
                        }
                        return null;
                    },
                    uuid.toString()
            );
        });
    }

    private void applyNickname(Player player, Component nickname) {
        player.displayName(nickname);
        player.playerListName(nickname);
        //Bukkit.getLogger().info("[Serversentials] Applied nickname for " + player.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("nick")) return null;
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reset");
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

}

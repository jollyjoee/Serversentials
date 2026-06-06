package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MsgCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public static final Set<UUID> socialSpyUsers = ConcurrentHashMap.newKeySet();

    public MsgCommand(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        String cmdName = command.getName().toLowerCase(Locale.ROOT);

        if (cmdName.equals("socialspy")) {
            if (!player.hasPermission("serversentials.socialspy")) {
                player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            UUID uuid = player.getUniqueId();
            if (socialSpyUsers.contains(uuid)) {
                socialSpyUsers.remove(uuid);
                player.sendMessage(mm.deserialize("<red>SocialSpy toggled OFF."));
            } else {
                socialSpyUsers.add(uuid);
                player.sendMessage(mm.deserialize("<green>SocialSpy toggled ON."));
            }
            return true;
        }

        if (cmdName.equals("r") || cmdName.equals("reply")) {
            if (!player.hasPermission("serversentials.msg")) {
                player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            String target = plugin.getReplyTarget(player.getUniqueId());
            if (target == null) {
                player.sendMessage(mm.deserialize("<red>No one to reply to."));
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(mm.deserialize("<red>Usage: /r <message>"));
                return true;
            }
            String message = String.join(" ", args);
            sendMessage(player, target, message);
            return true;
        }

        // /msg <player> <message>
        if (cmdName.equals("msg") || cmdName.equals("w") || cmdName.equals("tell") || cmdName.equals("pm") || cmdName.equals("message")) {
            if (!player.hasPermission("serversentials.msg")) {
                player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(mm.deserialize("<red>Usage: /msg <player> <message>"));
                return true;
            }
            String target = args[0];
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            sendMessage(player, target, message);
            return true;
        }

        return true;
    }

    private void sendMessage(Player sender, String targetName, String message) {
        Player localTarget = Bukkit.getPlayerExact(targetName);

        if (localTarget != null) {
            // Send locally
            localTarget.sendMessage(mm.deserialize("<gray>[" + sender.getName() + " -> me] " + message));
            sender.sendMessage(mm.deserialize("<gray>[me -> " + localTarget.getName() + "] " + message));
            
            plugin.setReplyTarget(sender.getUniqueId(), localTarget.getName());
            plugin.setReplyTarget(localTarget.getUniqueId(), sender.getName());

            // Broadcast to local socialspy users
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (socialSpyUsers.contains(p.getUniqueId()) && !p.equals(sender) && !p.equals(localTarget)) {
                    p.sendMessage(mm.deserialize("<red>[Spy] <gray>" + sender.getName() + " -> " + localTarget.getName() + ": " + message));
                }
            }

            // Broadcast to other servers' socialspy users
            plugin.getNetworkManager().sendPluginMessage(sender, "FORWARD_TO_PLAYER", "dummy", "SOCIALSPY_RECEIVE", sender.getName(), localTarget.getName(), message);
        } else if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
            // Send cross-server
            sender.sendMessage(mm.deserialize("<gray>[me -> " + targetName + "] " + message));
            plugin.setReplyTarget(sender.getUniqueId(), targetName);

            // Forward MSG_RECEIVE and SOCIALSPY_RECEIVE
            plugin.getNetworkManager().forwardToPlayer(sender, targetName, "MSG_RECEIVE", sender.getName(), targetName, message);
            plugin.getNetworkManager().sendPluginMessage(sender, "FORWARD_TO_PLAYER", "dummy", "SOCIALSPY_RECEIVE", sender.getName(), targetName, message);
        } else {
            sender.sendMessage(mm.deserialize("<red>Player '" + targetName + "' is offline."));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("serversentials.msg")) {
            return Collections.emptyList();
        }
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if (cmdName.equals("msg") || cmdName.equals("w") || cmdName.equals("tell") || cmdName.equals("pm") || cmdName.equals("message")) {
            if (args.length == 1) {
                return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[0]);
            }
        }
        return Collections.emptyList();
    }
}

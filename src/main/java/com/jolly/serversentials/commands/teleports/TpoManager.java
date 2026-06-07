package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TpoManager implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TpoManager(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    // -----------------------------
    // Main Command Handler
    // -----------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "tpo" -> handleTpo(player, args);
            case "tpohere" -> handleTpohere(player, args);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    // -----------------------------
    // Sending Requests
    // -----------------------------
    private void handleTpo(Player sender, String[] args) {
        if (!plugin.isModuleEnabled("tpo")) {
            sender.sendActionBar(plugin.mm("<red>This module is currently disabled!</red>"));
            return;
        }
        if (!sender.hasPermission("serversentials.tpo")) {
            sender.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            sender.sendActionBar(mm.deserialize("<red>Usage: /tpo <player>"));
            return;
        }
        String targetName = args[0];
        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendActionBar(mm.deserialize("<red>You cannot send a teleport request to yourself!"));
            return;
        }
        Player receiver = Bukkit.getPlayerExact(targetName);
        if (receiver != null && receiver.isOnline()) {
            sender.teleportAsync(receiver.getLocation());
            sender.sendActionBar(mm.deserialize("<green>Teleporting to <yellow>" + receiver.getName()));
        } else if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
            plugin.getNetworkManager().forwardToPlayer(sender, targetName, "TPO_REQUEST", sender.getName(), targetName, sender.getUniqueId().toString());
            sender.sendActionBar(mm.deserialize("<green>Teleporting to <yellow>" + targetName + "</yellow> (cross-server)..."));
        } else {
            sender.sendActionBar(mm.deserialize("<red>That player is not online!"));
        }
    }

    private void handleTpohere(Player sender, String[] args) {
        if (!plugin.isModuleEnabled("tpohere")) {
            sender.sendActionBar(plugin.mm("<red>This module is currently disabled!</red>"));
            return;
        }
        if (!sender.hasPermission("serversentials.tpohere")) {
            sender.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            sender.sendActionBar(mm.deserialize("<red>Usage: /tpohere <player>||all"));
            return;
        }
        if (args[0].equalsIgnoreCase("all")) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (!player.getUniqueId().equals(sender.getUniqueId())) {
                    player.teleportAsync(sender.getLocation());
                }
            });
            return;
        }
        String targetName = args[0];
        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendActionBar(mm.deserialize("<red>You cannot send a teleport request to yourself!"));
            return;
        }
        Player receiver = Bukkit.getPlayerExact(targetName);
        if (receiver != null && receiver.isOnline()) {
            receiver.teleportAsync(sender.getLocation());
            sender.sendActionBar(mm.deserialize("<green>Teleported <yellow>" + receiver.getName() + "</yellow> to you."));
        } else if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
            String localServer = plugin.getConfig().getString("server-name", "unknown");
            Location loc = sender.getLocation();
            plugin.getNetworkManager().forwardToPlayer(sender, targetName, "TPOHERE_REQUEST", sender.getName(), targetName, sender.getUniqueId().toString(), localServer, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            sender.sendActionBar(mm.deserialize("<green>Teleporting <yellow>" + targetName + "</yellow> to you (cross-server)..."));
        } else {
            sender.sendActionBar(mm.deserialize("<red>That player is not online!"));
        }
    }

    private boolean isValidTarget(Player sender, Player receiver) {
        if (receiver == null || !receiver.isOnline()) {
            sender.sendActionBar(mm.deserialize("<red>That player is not online!"));
            return false;
        }
        if (receiver.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendActionBar(mm.deserialize("<red>You cannot send a teleport request to yourself!"));
            return false;
        }
        return true;
    }


    // -----------------------------
    // Tab Completion
    // -----------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[0]).stream()
                    .filter(name -> !name.equalsIgnoreCase(player.getName()))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

}

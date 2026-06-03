package com.jolly.serversentials.commands.teleports;

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
        Player receiver = Bukkit.getPlayer(args[0]);
        if (!isValidTarget(sender, receiver)) return;
        sender.teleportAsync(receiver.getLocation());
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
                player.teleportAsync(sender.getLocation());
            });
            return;
        }
        Player receiver = Bukkit.getPlayer(args[0]);
        if (!isValidTarget(sender, receiver)) return;
        receiver.teleportAsync(sender.getLocation());
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
            String partial = args[0].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player)) // exclude self
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

}

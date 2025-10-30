package com.jolly.serversentials.commands;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.bukkit.Bukkit.getServer;

public class Containers implements CommandExecutor, TabCompleter {
    private static Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public static final Set<UUID> invseeUsers = new HashSet<>();
    public Containers(Serversentials plugin) {
        this.plugin = plugin;
    }



    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "craft" -> handleCraft(player);
            case "anvil" -> handleAnvil(player);
            case "loom" -> handleLoom(player);
            case "echest", "ec" -> handleEchest(player, args);
            case "invsee", "inv" -> handleInvsee(player, args);
            case "stonecutter", "scutter" -> handleStoneCutter(player);
            case "smithingtable", "smith" -> handleSmith(player);
        }
        return true;
    }

    private void handleCraft(Player player) {
        if (!player.hasPermission("serversentials.craft")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        player.openWorkbench(null, true);
    }

    private void handleAnvil(Player player) {
        if (!player.hasPermission("serversentials.anvil")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        player.openAnvil(null, true);
    }

    private void handleLoom(Player player) {
        if (!player.hasPermission("serversentials.loom")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        player.openLoom(null, true);
    }

    private void handleEchest(Player player, String[] args) {
        if (!player.hasPermission("serversentials.echest")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length > 0) {
            if (!player.hasPermission("serversentials.echest.others")) {
                player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
                return;
            }
            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer != null) {
                player.openInventory(targetPlayer.getEnderChest());
            } else {
                player.sendActionBar(plugin.mm("<red>Player not found!"));
            }
        } else {
            player.openInventory(player.getEnderChest());
        }
    }

    private void handleInvsee(Player player, String[] args) {
        if (!player.hasPermission("serversentials.invsee")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length > 0) {
            invseeUsers.add(player.getUniqueId());
            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer != null) {
                player.openInventory(targetPlayer.getInventory());
            } else {
                player.sendActionBar(plugin.mm("<red>Player not found!"));
            }
        } else {
            player.sendActionBar(plugin.mm("Usage: /invsee <player>"));
        }
    }

    private void handleStoneCutter(Player player) {
        if (!player.hasPermission("serversentials.stonecutter")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        player.openStonecutter(null, true);
    }

    private void handleSmith(Player player) {
        if (!player.hasPermission("serversentials.smithingtable")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        player.openSmithingTable(null, true);
    }

    @Override
    public @Nullable List<String> onTabComplete(CommandSender sender, Command command, String s, String [] args) {
        return List.of();
    }
}

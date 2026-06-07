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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.bukkit.Bukkit.getServer;

public class Containers implements CommandExecutor, TabCompleter {
    private static Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public static final Set<UUID> invseeUsers = new HashSet<>();
    public static final Map<UUID, String> crossServerInvsee = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, String> crossServerEchest = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<UUID, UUID> localInvseeTargets = new java.util.concurrent.ConcurrentHashMap<>();
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
        String moduleToCheck = cmd;
        if (cmd.equals("ec")) moduleToCheck = "echest";
        if (cmd.equals("inv")) moduleToCheck = "invsee";
        if (cmd.equals("scutter")) moduleToCheck = "stonecutter";
        if (cmd.equals("smith")) moduleToCheck = "smithingtable";

        if (!plugin.isModuleEnabled(moduleToCheck)) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

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
                String targetName = args[0];
                if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                    plugin.getNetworkManager().forwardToPlayer(player, targetName, "EC_REQUEST", player.getName(), targetName);
                    player.sendMessage(plugin.mm("<green>Querying " + targetName + "'s ender chest cross-server..."));
                } else {
                    player.sendActionBar(plugin.mm("<red>Player not found!"));
                }
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
            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer != null) {
                openLocalInvsee(player, targetPlayer, plugin);
            } else {
                String targetName = args[0];
                if (plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                    plugin.getNetworkManager().forwardToPlayer(player, targetName, "INV_REQUEST", player.getName(), targetName);
                    player.sendMessage(plugin.mm("<green>Querying " + targetName + "'s inventory cross-server..."));
                } else {
                    player.sendActionBar(plugin.mm("<red>Player not found!"));
                }
            }
        } else {
            player.sendActionBar(plugin.mm("Usage: /invsee <player>"));
        }
    }

    public static void openLocalInvsee(Player admin, Player target, Serversentials plugin) {
        Inventory gui = Bukkit.createInventory(null, 54, plugin.mm("<dark_gray>Invsee: " + target.getName()));

        // Populate main inventory (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = target.getInventory().getItem(i);
            if (item != null) gui.setItem(i, item.clone());
        }

        // Separator slots (36-44)
        ItemStack pane = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = pane.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.empty());
        pane.setItemMeta(meta);
        for (int i = 36; i < 45; i++) {
            gui.setItem(i, pane);
        }

        // Armor (45-48)
        gui.setItem(45, target.getInventory().getHelmet() != null ? target.getInventory().getHelmet().clone() : null);
        gui.setItem(46, target.getInventory().getChestplate() != null ? target.getInventory().getChestplate().clone() : null);
        gui.setItem(47, target.getInventory().getLeggings() != null ? target.getInventory().getLeggings().clone() : null);
        gui.setItem(48, target.getInventory().getBoots() != null ? target.getInventory().getBoots().clone() : null);

        // Offhand (49)
        gui.setItem(49, target.getInventory().getItemInOffHand() != null ? target.getInventory().getItemInOffHand().clone() : null);

        // Ender Chest shortcut (50)
        ItemStack echest = new ItemStack(org.bukkit.Material.ENDER_CHEST);
        org.bukkit.inventory.meta.ItemMeta ecMeta = echest.getItemMeta();
        ecMeta.displayName(plugin.mm("<purple>Ender Chest"));
        echest.setItemMeta(ecMeta);
        gui.setItem(50, echest);

        // Status Info (53)
        ItemStack status = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.ItemMeta statMeta = status.getItemMeta();
        statMeta.displayName(plugin.mm("<gold>Status Info"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        double health = target.getHealth();
        double maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        int food = target.getFoodLevel();
        int xp = target.getLevel();
        lore.add(plugin.mm("<gray>Health: <red>" + String.format("%.1f", health) + "/" + String.format("%.1f", maxHealth)));
        lore.add(plugin.mm("<gray>Food Level: <gold>" + food + "/20"));
        lore.add(plugin.mm("<gray>XP Level: <green>" + xp));
        statMeta.lore(lore);
        status.setItemMeta(statMeta);
        gui.setItem(53, status);

        admin.openInventory(gui);
        localInvseeTargets.put(admin.getUniqueId(), target.getUniqueId());
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
    public @Nullable List<String> onTabComplete(CommandSender sender, Command command, String alias, String [] args) {
        if (!(sender instanceof Player player)) return List.of();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("invsee") || cmd.equals("inv") || cmd.equals("echest") || cmd.equals("ec")) {
            if (args.length == 1) {
                return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[0]).stream()
                        .filter(name -> !name.equalsIgnoreCase(player.getName()))
                        .sorted()
                        .toList();
            }
        }
        return List.of();
    }
}

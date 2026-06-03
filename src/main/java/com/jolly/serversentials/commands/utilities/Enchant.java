package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.Scheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class Enchant implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<String> enchantNames;

    public Enchant(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.enchantNames = Arrays.stream(Enchantment.values())
                .filter(Objects::nonNull)
                .map(e -> e.getKey().getKey().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!plugin.isModuleEnabled("enchant.enabled")) {
            player.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (!player.hasPermission("serversentials.enchant")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length < 1) {
            player.sendActionBar(mm.deserialize("<red>Usage: /" + label + " <enchantment> [level] [player]"));
            return true;
        }

        // Find enchantment
        String enchName = args[0].toLowerCase(Locale.ROOT);
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchName));
        if (enchantment == null) {
            player.sendActionBar(mm.deserialize("<red>Invalid enchantment: <white>" + enchName + "</white>"));
            return true;
        }

        boolean respectLimits = plugin.getConfig().getBoolean("modules.enchant.respect-max-levels", true);
        int maxLevel = enchantment.getMaxLevel();

        // Parse level (default = max)
        int level = maxLevel;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendActionBar(mm.deserialize("<red>Invalid level!"));
                return true;
            }
        }

        // Determine target player
        Player target = player;
        if (args.length >= 3) {
            if (!player.hasPermission("serversentials.enchant.others")) {
                player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }

            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return true;
            }
        }

        int finalLevel = level;
        Player finalTarget = target;
        Enchantment finalEnchant = enchantment;

        scheduler.run(finalTarget, () -> {
            ItemStack item = finalTarget.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                finalTarget.sendActionBar(mm.deserialize("<red>You must hold an item in your hand!"));
                return;
            }

            // Handle level 0 → remove enchantment
            if (finalLevel == 0) {
                if (item.containsEnchantment(finalEnchant)) {
                    item.removeEnchantment(finalEnchant);
                    finalTarget.sendActionBar(mm.deserialize("<yellow>Removed <white>" + finalEnchant.getKey().getKey() + "</white> from your item."));
                    if (!finalTarget.equals(player)) {
                        player.sendActionBar(mm.deserialize("<yellow>Removed <white>" + finalEnchant.getKey().getKey()
                                + "</white> from <white>" + finalTarget.getName() + "</white>'s item."));
                    }
                } else {
                    finalTarget.sendActionBar(mm.deserialize("<red>That item doesn’t have that enchantment."));
                }
                return;
            }

            // Enchant item (respect or bypass limits)
            int appliedLevel = respectLimits ? Math.min(finalLevel, finalEnchant.getMaxLevel()) : finalLevel;

            try {
                item.addUnsafeEnchantment(finalEnchant, appliedLevel);
            } catch (Exception e) {
                finalTarget.sendActionBar(mm.deserialize("<red>Failed to apply enchantment!"));
                return;
            }

            String enchKey = finalEnchant.getKey().getKey();
            if (finalTarget.equals(player)) {
                player.sendActionBar(mm.deserialize("<green>Applied <white>" + enchKey + " " + appliedLevel + "</white> to your held item."));
            } else {
                player.sendActionBar(mm.deserialize("<green>Applied <white>" + enchKey + " " + appliedLevel
                        + "</white> to <white>" + finalTarget.getName() + "</white>'s held item."));
                finalTarget.sendActionBar(mm.deserialize("<green>You received <white>" + enchKey + " " + appliedLevel
                        + "</white> from <white>" + player.getName() + "</white>."));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return enchantNames.stream()
                    .filter(name -> name.startsWith(input))
                    .limit(50)
                    .toList();
        } else if (args.length == 3) {
            String input = args[2].toLowerCase(Locale.ROOT);
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    players.add(p.getName());
                }
            }
            return players;
        }
        return Collections.emptyList();
    }
}

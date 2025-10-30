package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.Scheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Item implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<String> materialNames;

    public Item(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.materialNames = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(mat -> mat.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!player.hasPermission("serversentials.item")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length < 1) {
            player.sendActionBar(mm.deserialize("<red>Usage: /" + label + " <material> [amount] [player]"));
            return true;
        }

        // Parse material
        Material material = Material.matchMaterial(args[0].toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            player.sendActionBar(mm.deserialize("<red>Invalid material: <white>" + args[0] + "</white>"));
            return true;
        }

        // Read config setting
        boolean respectMaxStack = plugin.getConfig().getBoolean("modules.item.respect-max-stack-size", true);
        int maxStack = material.getMaxStackSize();

        // Parse amount (defaults to max stack)
        int amount = maxStack;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                player.sendActionBar(mm.deserialize("<red>Invalid amount!"));
                return true;
            }
        }

        // Determine target
        Player target = player;
        if (args.length >= 3) {
            if (!player.hasPermission("serversentials.item.others")) {
                player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }

            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return true;
            }
        }

        Player finalTarget = target;
        int finalAmount = amount;

        // 🧭 Use Scheduler to run safely on the correct thread/region
        scheduler.run(finalTarget, () -> {
            if (respectMaxStack) {
                // Split into valid stack sizes
                int remaining = finalAmount;
                while (remaining > 0) {
                    int giveAmount = Math.min(remaining, maxStack);
                    remaining -= giveAmount;
                    finalTarget.getInventory().addItem(new ItemStack(material, giveAmount));
                }
            } else {
                // Allow oversized stacks
                finalTarget.getInventory().addItem(new ItemStack(material, finalAmount));
            }

            String itemName = material.name().toLowerCase(Locale.ROOT);
            if (finalTarget.equals(player)) {
                player.sendActionBar(mm.deserialize("<green>Gave yourself <white>" + finalAmount + "x " + itemName + "</white>."));
            } else {
                player.sendActionBar(mm.deserialize("<green>Gave <white>" + finalAmount + "x " + itemName
                        + "</white> to <white>" + finalTarget.getName() + "</white>."));
                finalTarget.sendActionBar(mm.deserialize("<green>You received <white>" + finalAmount + "x " + itemName
                        + "</white> from <white>" + player.getName() + "</white>."));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return materialNames.stream()
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

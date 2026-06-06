package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class LoreCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoreCommand(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!player.hasPermission("serversentials.lore")) {
            player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<red>Usage: /lore <add|set|delete|clear> ..."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(mm.deserialize("<red>You must be holding an item in your main hand to edit its lore!"));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(mm.deserialize("<red>This item cannot have lore!"));
            return true;
        }

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize("<red>Usage: /lore add <content>"));
                    return true;
                }
                String rawLore = getJoinedArgs(args, 1);
                Component parsedLore = plugin.getFormatUtility().parse(player, rawLore);
                lore.add(parsedLore);
                meta.lore(lore);
                item.setItemMeta(meta);
                player.sendMessage(mm.deserialize("<green>Lore line added: ").append(parsedLore));
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(mm.deserialize("<red>Usage: /lore set <line number> <content>"));
                    return true;
                }
                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(mm.deserialize("<red>Invalid line number!"));
                    return true;
                }
                int index = lineNumber - 1;
                if (index < 0 || index > lore.size()) {
                    player.sendMessage(mm.deserialize("<red>Line number out of bounds! Choose between 1 and " + (lore.size() + 1)));
                    return true;
                }
                String rawLore = getJoinedArgs(args, 2);
                Component parsedLore = plugin.getFormatUtility().parse(player, rawLore);
                if (index == lore.size()) {
                    lore.add(parsedLore);
                } else {
                    lore.set(index, parsedLore);
                }
                meta.lore(lore);
                item.setItemMeta(meta);
                player.sendMessage(mm.deserialize("<green>Lore line " + lineNumber + " set to: ").append(parsedLore));
            }
            case "delete", "del", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize("<red>Usage: /lore delete <line number>"));
                    return true;
                }
                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(mm.deserialize("<red>Invalid line number!"));
                    return true;
                }
                int index = lineNumber - 1;
                if (index < 0 || index >= lore.size()) {
                    player.sendMessage(mm.deserialize("<red>Line number out of bounds! Valid lines are 1 to " + lore.size()));
                    return true;
                }
                Component removedLore = lore.remove(index);
                meta.lore(lore);
                item.setItemMeta(meta);
                player.sendMessage(mm.deserialize("<green>Lore line " + lineNumber + " deleted (was: ").append(removedLore).append(mm.deserialize("<green>)")));
            }
            case "clear" -> {
                meta.lore(null);
                item.setItemMeta(meta);
                player.sendMessage(mm.deserialize("<green>Lore cleared successfully."));
            }
            default -> player.sendMessage(mm.deserialize("<red>Unknown subcommand! Use: /lore <add|set|delete|clear>"));
        }

        return true;
    }

    private String getJoinedArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (String sub : List.of("add", "set", "delete", "clear")) {
                if (sub.startsWith(partial)) suggestions.add(sub);
            }
            return suggestions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && item.getType() != Material.AIR) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        List<Component> lore = meta.lore();
                        int size = (lore == null) ? 0 : lore.size();
                        List<String> lines = new ArrayList<>();
                        int max = sub.equals("set") ? size + 1 : size;
                        for (int i = 1; i <= max; i++) {
                            lines.add(String.valueOf(i));
                        }
                        return lines;
                    }
                }
            }
        }

        return Collections.emptyList();
    }
}

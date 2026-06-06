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

public class ItemRenameCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ItemRenameCommand(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!player.hasPermission("serversentials.itemrename")) {
            player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(mm.deserialize("<red>You must be holding an item in your main hand to rename it!"));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(mm.deserialize("<red>This item cannot be renamed!"));
            return true;
        }

        if (args.length == 0) {
            // Reset to default name
            meta.displayName(null);
            item.setItemMeta(meta);
            player.sendMessage(mm.deserialize("<green>Item name reset to default."));
            return true;
        }

        // Parse format and set item name
        String rawName = String.join(" ", args);
        Component parsedName = plugin.getFormatUtility().parse(player, rawName);
        meta.displayName(parsedName);
        item.setItemMeta(meta);

        Component successMessage = mm.deserialize("<green>Item renamed to: ").append(parsedName);
        player.sendMessage(successMessage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

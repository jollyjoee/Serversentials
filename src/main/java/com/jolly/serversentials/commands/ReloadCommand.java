package com.jolly.serversentials.commands;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand implements CommandExecutor {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ReloadCommand(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("serversentials.reload")) {
            if (sender instanceof Player p) {
                p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            } else {
                sender.sendMessage("§cNo permission.");
            }
            return true;
        }

        plugin.reloadConfig();
        plugin.getLogger().info("✅ Serversentials reloaded.");
        if (sender instanceof Player p) {
            p.sendActionBar(mm.deserialize("<green>Serversentials reloaded!</green>"));
        } else {
            sender.sendMessage("§aServersentials reloaded!");
        }

        return true;
    }
}

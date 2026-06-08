package com.jolly.serversentials.economy;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BalanceCommand(Serversentials plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isModuleEnabled("economy.enabled")) {
            sender.sendMessage(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        OfflinePlayer target = null;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Only players can check their own balance!");
                return true;
            }
            if (!p.hasPermission("serversentials.balance")) {
                p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            target = p;
        } else if (args.length == 1) {
            if (sender instanceof Player p && !p.hasPermission("serversentials.balance.others")) {
                p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (offline == null || offline.getUniqueId() == null) {
                if (sender instanceof Player p) {
                    p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.player-not-found")));
                } else {
                    sender.sendMessage(mm.deserialize(plugin.prefixMessage("messages.player-not-found")));
                }
                return true;
            }
            target = offline;
        } else {
            sender.sendMessage(mm.deserialize("<red>Usage: /" + label + " [player]"));
            return true;
        }

        final OfflinePlayer finalTarget = target;
        economy.getBalanceAsync(finalTarget.getUniqueId()).thenAccept(balance -> {
            String targetName = finalTarget.getName();
            if (targetName == null) targetName = args.length > 0 ? args[0] : "Unknown";
            String msg = plugin.getConfig().getString("messages.balance-display")
                    .replace("{player}", targetName)
                    .replace("{symbol}", economy.getCurrencySymbol())
                    .replace("{balance}", String.format("%.2f", balance));

            if (sender instanceof Player p) {
                p.sendActionBar(mm.deserialize(msg));
            } else {
                sender.sendMessage(mm.deserialize(msg));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
    }
}

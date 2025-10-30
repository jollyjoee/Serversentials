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
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) return true;

        Player target = null;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) return true;
            target = p;
        } else if (args.length == 1) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            if (offline == null || offline.getUniqueId() == null) {
                sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.player-not-found")));
                return true;
            }
            target = offline.getPlayer() != null ? offline.getPlayer() : (Player) sender;
        }

        final Player display = target instanceof Player p ? p : null;
        OfflinePlayer offlineTarget = target instanceof Player p ? p : (OfflinePlayer) target;

        economy.getBalanceAsync(offlineTarget.getUniqueId()).thenAccept(balance -> {
            String msg = plugin.getConfig().getString("messages.balance-display")
                    .replace("{player}", offlineTarget.getName())
                    .replace("{symbol}", economy.getCurrencySymbol())
                    .replace("{balance}", String.format("%.2f", balance));

            if (display != null) {
                display.sendActionBar(mm.deserialize(msg));
            } else {
                sender.sendActionBar(mm.deserialize(msg));
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

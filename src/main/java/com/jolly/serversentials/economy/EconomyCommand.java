package com.jolly.serversentials.economy;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public EconomyCommand(Serversentials plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isModuleEnabled("economy.enabled")) {
            sender.sendMessage(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (args.length != 3) {
            sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.economy-usage")));
            return true;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getOfflinePlayer(args[1]).getPlayer();
        if (target == null) {
            sender.sendActionBar(mm.deserialize(plugin.prefixMessage(plugin.getConfig().getString("messages.player-not-found"))));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.invalid-number")));
            return true;
        }

        UUID targetUUID = target.getUniqueId();

        switch (action) {
            case "set" -> economy.setBalanceAsync(targetUUID, amount).thenRun(() ->
                    sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.economy-success")
                            .replace("{action}", "Set")
                            .replace("{symbol}", economy.getCurrencySymbol())
                            .replace("{amount}", String.format("%.2f", amount))
                            .replace("{player}", target.getName())))
            );
            case "give" -> economy.addBalanceAsync(targetUUID, amount).thenRun(() ->
                    sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.economy-success")
                            .replace("{action}", "Gave")
                            .replace("{symbol}", economy.getCurrencySymbol())
                            .replace("{amount}", String.format("%.2f", amount))
                            .replace("{player}", target.getName())))
            );
            case "deduct" -> economy.deductBalanceAsync(targetUUID, amount).thenRun(() ->
                    sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.economy-success")
                            .replace("{action}", "Deducted")
                            .replace("{symbol}", economy.getCurrencySymbol())
                            .replace("{amount}", String.format("%.2f", amount))
                            .replace("{player}", target.getName())))
            );
            default -> sender.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.economy-usage")));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 2) return Collections.emptyList();
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
    }
}

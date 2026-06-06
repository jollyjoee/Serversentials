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

public class PayCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PayCommand(Serversentials plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isModuleEnabled("economy.enabled")) {
            sender.sendMessage(mm.deserialize("<red>This module is currently disabled!</red>"));
            return true;
        }

        if (!(sender instanceof Player p)) return true;
        if (args.length != 2) {
            p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.pay-usage")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            String targetName = args[0];
            if (plugin.getConfig().getBoolean("economy.cross-server-pay", true) && plugin.getNetworkManager().isOnlineOnNetwork(targetName)) {
                if (targetName.equalsIgnoreCase(p.getName())) {
                    p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.invalid-recipient")));
                    return true;
                }

                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.invalid-number")));
                    return true;
                }

                UUID senderUUID = p.getUniqueId();
                economy.getBalanceAsync(senderUUID).thenAccept(balance -> {
                    if (balance < amount) {
                        p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.pay-insufficient")));
                        return;
                    }

                    economy.deductBalanceAsync(senderUUID, amount).thenRun(() -> {
                        plugin.getNetworkManager().forwardToPlayer(p, targetName, "PAY_CHECK_REQ", p.getName(), targetName, amount);
                        p.sendMessage(mm.deserialize("<green>Processing cross-server payment of <white>" + amount + " <green>to <white>" + targetName + "..."));
                    });
                });
                return true;
            }

            p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.player-not-found")));
            return true;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.invalid-recipient")));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.invalid-number")));
            return true;
        }

        UUID senderUUID = p.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        economy.getBalanceAsync(senderUUID).thenAccept(balance -> {
            if (balance < amount) {
                p.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.pay-insufficient")));
                return;
            }

            economy.deductBalanceAsync(senderUUID, amount).thenRun(() ->
                    economy.addBalanceAsync(targetUUID, amount).thenRun(() -> {
                        String sentMsg = plugin.getConfig().getString("messages.pay-sent")
                                .replace("{symbol}", economy.getCurrencySymbol())
                                .replace("{amount}", String.format("%.2f", amount))
                                .replace("{player}", target.getName());

                        String receivedMsg = plugin.getConfig().getString("messages.pay-received")
                                .replace("{symbol}", economy.getCurrencySymbol())
                                .replace("{amount}", String.format("%.2f", amount))
                                .replace("{player}", p.getName());

                        p.sendActionBar(mm.deserialize(sentMsg));
                        target.sendActionBar(mm.deserialize(receivedMsg));
                    })
            );
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        if (plugin.getConfig().getBoolean("economy.cross-server-pay", true)) {
            return plugin.getNetworkManager().getNetworkPlayerSuggestions(args[0]).stream()
                    .filter(name -> !name.equalsIgnoreCase(sender.getName()))
                    .sorted()
                    .toList();
        }
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
    }
}

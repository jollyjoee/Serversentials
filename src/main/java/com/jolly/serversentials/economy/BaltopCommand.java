package com.jolly.serversentials.economy;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BaltopCommand implements CommandExecutor {

    private final Serversentials plugin;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final int perPage = 10;

    public BaltopCommand(Serversentials plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }

        int finalPage = page;
        economy.getTopBalancesAsync(page, perPage).thenAccept(list -> {
            if (list.isEmpty()) {
                sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.baltop-empty")));
                return;
            }

            sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.baltop-header").replace("{page}", String.valueOf(finalPage))));

            for (int i = 0; i < list.size(); i++) {
                Map.Entry<UUID, Double> entry = list.get(i);
                UUID uuid = entry.getKey();
                double balance = entry.getValue();

                String playerName;
                Player online = Bukkit.getPlayer(uuid); // online check
                if (online != null) {
                    playerName = online.getName();
                } else {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    playerName = offline.getName() != null ? offline.getName() : uuid.toString();
                }

                String line = plugin.getConfig().getString("baltop-line", "<yellow>{rank}. {player} - {symbol}{balance}")
                        .replace("{rank}", String.valueOf((finalPage-1)*perPage + i + 1))
                        .replace("{player}", playerName)
                        .replace("{symbol}", economy.getCurrencySymbol())
                        .replace("{balance}", String.format("%.2f", balance));

                sender.sendMessage(mm.deserialize(line));
            }

        });

        return true;
    }
}

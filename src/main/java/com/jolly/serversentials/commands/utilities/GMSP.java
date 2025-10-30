package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GMSP implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;

    public GMSP(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        if (!command.getName().equalsIgnoreCase("gmsp")) return false;
        if (!player.hasPermission("serversentials.gmsp")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return true;
        }
        if (args.length == 1) {
            if (!player.hasPermission("serversentials.gmsp.others")) {
                player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && target.isOnline()) {
                target.setGameMode(GameMode.SPECTATOR);
                target.sendActionBar(plugin.mm("<green>Your gamemode has been set to Spectator!"));
                player.sendActionBar(plugin.mm("<green>Set " + target.getName() + " to Spectator mode."));
                return true;
            } else {
                player.sendActionBar(plugin.mm("<red>Player not found!"));
                return true;
            }
        }
        if (!player.getGameMode().equals(GameMode.SPECTATOR)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendActionBar(plugin.mm("<green>Your gamemode has been set to Spectator."));
        } else {
            player.sendActionBar(plugin.mm("<yellow>You are already in Spectator mode."));
        }
        return true;
    }

    // Tab completion for the first argument (online player names)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().startsWith("gmsp")) return null;
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}

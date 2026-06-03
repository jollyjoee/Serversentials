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

public class GMS implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;

    public GMS(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }
        if (!command.getName().equalsIgnoreCase("gms")) return false;

        if (!plugin.isModuleEnabled("gamemode")) {
            player.sendActionBar(plugin.mm("<red>This module is currently disabled!</red>"));
            return true;
        }
        if (!player.hasPermission("serversentials.gms")) {
            player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return true;
        }
        if (args.length == 1) {
            if (!player.hasPermission("serversentials.gms.others")) {
                player.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && target.isOnline()) {
                target.setGameMode(GameMode.SURVIVAL);
                target.sendActionBar(plugin.mm("<green>Your gamemode has been set to Survival!"));
                player.sendActionBar(plugin.mm("<green>Set " + target.getName() + " to Survival mode."));
                return true;
            } else {
                player.sendActionBar(plugin.mm("<red>Player not found!"));
                return true;
            }
        }
        if (!player.getGameMode().equals(GameMode.SURVIVAL)) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendActionBar(plugin.mm("<green>Your gamemode has been set to Survival."));
        } else {
            player.sendActionBar(plugin.mm("<yellow>You are already in Survival mode."));
        }

        return true;
    }

    // Tab completion for the first argument (online player names)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().startsWith("gms")) return null;
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

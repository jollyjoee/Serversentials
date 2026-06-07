package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class Whois implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public Whois(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serversentials.whois")) {
            sender.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<red>Usage: /whois <nickname>"));
            return true;
        }

        String searchNick = args[0];
        List<String> realNames = plugin.getNetworkManager().getPlayersWithNickname(searchNick);

        if (realNames.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>No online player found with nickname '<yellow>" + searchNick + "</yellow>'."));
        } else {
            sender.sendMessage(mm.deserialize("<green>Online players matching '<yellow>" + searchNick + "</yellow>':"));
            for (String name : realNames) {
                String rawNick = plugin.getNetworkManager().getRawNickname(name);
                String display = (rawNick != null) ? rawNick : name;
                sender.sendMessage(mm.deserialize(" <gold>- " + name + " <gray>(Display: " + display + ")</gray>"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("whois")) return null;
        if (args.length == 1) {
            return plugin.getNetworkManager().getNetworkNicknameSuggestions(args[0]);
        }
        return Collections.emptyList();
    }
}

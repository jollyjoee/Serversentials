package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public StaffChatCommand(Serversentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!player.hasPermission("serversentials.staffchat")) {
            player.sendMessage(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<red>Usage: /sc <message>"));
            return true;
        }

        String message = String.join(" ", args);
        String server = plugin.getConfig().getString("server-name", "unknown");
        String staffMsg = "<dark_red>[StaffChat]</dark_red> <gray>(" + server + ")</gray> <gold>" + player.getName() + "</gold> <gray>»</gray> <white>" + message + "</white>";

        // Send locally
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("serversentials.staffchat")) {
                p.sendMessage(mm.deserialize(staffMsg));
            }
        }

        // Send cross-server
        plugin.getNetworkManager().sendPluginMessage(player, "BROADCAST_STAFF", staffMsg);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SpeedCommand implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SpeedCommand(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player targetPlayer = null;
        float speedInput = -1;
        String cmdName = command.getName().toLowerCase(Locale.ROOT);

        if (!plugin.isModuleEnabled(cmdName)) {
            if (sender instanceof Player p) {
                p.sendActionBar(mm.deserialize("<red>This module is currently disabled!</red>"));
            } else {
                sender.sendMessage("This module is currently disabled!");
            }
            return true;
        }

        if (sender instanceof Player p && !p.hasPermission("serversentials." + cmdName)) {
            p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<red>Usage: /" + label + " <0-10> [player]"));
            return true;
        }

        try {
            speedInput = Float.parseFloat(args[0]);
            if (speedInput < 0 || speedInput > 10) {
                sender.sendMessage(mm.deserialize("<red>Speed must be between 0 and 10!"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Invalid speed value! Must be a number between 0 and 10."));
            return true;
        }

        if (args.length >= 2) {
            if (sender instanceof Player p && !p.hasPermission("serversentials." + cmdName + ".others")) {
                p.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
                return true;
            }
            targetPlayer = Bukkit.getPlayerExact(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage(mm.deserialize(plugin.prefixMessage("messages.player-not-found")));
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Please specify a target player: /" + label + " <0-10> <player>");
                return true;
            }
            targetPlayer = p;
        }

        final Player target = targetPlayer;
        final float finalSpeed = speedInput;

        scheduler.run(target, () -> {
            if (cmdName.equals("flyspeed")) {
                float flySpeed = finalSpeed < 1.0f ? finalSpeed * 0.1f : ((finalSpeed - 1.0f) / 9.0f) * 0.9f + 0.1f;
                target.setFlySpeed(flySpeed);
                sender.sendMessage(mm.deserialize("<green>Fly speed set to <white>" + finalSpeed + "</white> for <white>" + target.getName() + "</white>."));
                if (!target.equals(sender)) {
                    target.sendMessage(mm.deserialize("<green>Your fly speed has been set to <white>" + finalSpeed + "</white> by <white>" + sender.getName() + "</white>."));
                }
            } else {
                float walkSpeed = finalSpeed < 1.0f ? finalSpeed * 0.2f : ((finalSpeed - 1.0f) / 9.0f) * 0.8f + 0.2f;
                target.setWalkSpeed(walkSpeed);
                sender.sendMessage(mm.deserialize("<green>Walk speed set to <white>" + finalSpeed + "</white> for <white>" + target.getName() + "</white>."));
                if (!target.equals(sender)) {
                    target.sendMessage(mm.deserialize("<green>Your walk speed has been set to <white>" + finalSpeed + "</white> by <white>" + sender.getName() + "</white>."));
                }
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase(Locale.ROOT);
        if (!cmdName.equals("flyspeed") && !cmdName.equals("walkspeed")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> speeds = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                speeds.add(String.valueOf(i));
            }
            return speeds;
        } else if (args.length == 2) {
            if (sender.hasPermission("serversentials." + cmdName + ".others")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                List<String> completions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        }
        return Collections.emptyList();
    }
}

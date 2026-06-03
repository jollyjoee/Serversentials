package com.jolly.serversentials;

import com.jolly.serversentials.commands.utilities.Fly;
import com.jolly.serversentials.commands.utilities.Hide;
import com.jolly.serversentials.commands.utilities.Nick;
import com.jolly.serversentials.commands.utilities.Vanish;
import com.jolly.serversentials.economy.EconomyManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Placeholder extends PlaceholderExpansion {

    private final Serversentials plugin;
    private final Nick nick;
    private final Fly fly;
    private final Vanish vanish;
    private final Hide hide;
    private final EconomyManager eco;
    public Placeholder(Serversentials plugin, Nick nick, Fly fly, Vanish vanish, Hide hide, EconomyManager eco) {
        this.plugin = plugin;
        this.nick = nick;
        this.fly = fly;
        this.vanish = vanish;
        this.hide = hide;
        this.eco = eco;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serversentials";
    }

    @Override
    public @NotNull String getAuthor() {
        return "jolly";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "No player found";
        if (identifier.equalsIgnoreCase("displayname")) {
            Component displayName = player.displayName();
            if (displayName == null) displayName = Component.text(player.getName());
            return LegacyComponentSerializer.legacySection().serialize(displayName);
        }
        if (identifier.equalsIgnoreCase("name")) return player.getName();
        if (identifier.equalsIgnoreCase("isflying")) return fly.isFlying(player) ? "true" : "false";
        if (identifier.equalsIgnoreCase("gamemode")) return String.valueOf(player.getGameMode());
        if (identifier.equalsIgnoreCase("ping")) return String.valueOf(player.getPing());
        if (identifier.equalsIgnoreCase("vanished")) return vanish.isVanished(player) ? "true" : "false";
        if (identifier.equalsIgnoreCase("hidden")) return hide.isHidden(player) ? "true" : "false";
        if (identifier.equalsIgnoreCase("eco_balance")) {
            return String.valueOf(eco.getBalance(player));
        }
        if (identifier.equalsIgnoreCase("eco_balance_formatted")) {
            return eco.format(eco.getBalance(player));
        }
        return null;
    }
}

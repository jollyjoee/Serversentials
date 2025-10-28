package com.jolly.serversentials;

import com.jolly.serversentials.commands.utilities.Fly;
import com.jolly.serversentials.commands.utilities.Nick;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Placeholder extends PlaceholderExpansion {

    private final Serversentials plugin;
    private final Nick nick;
    private final Fly fly;
    public Placeholder(Serversentials plugin, Nick nick, Fly fly) {
        this.plugin = plugin;
        this.nick = nick;
        this.fly = fly;
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
        if (identifier.equalsIgnoreCase("nick")) {
            String rawNick = nick.getNick(player);
            if (rawNick == null || rawNick.isEmpty()) return player.getName();
            return LegacyComponentSerializer.legacySection()
                    .serialize(MiniMessage.miniMessage().deserialize(rawNick));
        }
        if (identifier.equalsIgnoreCase("name")) return player.getName();
        if (identifier.equalsIgnoreCase("isflying")) return fly.isFlying(player) ? "true" : "false";
        if (identifier.equalsIgnoreCase("gamemode")) return String.valueOf(player.getGameMode());
        return null;
    }
}

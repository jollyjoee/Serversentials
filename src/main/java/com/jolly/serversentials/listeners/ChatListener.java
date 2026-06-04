package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.economy.VaultHook;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;

public class ChatListener implements Listener {

    private final Serversentials plugin;
    private final VaultHook vaultHook;
    private final ChatRenderer renderer;

    public ChatListener(Serversentials plugin, VaultHook vaultHook) {
        this.plugin = plugin;
        this.vaultHook = vaultHook;
        this.renderer = new ServersentialsChatRenderer();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Check global enabled state from config
        if (!plugin.getConfig().getBoolean("modules.chat.enabled", true)) {
            return;
        }

        // Check global permission if configured
        if (plugin.getConfig().getBoolean("modules.chat.require-global-permission", true)) {
            if (!player.hasPermission("serversentials.chat")) {
                return;
            }
        }

        // 1. Process player's raw message and format it with authorized tags/colors
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component parsedMessage = plugin.getFormatUtility().parse(player, rawText);

        // 2. Set event message to the parsed component
        event.message(parsedMessage);

        // 3. Register our modern ChatRenderer
        event.renderer(renderer);
    }

    /** ChatRenderer to format the final layout seen by other players */
    private class ServersentialsChatRenderer implements ChatRenderer {
        @Override
        public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
            String rawPrefix = vaultHook.isChatHooked() ? vaultHook.getChat().getPlayerPrefix(source) : "";
            String rawSuffix = vaultHook.isChatHooked() ? vaultHook.getChat().getPlayerSuffix(source) : "";

            // Format template lookup
            String formatTemplate = getFormatTemplate(source);

            // Apply PlaceholderAPI
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatTemplate = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(source, formatTemplate);
            }
            
            // Translate any legacy codes (like &c) from PlaceholderAPI into MiniMessage tags
            formatTemplate = plugin.getFormatUtility().translateLegacyToMiniMessageUnrestricted(formatTemplate);

            // Create layout component placeholders
            Component prefixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix);
            Component suffixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawSuffix);
            Component playerComponent = source.displayName(); // respects custom /nick names

            TagResolver layoutPlaceholders = TagResolver.resolver(
                    Placeholder.component("prefix", prefixComponent),
                    Placeholder.component("suffix", suffixComponent),
                    Placeholder.component("player", playerComponent),
                    Placeholder.component("message", message)
            );

            // Compile final chat component securely without raw injection risk
            return MiniMessage.miniMessage().deserialize(formatTemplate, layoutPlaceholders);
        }
    }

    /** Select the correct format template configured in config.yml */
    private String getFormatTemplate(Player player) {
        List<?> formatsList = plugin.getConfig().getList("modules.chat.formats");
        if (formatsList != null) {
            for (Object obj : formatsList) {
                if (obj instanceof Map<?, ?> map) {
                    String permission = (String) map.get("permission");
                    String format = (String) map.get("format");
                    if (permission != null && format != null && player.hasPermission(permission)) {
                        return format;
                    }
                }
            }
        }
        return plugin.getConfig().getString("modules.chat.default-format", "<gray>{prefix}</gray><white>{player}</white><gray> » {message}</gray>");
    }
}

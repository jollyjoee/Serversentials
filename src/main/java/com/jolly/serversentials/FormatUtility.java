package com.jolly.serversentials;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class FormatUtility {

    private final Serversentials plugin;

    private static final Set<String> STANDARD_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    );

    private static final Set<String> DECORATIONS = Set.of(
            "bold", "italic", "underlined", "underline", "strikethrough", "obfuscated", "magic"
    );

    public FormatUtility(Serversentials plugin) {
        this.plugin = plugin;
    }

    /**
     * Parses a raw string with the player's format and color permissions.
     */
    public Component parse(Player player, String input) {
        String translatedMessage = translateLegacyToMiniMessage(player, input);
        TagResolver playerMessageResolver = createPlayerMessageResolver(player);
        MiniMessage playerMiniMessage = MiniMessage.builder()
                .tags(playerMessageResolver)
                .build();
        return playerMiniMessage.deserialize(translatedMessage);
    }

    /**
     * Translates legacy codes (like &c) to MiniMessage tags if permission is held.
     */
    public String translateLegacyToMiniMessage(Player player, String message) {
        StringBuilder sb = new StringBuilder();
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                String tag = legacyCodeToTag(code);
                if (tag != null && hasPermissionForTag(player, tag)) {
                    sb.append("<").append(tag).append(">");
                    i++; // skip code char
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    /**
     * Translates legacy codes to MiniMessage tags globally without permission checks (useful for formats).
     */
    public String translateLegacyToMiniMessageUnrestricted(String message) {
        StringBuilder sb = new StringBuilder();
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if ((chars[i] == '&' || chars[i] == '§') && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                String tag = legacyCodeToTag(code);
                if (tag != null) {
                    sb.append("<").append(tag).append(">");
                    i++; // skip code char
                    continue;
                }
            }
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    /**
     * Builds dynamic TagResolver that only parses tags the player has explicit permission to use.
     */
    public TagResolver createPlayerMessageResolver(Player player) {
        return new TagResolver() {
            @Override
            public Tag resolve(String name, ArgumentQueue arguments, Context context) {
                if (!hasPermissionForTag(player, name)) {
                    return null; // Ignore and let MiniMessage treat as raw plain text
                }

                Tag resolved = null;
                if (StandardTags.color().has(name)) {
                    resolved = StandardTags.color().resolve(name, arguments, context);
                } else if (StandardTags.decorations().has(name)) {
                    resolved = StandardTags.decorations().resolve(name, arguments, context);
                } else if (StandardTags.rainbow().has(name)) {
                    resolved = StandardTags.rainbow().resolve(name, arguments, context);
                } else if (StandardTags.gradient().has(name)) {
                    resolved = StandardTags.gradient().resolve(name, arguments, context);
                }

                return resolved;
            }

            @Override
            public boolean has(String name) {
                return hasPermissionForTag(player, name) && (
                        StandardTags.color().has(name) ||
                        StandardTags.decorations().has(name) ||
                        StandardTags.rainbow().has(name) ||
                        StandardTags.gradient().has(name)
                );
            }
        };
    }

    /**
     * Checks if a player has permission for a specific MiniMessage tag.
     */
    public boolean hasPermissionForTag(Player player, String tag) {
        String tagName = tag.toLowerCase(Locale.ROOT);

        // 1. Standard Colors
        if (STANDARD_COLORS.contains(tagName)) {
            if (player.hasPermission("serversentials.chat.color." + tagName)) {
                return true;
            }
            boolean hasGlobal = player.hasPermission("serversentials.chat.color.all") || player.hasPermission("serversentials.chat.color.*");
            if (hasGlobal) {
                // Respect explicit negative override set in permission plugins (evaluates to false)
                if (player.isPermissionSet("serversentials.chat.color." + tagName) && !player.hasPermission("serversentials.chat.color." + tagName)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        // 2. Hex Colors
        if (isHexColor(tagName)) {
            if (player.hasPermission("serversentials.chat.color.hex")) {
                return true;
            }
            boolean hasGlobal = player.hasPermission("serversentials.chat.color.all") || player.hasPermission("serversentials.chat.color.*");
            if (hasGlobal) {
                if (player.isPermissionSet("serversentials.chat.color.hex") && !player.hasPermission("serversentials.chat.color.hex")) {
                    return false;
                }
                return true;
            }
            return false;
        }

        // 3. Styles / Decorations
        if (DECORATIONS.contains(tagName)) {
            // Map "underline" standard to standard node
            String permName = tagName.equals("underline") ? "underlined" : tagName;
            if (player.hasPermission("serversentials.chat.style." + permName)) {
                return true;
            }
            boolean hasGlobal = player.hasPermission("serversentials.chat.style.all") || player.hasPermission("serversentials.chat.style.*");
            if (hasGlobal) {
                if (player.isPermissionSet("serversentials.chat.style." + permName) && !player.hasPermission("serversentials.chat.style." + permName)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        // 4. Special effects: rainbow / gradient
        if (tagName.equals("rainbow") || tagName.equals("gradient")) {
            if (player.hasPermission("serversentials.chat.style." + tagName)) {
                return true;
            }
            boolean hasGlobal = player.hasPermission("serversentials.chat.style.all") || player.hasPermission("serversentials.chat.style.*");
            if (hasGlobal) {
                if (player.isPermissionSet("serversentials.chat.style." + tagName) && !player.hasPermission("serversentials.chat.style." + tagName)) {
                    return false;
                }
                return true;
            }
            return false;
        }

        return false;
    }

    private boolean isHexColor(String name) {
        if (name.startsWith("#")) {
            return name.length() == 4 || name.length() == 7;
        }
        if (name.length() == 3 || name.length() == 6) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (Character.digit(c, 16) == -1) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private String legacyCodeToTag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }
}

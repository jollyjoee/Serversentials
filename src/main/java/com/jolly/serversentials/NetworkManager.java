package com.jolly.serversentials;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NetworkManager implements PluginMessageListener {

    public static final String CHANNEL = "serversentials:channel";

    private final Serversentials plugin;
    private final Set<String> networkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> networkNicknames = new ConcurrentHashMap<>();
    private boolean listRequested = false;

    public NetworkManager(Serversentials plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public Set<String> getNetworkPlayers() {
        return networkPlayers;
    }

    public boolean isOnlineOnNetwork(String name) {
        return networkPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(name));
    }

    public List<String> getNetworkPlayerSuggestions(String prefix) {
        String lower = prefix.toLowerCase();
        if (networkPlayers.isEmpty()) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(lower))
                    .collect(Collectors.toList());
        }
        return networkPlayers.stream()
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    public void onPlayerJoinServer(Player player) {
        if (!listRequested || networkPlayers.isEmpty()) {
            plugin.getScheduler().runLater(player, () -> {
                requestPlayerList(player);
                listRequested = true;
            }, 20L);
        }
    }

    public void onPlayerQuitServer() {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            networkPlayers.clear();
            networkNicknames.clear();
            listRequested = false;
        }
    }

    public void requestPlayerList(Player player) {
        sendPluginMessage(player, "PLAYER_LIST_REQ");
    }

    public void sendPluginMessage(Player player, String subChannel, Object... args) {
        if (player == null) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        for (Object arg : args) {
            if (arg instanceof String) out.writeUTF((String) arg);
            else if (arg instanceof Integer) out.writeInt((Integer) arg);
            else if (arg instanceof Double) out.writeDouble((Double) arg);
            else if (arg instanceof Float) out.writeFloat((Float) arg);
            else if (arg instanceof Boolean) out.writeBoolean((Boolean) arg);
            else if (arg instanceof byte[]) out.write((byte[]) arg);
        }
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    public void forwardToPlayer(Player player, String targetPlayer, String subChannel, Object... args) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        for (Object arg : args) {
            if (arg instanceof String) out.writeUTF((String) arg);
            else if (arg instanceof Integer) out.writeInt((Integer) arg);
            else if (arg instanceof Double) out.writeDouble((Double) arg);
            else if (arg instanceof Float) out.writeFloat((Float) arg);
            else if (arg instanceof Boolean) out.writeBoolean((Boolean) arg);
            else if (arg instanceof byte[]) out.write((byte[]) arg);
        }
        sendPluginMessage(player, "FORWARD_TO_PLAYER", targetPlayer, subChannel, out.toByteArray());
    }

    public void requestPlayerTransfer(Player player, String playerName, String targetServer) {
        sendPluginMessage(player, "PLAYER_TRANSFER_REQ", playerName, targetServer);
    }

    public void broadcastNickSync(String playerName, String nickname) {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier != null) {
            sendPluginMessage(carrier, "NICK_SYNC", playerName, nickname);
        }
    }

    public String stripColorAndFormatting(String input) {
        if (input == null) return "";
        String stripped = input.replaceAll("<[^>]*>", "");
        stripped = stripped.replaceAll("(?i)[&§][0-9a-fk-or]", "");
        return stripped;
    }

    public List<String> getNetworkNicknameSuggestions(String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        List<String> suggestions = new ArrayList<>();
        for (String realName : networkPlayers) {
            String rawNick = networkNicknames.get(realName.toLowerCase());
            String strippedNick = (rawNick != null) ? stripColorAndFormatting(rawNick) : realName;
            if (strippedNick.toLowerCase().startsWith(lowerPrefix)) {
                suggestions.add(strippedNick);
            }
        }
        return suggestions;
    }

    public List<String> getPlayersWithNickname(String nickname) {
        String lowerNick = nickname.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String realName : networkPlayers) {
            if (realName.equalsIgnoreCase(lowerNick)) {
                if (!matches.contains(realName)) matches.add(realName);
            }
            String rawNick = networkNicknames.get(realName.toLowerCase());
            String strippedNick = (rawNick != null) ? stripColorAndFormatting(rawNick) : realName;
            if (strippedNick.equalsIgnoreCase(lowerNick)) {
                if (!matches.contains(realName)) matches.add(realName);
            }
        }
        return matches;
    }

    public String getRawNickname(String realName) {
        return networkNicknames.get(realName.toLowerCase());
    }

    public void syncNicknamesFromDatabase() {
        if (networkPlayers.isEmpty()) return;
        plugin.getScheduler().runAsync(() -> {
            StringBuilder sb = new StringBuilder();
            List<String> params = new ArrayList<>();
            for (String name : networkPlayers) {
                sb.append("?,");
                params.add(name);
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
            String query = "SELECT name, nickname FROM nick_data WHERE name IN (" + sb.toString() + ")";
            plugin.getDatabase().querySafe(query, rs -> {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String nick = rs.getString("nickname");
                    if (name != null && nick != null) {
                        networkNicknames.put(name.toLowerCase(), nick);
                    }
                }
                return null;
            }, params.toArray());
        });
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "PLAYER_JOIN": {
                String name = in.readUTF();
                networkPlayers.add(name);
                break;
            }
            case "PLAYER_QUIT": {
                String name = in.readUTF();
                networkPlayers.remove(name);
                networkNicknames.remove(name.toLowerCase());
                break;
            }
            case "PLAYER_LIST_RESP": {
                String rawList = in.readUTF();
                networkPlayers.clear();
                if (!rawList.isEmpty()) {
                    for (String name : rawList.split(",")) {
                        networkPlayers.add(name);
                    }
                    syncNicknamesFromDatabase();
                }
                break;
            }
            case "NICK_SYNC": {
                String raw = in.readUTF();
                String[] split = raw.split(";", 2);
                if (split.length == 2) {
                    networkNicknames.put(split[0].toLowerCase(), split[1]);
                }
                break;
            }
            default: {
                if (plugin.getNetworkPacketHandler() != null) {
                    plugin.getNetworkPacketHandler().handle(player, subChannel, in);
                }
                break;
            }
        }
    }
}

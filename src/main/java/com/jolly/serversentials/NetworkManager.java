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
            requestPlayerList(player);
            listRequested = true;
        }
    }

    public void onPlayerQuitServer() {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            networkPlayers.clear();
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
                break;
            }
            case "PLAYER_LIST_RESP": {
                String rawList = in.readUTF();
                networkPlayers.clear();
                if (!rawList.isEmpty()) {
                    for (String name : rawList.split(",")) {
                        networkPlayers.add(name);
                    }
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

package com.jolly.serversentials.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

@Plugin(
        id = "serversentials",
        name = "Serversentials",
        version = "1.5",
        description = "Essential network utilities",
        authors = {"Jolly"}
)
public class ServersentialsVelocity {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("serversentials:channel");

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public ServersentialsVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        logger.info("Serversentials Velocity Proxy integration registered successfully!");
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        // Notify all servers of a player joining/switching servers to sync the tab-completion cache
        broadcastToAllServers("PLAYER_JOIN", player.getUsername());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        // Notify all servers of a player disconnecting from the network
        broadcastToAllServers("PLAYER_QUIT", player.getUsername());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equalsIgnoreCase("serversentials:channel")) {
            return;
        }

        // Prevent forwarding to client
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection serverSource)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subChannel = in.readUTF();

        switch (subChannel) {
            case "PLAYER_LIST_REQ": {
                // Server is requesting the complete network player list (e.g., on boot)
                String requesterServerName = serverSource.getServerInfo().getName();
                String playerList = proxy.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .collect(Collectors.joining(","));

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("PLAYER_LIST_RESP");
                out.writeUTF(playerList);
                serverSource.sendPluginMessage(CHANNEL, out.toByteArray());
                break;
            }

            case "FORWARD_TO_PLAYER": {
                String targetPlayerName = in.readUTF();
                String packetSubChannel = in.readUTF();
                byte[] payload = readRemainingBytes(in);

                Optional<Player> targetPlayer = proxy.getPlayer(targetPlayerName);
                if (targetPlayer.isPresent()) {
                    targetPlayer.get().getCurrentServer().ifPresent(srv -> {
                        srv.sendPluginMessage(CHANNEL, payload);
                    });
                }
                break;
            }

            case "FORWARD_TO_SERVER": {
                String targetServerName = in.readUTF();
                String packetSubChannel = in.readUTF();
                byte[] payload = readRemainingBytes(in);

                Optional<RegisteredServer> targetServer = proxy.getServer(targetServerName);
                if (targetServer.isPresent()) {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF(packetSubChannel);
                    out.write(payload);
                    targetServer.get().sendPluginMessage(CHANNEL, out.toByteArray());
                }
                break;
            }

            case "PLAYER_TRANSFER_REQ": {
                String playerName = in.readUTF();
                String targetServerName = in.readUTF();

                Optional<Player> targetPlayer = proxy.getPlayer(playerName);
                Optional<RegisteredServer> targetServer = proxy.getServer(targetServerName);

                if (targetPlayer.isPresent() && targetServer.isPresent()) {
                    targetPlayer.get().createConnectionRequest(targetServer.get()).fireAndForget();
                }
                break;
            }

            case "BROADCAST_STAFF": {
                String staffMessage = in.readUTF();
                // Broadcast to all backend servers
                broadcastToAllServers("BROADCAST_STAFF", staffMessage);
                break;
            }

            case "NICK_SYNC": {
                String playerName = in.readUTF();
                String nickname = in.readUTF();
                broadcastToAllServers("NICK_SYNC", playerName + ";" + nickname);
                break;
            }
        }
    }

    private void broadcastToAllServers(String subChannel, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        out.writeUTF(message);
        byte[] data = out.toByteArray();

        for (RegisteredServer srv : proxy.getAllServers()) {
            srv.sendPluginMessage(CHANNEL, data);
        }
    }

    private byte[] readRemainingBytes(ByteArrayDataInput in) {
        // Read remaining bytes safely
        List<Byte> bytes = new ArrayList<>();
        try {
            while (true) {
                bytes.add(in.readByte());
            }
        } catch (Exception e) {
            // EOF reached
        }
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            arr[i] = bytes.get(i);
        }
        return arr;
    }
}

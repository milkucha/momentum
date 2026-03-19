package io.github.milkucha.momentum;

import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.network.ServerKeyState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Common (both-sides) initializer for Momentum.
 *
 * Registers the C2S key-state packet receiver so the server knows which
 * Momentum keys each player is holding, enabling correct movement on
 * dedicated multiplayer servers.
 *
 * Also cleans up ServerKeyState when a player disconnects to avoid stale
 * entries causing phantom braking or drifting on respawn.
 */
public class Momentum implements ModInitializer {

    @Override
    public void onInitialize() {
        // Receive key state from client and store it per-player.
        // Runs on the Netty network thread — ServerKeyState uses ConcurrentHashMap.
        ServerPlayNetworking.registerGlobalReceiver(KeyStatePacket.ID,
            (server, player, handler, buf, responseSender) -> {
                KeyStatePacket pkt = KeyStatePacket.read(buf);
                ServerKeyState.set(player.getUuid(), pkt.brake, pkt.drift);
            });

        // Clear state on disconnect so a reconnecting player starts clean.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ServerKeyState.remove(handler.player.getUuid()));
    }
}

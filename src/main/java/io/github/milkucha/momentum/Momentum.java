package io.github.milkucha.momentum;

import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.network.ServerKeyState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
        // Register the payload type for the C2S key-state packet.
        PayloadTypeRegistry.playC2S().register(KeyStatePacket.ID, KeyStatePacket.CODEC);

        // Receive key state from client and store it per-player.
        // Runs on the Netty network thread — ServerKeyState uses ConcurrentHashMap.
        ServerPlayNetworking.registerGlobalReceiver(KeyStatePacket.ID, (payload, context) ->
            ServerKeyState.set(context.player().getUuid(), payload.brake(), payload.drift()));

        // Clear state on disconnect so a reconnecting player starts clean.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ServerKeyState.remove(handler.player.getUuid()));
    }
}

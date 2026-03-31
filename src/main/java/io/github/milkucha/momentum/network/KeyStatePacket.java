package io.github.milkucha.momentum.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S packet that carries the current state of the 2 Momentum keys:
 * brake and drift.
 *
 * Sent by the client in START_CLIENT_TICK whenever any key state changes.
 * Received on the server to populate ServerKeyState so AutomobileEntityMixin
 * can read the correct key state regardless of logical side.
 */
public record KeyStatePacket(boolean brake, boolean drift) implements CustomPayload {

    public static final CustomPayload.Id<KeyStatePacket> ID =
        new CustomPayload.Id<>(Identifier.of("momentum", "key_state"));

    public static final PacketCodec<PacketByteBuf, KeyStatePacket> CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, KeyStatePacket::brake,
        PacketCodecs.BOOL, KeyStatePacket::drift,
        KeyStatePacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package io.github.milkucha.momentum.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * C2S packet that carries the current state of the 2 Momentum keys:
 * brake and drift.
 *
 * Sent by the client in START_CLIENT_TICK whenever any key state changes.
 * Received on the server to populate ServerKeyState so AutomobileEntityMixin
 * can read the correct key state regardless of logical side.
 */
public final class KeyStatePacket {

    public static final Identifier ID = new Identifier("momentum", "key_state");

    public final boolean brake;
    public final boolean drift;

    public KeyStatePacket(boolean brake, boolean drift) {
        this.brake = brake;
        this.drift = drift;
    }

    public static KeyStatePacket read(PacketByteBuf buf) {
        return new KeyStatePacket(
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(brake);
        buf.writeBoolean(drift);
    }
}

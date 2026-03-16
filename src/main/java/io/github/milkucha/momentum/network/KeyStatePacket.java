package io.github.milkucha.momentum.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * C2S packet that carries the current state of the 5 Momentum keys:
 * brake (Space), J, K, N, M.
 *
 * Sent by the client in START_CLIENT_TICK whenever any key state changes.
 * Received on the server to populate ServerKeyState so AutomobileEntityMixin
 * can read the correct key state regardless of logical side.
 */
public final class KeyStatePacket {

    public static final Identifier ID = new Identifier("momentum", "key_state");

    public final boolean brake;
    public final boolean j;
    public final boolean k;
    public final boolean n;
    public final boolean m;

    public KeyStatePacket(boolean brake, boolean j, boolean k, boolean n, boolean m) {
        this.brake = brake;
        this.j = j;
        this.k = k;
        this.n = n;
        this.m = m;
    }

    public static KeyStatePacket read(PacketByteBuf buf) {
        return new KeyStatePacket(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(brake);
        buf.writeBoolean(j);
        buf.writeBoolean(k);
        buf.writeBoolean(n);
        buf.writeBoolean(m);
    }
}

package io.github.milkucha.momentum.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side store of key states per player UUID.
 *
 * Written by the KeyStatePacket receiver (network thread) — ConcurrentHashMap
 * ensures safe cross-thread access without locks.
 * Read by AutomobileEntityMixin on the server logical side to drive movement.
 * Cleaned up on player disconnect via ServerPlayConnectionEvents.DISCONNECT.
 *
 * Index mapping: [0]=brake [1]=j [2]=k [3]=n [4]=m
 */
public final class ServerKeyState {

    private static final Map<UUID, boolean[]> STATES = new ConcurrentHashMap<>();

    private ServerKeyState() {}

    public static void set(UUID playerId, boolean brake, boolean j, boolean k, boolean n, boolean m) {
        STATES.put(playerId, new boolean[]{brake, j, k, n, m});
    }

    public static boolean getBrake(UUID playerId) { return get(playerId, 0); }
    public static boolean getJ(UUID playerId)     { return get(playerId, 1); }
    public static boolean getK(UUID playerId)     { return get(playerId, 2); }
    public static boolean getN(UUID playerId)     { return get(playerId, 3); }
    public static boolean getM(UUID playerId)     { return get(playerId, 4); }

    public static void remove(UUID playerId) {
        STATES.remove(playerId);
    }

    private static boolean get(UUID id, int idx) {
        boolean[] s = STATES.get(id);
        return s != null && s[idx];
    }
}

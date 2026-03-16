package io.github.milkucha.momentum;

/**
 * Client-side drift key state for Momentum.
 *
 * driftKeyHeld is written by START_CLIENT_TICK (client thread) and read by
 * AutomobileEntityMixin.momentum$jDriftTick which runs on both the client
 * entity and the integrated-server entity. volatile ensures cross-thread
 * visibility, matching the pattern used by MomentumBrakeState.
 *
 * prevDriftKeyHeld is intentionally NOT here — rising/falling edge tracking
 * is done via a @Unique instance field (momentum$prevDriftKeyHeld) on each
 * AutomobileEntity instance, so client and server entities track their own
 * edges independently and never race on a shared static.
 */
public class MomentumDriftState {
    // J key — Momentum transplanted drift
    public static volatile boolean driftKeyHeld = false;

    // K key — Momentum arcade drift (slip-angle simulation)
    // prevKDriftKeyHeld is intentionally NOT here — edge tracking is done via a
    // @Unique instance field on each AutomobileEntity, matching the J-key pattern.
    public static volatile boolean kDriftKeyHeld = false;

    // N key — brake-then-drift (same drift logic as J, gated behind a brake phase)
    public static volatile boolean nDriftKeyHeld = false;

    // M key — combined: brake when steering=0, K-drift when steering≠0
    public static volatile boolean mKeyHeld = false;

    // O key — profile selector: delegates to J, K, or M based on config
    public static volatile boolean oKeyHeld = false;
}

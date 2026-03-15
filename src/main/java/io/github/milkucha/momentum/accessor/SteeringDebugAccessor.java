package io.github.milkucha.momentum.accessor;

/**
 * Exposes private steering state from AutomobileEntity for the debug HUD overlay.
 * AutomobileEntityMixin implements this interface via @Unique methods.
 * Cast any AutomobileEntity to SteeringDebugAccessor to read values.
 */
public interface SteeringDebugAccessor {
    float momentum$getSteering();
    float momentum$getHSpeed();
    float momentum$getAngularSpeed();
    boolean momentum$isDrifting();
    boolean momentum$isHoldingDrift();
    boolean momentum$isAccelerating();
    boolean momentum$isBraking();
    boolean momentum$isSteeringLeft();
    boolean momentum$isSteeringRight();
    int momentum$getTurboCharge();
    boolean momentum$isOnGround();
    boolean momentum$isKDriftActive();
    float   momentum$getKDriftOffset();
}

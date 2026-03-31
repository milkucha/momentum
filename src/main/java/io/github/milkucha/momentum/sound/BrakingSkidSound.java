package io.github.milkucha.momentum.sound;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumBrakeState;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Looping skid sound that plays while the player brakes (Space held) and
 * engineSpeed is positive. Stops itself the moment either condition is false.
 *
 * Uses Automobility's existing skid sound event (automobility:entity.automobile.skid).
 * Pitch scales with hSpeed so the screech naturally lowers as the car slows.
 */
public class BrakingSkidSound extends MovingSoundInstance {

    private static final Identifier SKID_ID =
            Identifier.of("automobility", "entity.automobile.skid");

    private final AutomobileEntity automobile;

    public BrakingSkidSound(AutomobileEntity automobile) {
        super(resolveSound(), SoundCategory.AMBIENT, Random.create());
        this.automobile = automobile;
        this.repeat = true;
        this.repeatDelay = 0;
        this.x = automobile.getX();
        this.y = automobile.getY();
        this.z = automobile.getZ();
        this.volume = 1.0f;
        this.pitch  = 1.0f;
    }

    private static SoundEvent resolveSound() {
        SoundEvent event = Registries.SOUND_EVENT.get(SKID_ID);
        return event != null ? event : SoundEvent.of(SKID_ID);
    }

    @Override
    public void tick() {
        if (automobile.isRemoved()) {
            setDone();
            return;
        }

        float hSpeed = automobile.getHSpeed();

        // Stop when braking ends or speed has reached zero / gone into reverse
        if (!MomentumBrakeState.brakeHeld || hSpeed <= 0f) {
            setDone();
            return;
        }

        this.x = automobile.getX();
        this.y = automobile.getY();
        this.z = automobile.getZ();

        // Pitch scales with speed: full screech at top speed, lower as car slows
        this.pitch  = 0.7f + 0.5f * Math.min(hSpeed / 0.5f, 1.0f);
        this.volume = ((net.minecraft.entity.Entity) automobile).isOnGround() ? 1.0f : 0.0f;
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }
}

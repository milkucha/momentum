package io.github.milkucha.momentum.sound;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.util.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Looping skid sound that plays while Responsive Drift is active.
 * Stops itself when responsiveDriftActive becomes false or the entity is removed.
 * Pitch scales with hSpeed so the screech naturally lowers as the car slows.
 */
public class ResponsiveDriftSkidSound extends MovingSoundInstance {

    private static final Identifier SKID_ID =
            new Identifier("automobility", "entity.automobile.skid");

    private final AutomobileEntity automobile;
    private final SteeringDebugAccessor accessor;

    public ResponsiveDriftSkidSound(AutomobileEntity automobile) {
        super(resolveSound(), SoundCategory.AMBIENT, Random.create());
        this.automobile = automobile;
        this.accessor = (SteeringDebugAccessor) automobile;
        this.repeat = true;
        this.repeatDelay = 0;
        this.x = automobile.getX();
        this.y = automobile.getY();
        this.z = automobile.getZ();
        this.volume = 1.0f;
        this.pitch  = 1.0f;
    }

    private static SoundEvent resolveSound() {
        SoundEvent event = Registry.SOUND_EVENT.get(SKID_ID);
        return event != null ? event : new SoundEvent(SKID_ID);
    }

    @Override
    public void tick() {
        if (automobile.isRemoved()) {
            setDone();
            return;
        }

        if (!accessor.momentum$isResponsiveDriftActive()) {
            setDone();
            return;
        }

        this.x = automobile.getX();
        this.y = automobile.getY();
        this.z = automobile.getZ();

        float hSpeed = automobile.getHSpeed();
        this.pitch  = 0.7f + 0.5f * Math.min(hSpeed / 0.5f, 1.0f);
        this.volume = 1.0f;
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }
}

package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved client-only mixin targeting AutomobileEntity.
 * H-key diagnostic removed — J-key drift is now in AutomobileEntityMixin.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public class AutomobileBrakeMixin {
}

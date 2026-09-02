package dev.elytracombat.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the rocket's attached rider so the G monitor can tell when a player is boosting:
 * while a firework carries a player, that player is immune to G-force.
 */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketAccessor {
    @Accessor("attachedToEntity")
    LivingEntity elytraCombat$getAttachedTo();
}

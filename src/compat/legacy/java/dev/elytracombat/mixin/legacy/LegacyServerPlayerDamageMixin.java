package dev.elytracombat.mixin.legacy;

import dev.elytracombat.DamageFilter;
import dev.elytracombat.ElytraCooldowns;
import dev.elytracombat.Freefall;
import dev.elytracombat.GForce;
import dev.elytracombat.PhysicalDamage;
import dev.elytracombat.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21/1.21.1 flavor of the damage hooks. On this generation {@code ServerPlayer} does not
 * declare its own {@code hurt}/{@code die} (no {@code hurtServer} split yet), so the
 * injections target {@code LivingEntity} and filter for server players. Everything else is
 * identical to the modern {@code dev.elytracombat.mixin.ServerPlayerDamageMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LegacyServerPlayerDamageMixin {
    @Unique private ItemStack elytraCombat$wornElytra = ItemStack.EMPTY;
    @Unique private float elytraCombat$healthBefore;
    @Unique private float elytraCombat$absorptionBefore;
    @Unique private boolean elytraCombat$wasFallFlying;
    @Unique private Vec3 elytraCombat$velocityBefore;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void elytraCombat$captureDamage(DamageSource source, float amount,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer self)) {
            return;
        }
        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        elytraCombat$wornElytra = ElytraCooldowns.isElytra(chest) ? chest : ItemStack.EMPTY;
        elytraCombat$healthBefore = self.getHealth();
        elytraCombat$absorptionBefore = self.getAbsorptionAmount();
        elytraCombat$wasFallFlying = self.isFallFlying();
        // Captured before the hit: the knockback is the first G spike of the fall.
        elytraCombat$velocityBefore = self.getDeltaMovement();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void elytraCombat$applyDamageCooldown(DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer self)) {
            return;
        }
        float healthLost = Math.max(0.0F, elytraCombat$healthBefore - self.getHealth());
        float absorptionLost = Math.max(0.0F, elytraCombat$absorptionBefore - self.getAbsorptionAmount());
        boolean acceptedDamage = ConfigManager.get().absorptionBypassesDisable
                ? healthLost > 0.0F
                : healthLost > 0.0F || absorptionLost > 0.0F;

        // isAlive: die() runs inside hurtServer and clears the markers, so without this guard
        // the fatal hit would freshly disable the (already cleaned) elytra of the corpse.
        boolean fallDamageIgnored = ConfigManager.get().damageFilter.ignoreFallDamage
                && source.is(DamageTypeTags.IS_FALL);

        if (self.isAlive() && acceptedDamage && !fallDamageIgnored
                && !elytraCombat$wornElytra.isEmpty() && DamageFilter.shouldTrigger(source)
                && !ElytraCooldowns.isDisabled(self.level(), elytraCombat$wornElytra)) {
            // Fresh disable only: hits never extend an ongoing cooldown. This includes the
            // mod's own G-force damage (elytra_combat:g_force): trauma past g_force.threshold_gs
            // can disable a working elytra on its own, mid-maneuver or on a hard landing.
            if (PhysicalDamage.isExternalPhysical(source)) {
                ElytraCooldowns.damageElytra(self, elytraCombat$wornElytra, healthLost + absorptionLost);
            }
            if (ElytraCooldowns.disable(self, elytraCombat$wornElytra) && elytraCombat$wasFallFlying) {
                Freefall.beginMidFlight(self, elytraCombat$velocityBefore);
                self.stopFallFlying();
            }
        }

        elytraCombat$wornElytra = ItemStack.EMPTY;
        elytraCombat$wasFallFlying = false;
        elytraCombat$velocityBefore = Vec3.ZERO;
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void elytraCombat$clearOnDeath(DamageSource source, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer self)) {
            return;
        }
        ElytraCooldowns.clearAll(self);
        Freefall.clear(self);
        GForce.clear(self);
    }
}

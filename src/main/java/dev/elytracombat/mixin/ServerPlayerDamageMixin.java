package dev.elytracombat.mixin;

import dev.elytracombat.DamageFilter;
import dev.elytracombat.ElytraCooldowns;
import dev.elytracombat.Freefall;
import dev.elytracombat.PhysicalDamage;
import dev.elytracombat.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDamageMixin {
    @Unique private ItemStack elytraCombat$wornElytra = ItemStack.EMPTY;
    @Unique private float elytraCombat$healthBefore;
    @Unique private float elytraCombat$absorptionBefore;
    @Unique private boolean elytraCombat$wasFallFlying;
    @Unique private Vec3 elytraCombat$velocityBefore;

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void elytraCombat$captureDamage(ServerLevel level, DamageSource source, float amount,
                                            CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        elytraCombat$wornElytra = ElytraCooldowns.isElytra(chest) ? chest : ItemStack.EMPTY;
        elytraCombat$healthBefore = self.getHealth();
        elytraCombat$absorptionBefore = self.getAbsorptionAmount();
        elytraCombat$wasFallFlying = self.isFallFlying();
        // Captured before the hit: the knockback is the first G spike of the fall.
        elytraCombat$velocityBefore = self.getDeltaMovement();
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void elytraCombat$applyDamageCooldown(ServerLevel level, DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
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
                && !ElytraCooldowns.isDisabled(level, elytraCombat$wornElytra)) {
            // Fresh disable only: hits never extend an ongoing cooldown, and damage the mod
            // itself deals (G-force) lands on an already disabled elytra, so it is ignored here.
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
        ServerPlayer self = (ServerPlayer) (Object) this;
        ElytraCooldowns.clearAll(self);
        Freefall.clear(self);
    }
}

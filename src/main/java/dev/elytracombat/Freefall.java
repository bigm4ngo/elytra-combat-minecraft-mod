package dev.elytracombat;

import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs the disorientation that follows a mid-flight disable: the shock at the moment of
 * the hit, and the effects that plague the initial fall.
 *
 * <p>A free fall session begins only when a worn elytra is disabled while its wearer is
 * flying (shot down, or swapping in an already disabled elytra mid-air). Sessions end on
 * landing, in water or lava, when the elytra stops being disabled, or on death. Falling
 * off something later with a disabled elytra equipped is an ordinary fall: no nausea, no
 * tumble - only the G-force system (if enabled) still applies to those.
 *
 * <p>Vanilla blends nausea in over 150 ticks and blends it out during the last 60 ticks of
 * an instance, so refreshing short instances every tick (the old behavior) held the blend
 * factor at zero and the effect never really appeared. Instead one long instance is applied
 * at the shock and re-applied only while it is about to enter its blend-out window, which
 * keeps the distortion at full strength for the whole fall without visible re-ramps.
 */
public final class Freefall {
    /** Re-apply nausea before the remaining duration enters its 60 tick blend-out window. */
    private static final int NAUSEA_BLEND_OUT_ADVANCE_TICKS = 60;
    private static final int DARKNESS_BLEND_OUT_ADVANCE_TICKS = 22;
    private static final int DARKNESS_DURATION_TICKS = 100;
    /** G-force damage is applied in batches to work with the damage cooldown instead of against it. */
    private static final int G_FORCE_APPLICATION_INTERVAL_TICKS = 10;

    private static final Map<ServerPlayer, FallState> STATES = new WeakHashMap<>();

    private Freefall() {
    }

    private static final class FallState {
        boolean midFlight;
        double impactSpeed;
        double gBuffer;
        int ticksSinceGForceApplied;
        SpinModel.TurnRates turnRates;
    }

    /** Starts a mid-flight session and applies the shock effects. Call while the player is still flying. */
    public static void beginMidFlight(ServerPlayer player) {
        FallState state = new FallState();
        state.midFlight = true;
        state.impactSpeed = Mth.clamp(player.getDeltaMovement().length(), 0.0, SpinModel.MAX_IMPACT_SPEED);
        STATES.put(player, state);

        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        if (config.nauseaStrength > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                    config.nauseaDurationSeconds * 20, config.nauseaStrength - 1, false, true, true));
        }
        if (config.darkness) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, true, true));
        }
        if (config.shockSound) {
            player.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 1.0F);
        }
        if (config.viewSnap) {
            float yawOffset = (player.getRandom().nextFloat() * 280.0F) - 140.0F;
            float pitchOffset = (player.getRandom().nextFloat() * 70.0F) - 45.0F;
            player.forceSetRotation(player.getYRot() + yawOffset, false,
                    Mth.clamp(player.getXRot() + pitchOffset, -90.0F, 90.0F), false);
        }
        // Darkness is deliberately not applied here: it is gated on the G load of the fall
        // itself (refreshDarkness), so slow falls stay clear while fast ones black out.
        state.turnRates = SpinModel.sample(state.impactSpeed, config.spinIntensity, player.getRandom()::nextDouble);
    }

    public static void tick(ServerPlayer player) {
        ElytraCooldowns.tickExpired(player);

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean disabled = ElytraCooldowns.isDisabled(player.level(), chest);
        FallState state = STATES.get(player);

        if (state != null && (!disabled || player.isDeadOrDying() || player.isRemoved())) {
            STATES.remove(player);
            state = null;
        }

        // Flying with a disabled elytra on (e.g. swapping it in mid-air) ends the flight
        // and counts as a mid-flight disable.
        if (disabled && player.isFallFlying()) {
            if (state == null) {
                beginMidFlight(player);
                state = STATES.get(player);
            }
            player.stopFallFlying();
        }

        if (disabled && isFreefalling(player)) {
            if (state != null && state.midFlight) {
                refreshNausea(player);
                refreshDarkness(player);
                applySpin(player, state);
            }
            applyGForce(player, state);
        } else if (state != null) {
            // The initial fall is over (landing, water, ladder, ...). The tumble ends here;
            // any later fall is an ordinary one and only G-force can apply.
            STATES.remove(player);
        }
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player);
    }

    private static boolean isFreefalling(ServerPlayer player) {
        return !player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && !player.isFallFlying()
                && !player.onClimbable()
                && player.getDeltaMovement().y < -0.05;
    }

    private static void refreshNausea(ServerPlayer player) {
        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        if (config.nauseaStrength <= 0) {
            return;
        }
        MobEffectInstance active = player.getEffect(MobEffects.NAUSEA);
        if (active == null || active.getDuration() <= NAUSEA_BLEND_OUT_ADVANCE_TICKS + 10
                || active.getAmplifier() < config.nauseaStrength - 1) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                    config.nauseaDurationSeconds * 20, config.nauseaStrength - 1, false, true, true));
        }
    }

    private static void refreshDarkness(ServerPlayer player) {
        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        if (!config.darkness) {
            return;
        }
        double downwardSpeed = Math.max(0.0, -player.getDeltaMovement().y);
        double gs = GForceMath.gsFor(downwardSpeed, ConfigManager.get().gForce.speedToGs);
        if (gs < config.darknessThresholdGs) {
            return;
        }
        MobEffectInstance active = player.getEffect(MobEffects.DARKNESS);
        if (active == null || active.getDuration() <= DARKNESS_BLEND_OUT_ADVANCE_TICKS + 10) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, true, true));
        }
    }

    private static void applySpin(ServerPlayer player, FallState state) {
        SpinModel.TurnRates rates = state.turnRates;
        if (rates == null) {
            return;
        }
        if (rates.expired()) {
            state.turnRates = null;
            return;
        }
        double nextPitch = player.getXRot() + rates.pitchRate();
        double pitchRate = nextPitch > 90.0 || nextPitch < -90.0 ? 0.0 : rates.pitchRate();
        player.forceSetRotation(player.getYRot() + (float) rates.yawRate(), true,
                player.getXRot() + (float) pitchRate, true);
        state.turnRates = rates.decayed();
    }

    private static void applyGForce(ServerPlayer player, FallState state) {
        ElytraCombatConfig.GForce config = ConfigManager.get().gForce;
        if (!config.enabled) {
            return;
        }
        if (state == null) {
            // Ordinary fall with a disabled elytra equipped: G-force still tracks it.
            state = new FallState();
            STATES.put(player, state);
        }

        double downwardSpeed = Math.max(0.0, -player.getDeltaMovement().y);
        double gs = GForceMath.gsFor(downwardSpeed, config.speedToGs);
        state.gBuffer += GForceMath.damagePerTick(gs, config.thresholdGs, config.damagePerGsPerSecond);
        state.ticksSinceGForceApplied++;

        if (state.ticksSinceGForceApplied >= G_FORCE_APPLICATION_INTERVAL_TICKS && state.gBuffer >= 1.0F) {
            DamageSource gForce = player.damageSources().flyIntoWall();
            player.hurtServer(player.level(), gForce, (float) state.gBuffer);
            state.gBuffer = 0.0;
            state.ticksSinceGForceApplied = 0;
        }
    }
}

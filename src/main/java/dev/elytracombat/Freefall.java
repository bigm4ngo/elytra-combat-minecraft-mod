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
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs the disorientation that follows a mid-flight disable: the shock at the moment of
 * the hit, and the effects that plague the initial fall.
 *
 * <p>A free fall session begins only when a worn elytra is disabled while its wearer is
 * flying (shot down, or swapping in an already disabled elytra mid-air). Sessions end on
 * landing, in water or lava, when the elytra stops being disabled, or on death, and then
 * enter a short settle window: nausea is swapped for an instance that blends out over the
 * window instead of running its full length, and the G load of the ending itself (the
 * impact of landing, or snapping a fresh elytra open mid-fall) is still measured. Falling
 * off something later with a disabled elytra equipped is an ordinary fall: no nausea, no
 * tumble, no darkness - only the G-force system (if enabled) still applies to those.
 *
 * <p>G load is the smoothed magnitude of the per-tick velocity change, so it spikes with
 * sudden events (the hit that starts the fall, the landing, re-engaging flight) and fades
 * as a steady fall cancels gravity out at terminal velocity. Darkness follows that load,
 * which is why a victim blacks out as they are shot and clears up again as they approach
 * terminal velocity.
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
    /** Darkness refreshes after the fall has ended stay brief so a hard landing only flashes. */
    private static final int DARKNESS_SETTLE_DURATION_TICKS = 40;
    /** Once the load drops below the threshold the remaining blackout fades out over this. */
    private static final int DARKNESS_FADE_DURATION_TICKS = 30;
    /** G-force damage is applied in batches to work with the damage cooldown instead of against it. */
    private static final int G_FORCE_APPLICATION_INTERVAL_TICKS = 10;
    /** Raw per-tick velocity changes are clamped to this; anything larger is a glitch, not a fall. */
    private static final double MAX_DELTA_PER_TICK = 6.0;
    /** A one-tick position jump bigger than this is a teleport: G tracking resets instead of spiking. */
    private static final double TELEPORT_GUARD_BLOCKS = 16.0;

    private static final Map<ServerPlayer, FallState> STATES = new WeakHashMap<>();

    private Freefall() {
    }

    private static final class FallState {
        boolean midFlight;
        double impactSpeed;
        double gBuffer;
        int ticksSinceGForceApplied;
        SpinModel.TurnRates turnRates;
        /** Velocity from the previous tick, the baseline of the per-tick velocity change. */
        Vec3 previousVelocity;
        Vec3 previousPosition;
        double smoothedDelta;
        /** The smoothed load of the current tick, also used to gate darkness. */
        double currentGs;
        /** Remaining settle ticks after the fall ended; negative while the session is active. */
        int settleTicks = -1;

        boolean settling() {
            return settleTicks >= 0;
        }
    }

    /**
     * Starts a mid-flight session and applies the shock effects. Call while the player is
     * still flying; {@code eventVelocity} is the velocity from just before the disabling
     * event, so the knockback of the hit itself registers as the first G spike.
     */
    public static FallState beginMidFlight(ServerPlayer player, Vec3 eventVelocity) {
        FallState state = new FallState();
        state.midFlight = true;
        state.impactSpeed = Mth.clamp(eventVelocity.length(), 0.0, SpinModel.MAX_IMPACT_SPEED);
        state.previousVelocity = eventVelocity;
        state.previousPosition = player.position();
        STATES.put(player, state);

        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        if (config.nauseaStrength > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                    config.nauseaDurationSeconds * 20, config.nauseaStrength - 1, false, true, true));
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
        // Darkness is deliberately not applied here: it is gated on the G load (refreshDarkness),
        // which spikes with the hit itself and fades as terminal velocity cancels gravity out.
        state.turnRates = SpinModel.sample(state.impactSpeed, config.spinIntensity, player.getRandom()::nextDouble);
        return state;
    }

    public static void tick(ServerPlayer player) {
        ElytraCooldowns.tickExpired(player);

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean disabled = ElytraCooldowns.isDisabled(player.level(), chest);
        FallState state = STATES.get(player);

        if (state != null && (player.isDeadOrDying() || player.isRemoved())) {
            STATES.remove(player);
            return;
        }

        if (state != null && state.settling()) {
            // The fall is over; effects wind down while sudden changes still count.
            if (disabled && player.isFallFlying()) {
                // A disabled elytra was swapped in mid-air again: a fresh shock session.
                state = beginMidFlight(player, player.getDeltaMovement());
                player.stopFallFlying();
            } else if (disabled && isFreefalling(player)) {
                // Fell again wearing the still disabled elytra: an ordinary fall from here on.
                state.settleTicks = -1;
                state.midFlight = false;
            } else {
                state.settleTicks--;
                if (state.settleTicks < 0) {
                    flushGForce(player, state);
                    STATES.remove(player);
                    return;
                }
            }
        } else {
            if (state != null && (!disabled || fallOver(player))) {
                // Landing, water, climbing, or the disabled elytra leaving the chest slot.
                enterSettle(player, state);
            }
            // Flying with a disabled elytra on (e.g. swapping it in mid-air) ends the flight
            // and counts as a mid-flight disable.
            if (disabled && player.isFallFlying()) {
                state = beginMidFlight(player, player.getDeltaMovement());
                player.stopFallFlying();
            } else if (state == null && disabled && isFreefalling(player)) {
                // Ordinary fall wearing a disabled elytra: only the G-force system applies.
                state = new FallState();
                state.previousVelocity = player.getDeltaMovement();
                state.previousPosition = player.position();
                STATES.put(player, state);
            }
        }

        if (state == null) {
            return;
        }

        trackGForce(player, state);
        if (state.settling()) {
            if (state.midFlight) {
                refreshDarkness(player, state, true);
            }
        } else if (state.midFlight) {
            refreshNausea(player);
            applySpin(player, state);
            refreshDarkness(player, state, false);
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

    /** The fall ended because the victim reached something solid, liquid, or climbable. */
    private static boolean fallOver(ServerPlayer player) {
        return player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable();
    }

    /**
     * Winds the session down: the nausea left over from the fall is replaced by an instance
     * sized to the settle window, so the distortion blends out shortly after landing or
     * stabilizing flight instead of persisting for its full configured length.
     */
    private static void enterSettle(ServerPlayer player, FallState state) {
        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        state.settleTicks = config.settleSeconds * 20;
        if (state.midFlight && config.nauseaStrength > 0 && player.hasEffect(MobEffects.NAUSEA)) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                    state.settleTicks, config.nauseaStrength - 1, false, true, true));
        }
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

    private static void refreshDarkness(ServerPlayer player, FallState state, boolean settling) {
        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        if (!config.darkness) {
            return;
        }
        MobEffectInstance active = player.getEffect(MobEffects.DARKNESS);
        if (state.currentGs >= config.darknessThresholdGs) {
            int duration = settling ? DARKNESS_SETTLE_DURATION_TICKS : DARKNESS_DURATION_TICKS;
            if (active == null || active.getDuration() <= DARKNESS_BLEND_OUT_ADVANCE_TICKS + 10
                    || active.getDuration() > duration) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, true, true));
            }
        } else if (active != null && active.getDuration() > DARKNESS_FADE_DURATION_TICKS) {
            // The load dropped (terminal velocity reached, or the impact was soft): fade now
            // instead of letting a full-length instance pulse on for another few seconds.
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_FADE_DURATION_TICKS, 0, false, true, true));
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

    /**
     * Measures the per-tick velocity change and feeds the G-force model. The load is always
     * tracked (darkness is gated on it), but damage only accrues when the G-force system is
     * enabled. Runs during the settle window too, so landing and re-engaging flight count.
     */
    private static void trackGForce(ServerPlayer player, FallState state) {
        Vec3 velocity = player.getDeltaMovement();
        if (state.previousVelocity == null) {
            state.previousVelocity = velocity;
            state.previousPosition = player.position();
            return;
        }
        Vec3 position = player.position();
        if (state.previousPosition != null && state.previousPosition.distanceTo(position) > TELEPORT_GUARD_BLOCKS) {
            // Teleported (command, chorus fruit, portal): velocity continuity is gone.
            state.previousVelocity = velocity;
            state.previousPosition = position;
            state.smoothedDelta = 0.0;
            state.currentGs = 0.0;
            return;
        }

        double rawDelta = Math.min(velocity.subtract(state.previousVelocity).length(), MAX_DELTA_PER_TICK);
        state.smoothedDelta = GForceMath.smooth(state.smoothedDelta, rawDelta, GForceMath.SMOOTHING_ALPHA);
        state.currentGs = GForceMath.gsForDelta(state.smoothedDelta, ConfigManager.get().gForce.deltaToGs);
        state.previousVelocity = velocity;
        state.previousPosition = position;

        ElytraCombatConfig.GForce config = ConfigManager.get().gForce;
        if (!config.enabled) {
            return;
        }
        state.gBuffer += GForceMath.damagePerTick(state.currentGs, config.thresholdGs, config.damagePerGsPerSecond);
        state.ticksSinceGForceApplied++;

        if (state.ticksSinceGForceApplied >= G_FORCE_APPLICATION_INTERVAL_TICKS && state.gBuffer >= 1.0F) {
            DamageSource gForce = player.damageSources().flyIntoWall();
            player.hurtServer(player.level(), gForce, (float) state.gBuffer);
            state.gBuffer = 0.0;
            state.ticksSinceGForceApplied = 0;
        }
    }

    /** Applies any accrued G damage left in the buffer when the session is discarded. */
    private static void flushGForce(ServerPlayer player, FallState state) {
        if (ConfigManager.get().gForce.enabled && state.gBuffer >= 1.0F) {
            DamageSource gForce = player.damageSources().flyIntoWall();
            player.hurtServer(player.level(), gForce, (float) state.gBuffer);
        }
        state.gBuffer = 0.0;
        state.ticksSinceGForceApplied = 0;
    }
}

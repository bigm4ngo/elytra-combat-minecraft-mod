package dev.elytracombat;

import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import dev.elytracombat.mixin.FireworkRocketAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The always-on G-force monitor. Every server tick it measures the smoothed change in
 * velocity of every player and converts it to a G load, regardless of whether an elytra
 * was disabled - so the knockback of a hit, a hard landing, snapping a fresh elytra open
 * mid-fall, an intense elytra maneuver, and the acceleration of a fresh free fall all
 * register the same way. Only the context is gated, not the session: a player counts as
 * loaded while they are gliding, or while airborne wearing an elytra during a fall that
 * reached flight-like speed. Walking, hopping, climbing, swimming, and ordinary short
 * drops stay outside the model.
 *
 * <p>Above {@code g_force.effect_threshold_gs} the load pulses darkness and nausea, and the
 * nausea keeps refreshing for as long as the player has not stabilised - high load, or
 * simply still falling unstabilised. Once the player lands or glides it off, the sustained
 * nausea is cleared shortly after. Above {@code g_force.threshold_gs} the load also deals
 * damage, and because that damage runs through the regular hurt pipeline the damage filter
 * sees it as {@code elytra_combat:g_force} - a fresh disable (and its mid-flight shock) can
 * therefore come from G-force alone. Rockets are exempt across the board: while a firework
 * is boosting a player there is no G-force for them at all, and a low-speed ground takeoff
 * still opens its own short grace window.
 *
 * <p>Vanilla blends nausea in over 150 ticks and darkness over 22, so instances are kept
 * longer than the blend-out windows and simply re-applied while the load persists - the
 * blend factor carries across re-applications, which keeps the distortion steady without
 * visible re-ramps and lets it finish quickly once the load clears.
 */
public final class GForce {
    /** Registry key of the custom damage type; the death message lives in the mod's lang file. */
    public static final ResourceKey<DamageType> G_FORCE_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(ElytraCombat.MOD_ID, "g_force"));

    /** G-force damage is applied in batches to work with the damage cooldown instead of against it. */
    private static final int APPLICATION_INTERVAL_TICKS = 10;
    /** Raw per-tick velocity changes are clamped to this; anything larger is a glitch, not a maneuver. */
    private static final double MAX_DELTA_PER_TICK = 6.0;
    /** A one-tick position jump bigger than this is a teleport: tracking resets instead of spiking. */
    private static final double TELEPORT_GUARD_BLOCKS = 16.0;
    /** A fall only starts counting as loaded once it drops faster than this (roughly an 8 block drop). */
    private static final double ELIGIBILITY_FALL_SPEED = 1.0;
    /**
     * Shortest nausea application that can hold a distortion at all: instances at or under
     * vanilla's 60 tick blend-out advance are permanently in blend-out and bleed to zero no
     * matter how often they are refreshed. 90 ticks keeps short configured values visible.
     */
    private static final int NAUSEA_MIN_INSTANCE_TICKS = 90;
    /** Re-apply before the remaining nausea enters its 60 tick blend-out advance window. */
    private static final int NAUSEA_REFRESH_ADVANCE_TICKS = 70;
    /** How long the load may stay below the effect threshold before sustained nausea is cleared. */
    private static final int NAUSEA_CLEAR_DELAY_TICKS = 10;
    private static final int DARKNESS_DURATION_TICKS = 40;
    private static final int DARKNESS_BLEND_OUT_ADVANCE_TICKS = 22;
    /**
     * Takeoff exception: starting a glide from the ground yanks the jump velocity onto the
     * look direction within a few ticks, which reads as a large load that no pilot feels.
     * Low-speed takeoffs open this grace window during which no G effects or damage apply.
     */
    private static final int TAKEOFF_GRACE_TICKS = 15;
    /** A takeoff only counts as a ground takeoff (and gets grace) below this speed. */
    private static final double TAKEOFF_GRACE_SPEED = 1.0;

    private static final Map<ServerPlayer, Track> TRACKS = new WeakHashMap<>();

    private GForce() {
    }

    private static final class Track {
        Vec3 previousVelocity;
        Vec3 previousPosition;
        double smoothedDelta;
        double currentGs;
        /** Fastest downward speed seen during the current air phase; resets on ground, water, and ladders. */
        double peakDownwardSpeed;
        double gBuffer;
        int ticksSinceApplied;
        boolean wasFallFlying;
        int takeoffGraceTicks;
        /** True while the nausea on this player was applied by the mod and is being sustained. */
        boolean nauseaSustained;
        int ticksBelowEffectThreshold;
    }

    public static void tick(ServerPlayer player) {
        if (player.isDeadOrDying() || player.isRemoved()) {
            TRACKS.remove(player);
            return;
        }
        Track track = TRACKS.computeIfAbsent(player, p -> new Track());

        Vec3 velocity = player.getDeltaMovement();
        if (track.previousVelocity == null) {
            seed(track, velocity, player.position(), player.isFallFlying());
            return;
        }
        Vec3 position = player.position();
        if (track.previousPosition != null && track.previousPosition.distanceTo(position) > TELEPORT_GUARD_BLOCKS) {
            // Teleported (command, chorus fruit, portal): velocity continuity is gone.
            seed(track, velocity, position, player.isFallFlying());
            return;
        }

        boolean fallFlying = player.isFallFlying();

        // Rocket immunity: while a firework carries this player, no G effects or damage
        // apply at all, and the model is pinned to zero so boost climbs and turns never
        // leak into the load after the boost burns out.
        if (fallFlying && isRocketBoosting(player)) {
            track.previousVelocity = velocity;
            track.previousPosition = position;
            track.smoothedDelta = 0.0;
            track.currentGs = 0.0;
            track.takeoffGraceTicks = 0;
            track.wasFallFlying = true;
            return;
        }

        boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable();
        // Eligibility is decided before the peak resets, so the impact tick of a real fall
        // still counts while hops and short drops (peak below the bar) never do.
        boolean eligible = fallFlying
                || (!grounded && wearsElytra(player) && track.peakDownwardSpeed >= ELIGIBILITY_FALL_SPEED);
        if (grounded) {
            track.peakDownwardSpeed = 0.0;
        } else {
            track.peakDownwardSpeed = Math.max(track.peakDownwardSpeed, Math.max(0.0, -velocity.y));
        }

        if (fallFlying && !track.wasFallFlying && velocity.length() < TAKEOFF_GRACE_SPEED) {
            // Ground takeoff: the jump and its redirect onto the glide path are not load.
            track.takeoffGraceTicks = TAKEOFF_GRACE_TICKS;
        }
        track.wasFallFlying = fallFlying;

        double rawDelta = Math.min(velocity.subtract(track.previousVelocity).length(), MAX_DELTA_PER_TICK);
        track.smoothedDelta = GForceMath.smooth(track.smoothedDelta, rawDelta, GForceMath.SMOOTHING_ALPHA);
        track.currentGs = GForceMath.gsForDelta(track.smoothedDelta, ConfigManager.get().gForce.deltaToGs);
        track.previousVelocity = velocity;
        track.previousPosition = position;

        boolean grace = track.takeoffGraceTicks > 0;
        if (grace) {
            track.takeoffGraceTicks--;
        }

        ElytraCombatConfig config = ConfigManager.get();
        boolean loadHigh = eligible && !grace && track.currentGs >= config.gForce.effectThresholdGs;
        // Still falling unstabilised counts even once the load itself fades toward terminal
        // velocity: the nausea keeps refreshing until the player lands or glides it off.
        boolean fallingFast = !grounded && !fallFlying
                && wearsElytra(player) && velocity.y <= -ELIGIBILITY_FALL_SPEED;

        if (loadHigh || fallingFast) {
            track.ticksBelowEffectThreshold = 0;
            refreshNausea(player, config.freefall, track);
            if (loadHigh) {
                refreshDarkness(player, config.freefall);
            }
        } else if (track.nauseaSustained
                && ++track.ticksBelowEffectThreshold >= NAUSEA_CLEAR_DELAY_TICKS) {
            // Stabilised: the load dropped and the player is no longer falling unstabilised.
            player.removeEffect(MobEffects.NAUSEA);
            track.nauseaSustained = false;
            track.ticksBelowEffectThreshold = 0;
        }

        if (loadHigh) {
            accrueDamage(player, track);
        }
        // Application runs regardless of current context: the landing burst is accrued
        // during eligible motion and must still land after the victim is on the ground.
        applyDamage(player, track);
    }

    /** True while a firework rocket is attached to and boosting this player. */
    private static boolean isRocketBoosting(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(FireworkRocketEntity.class,
                player.getBoundingBox().inflate(2.0),
                rocket -> ((FireworkRocketAccessor) rocket).elytraCombat$getAttachedTo() == player).isEmpty();
    }

    private static void accrueDamage(ServerPlayer player, Track track) {
        ElytraCombatConfig.GForce gForce = ConfigManager.get().gForce;
        if (!gForce.enabled) {
            return;
        }
        track.gBuffer += GForceMath.damagePerTick(track.currentGs, gForce.thresholdGs, gForce.damagePerGsPerSecond);
    }

    private static void applyDamage(ServerPlayer player, Track track) {
        ElytraCombatConfig.GForce gForce = ConfigManager.get().gForce;
        if (!gForce.enabled || ++track.ticksSinceApplied < APPLICATION_INTERVAL_TICKS) {
            return;
        }
        // Sub-heart leftovers decay instead of sitting in the buffer forever, which also
        // keeps tiny spikes from repeatedly poking the hurt invulnerability window.
        if (track.gBuffer >= 1.0F) {
            DamageSource gForceDamage = player.damageSources().source(G_FORCE_DAMAGE);
            player.hurtServer(player.level(), gForceDamage, (float) track.gBuffer);
            track.gBuffer = 0.0;
        } else {
            track.gBuffer *= 0.5;
        }
        track.ticksSinceApplied = 0;
    }

    public static void clear(ServerPlayer player) {
        TRACKS.remove(player);
    }

    /**
     * One-off instances for the mid-flight shock, applied the moment an elytra is disabled
     * in flight. The monitor takes over from here: the nausea is refreshed while the load
     * stays above the effect threshold and cleared once the player stabilises.
     */
    static void applyShockEffects(ServerPlayer player) {
        Track track = TRACKS.computeIfAbsent(player, p -> new Track());
        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        refreshNausea(player, config, track);
        refreshDarkness(player, config);
    }

    private static boolean wearsElytra(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        return ElytraCooldowns.isElytra(chest);
    }

    private static void seed(Track track, Vec3 velocity, Vec3 position, boolean fallFlying) {
        track.previousVelocity = velocity;
        track.previousPosition = position;
        track.smoothedDelta = 0.0;
        track.currentGs = 0.0;
        track.wasFallFlying = fallFlying;
        track.takeoffGraceTicks = 0;
    }

    private static void refreshNausea(ServerPlayer player, ElytraCombatConfig.Freefall config, Track track) {
        if (config.nauseaStrength <= 0) {
            return;
        }
        int duration = Math.max(config.nauseaDurationSeconds * 20, NAUSEA_MIN_INSTANCE_TICKS);
        MobEffectInstance active = player.getEffect(MobEffects.NAUSEA);
        if (active == null || active.getDuration() <= NAUSEA_REFRESH_ADVANCE_TICKS
                || active.getAmplifier() < config.nauseaStrength - 1) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,
                    duration, config.nauseaStrength - 1, false, true, true));
            // Freshly applied nausea must not be stripped by a clear-delay counter that was
            // already spent before it went on (the mid-flight shock is exactly that case:
            // applied outside the tick loop, with the counter at any stale value).
            track.nauseaSustained = true;
            track.ticksBelowEffectThreshold = 0;
        }
    }

    private static void refreshDarkness(ServerPlayer player, ElytraCombatConfig.Freefall config) {
        if (!config.darkness) {
            return;
        }
        MobEffectInstance active = player.getEffect(MobEffects.DARKNESS);
        if (active == null || active.getDuration() <= DARKNESS_BLEND_OUT_ADVANCE_TICKS + 10) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0, false, true, true));
        }
    }
}

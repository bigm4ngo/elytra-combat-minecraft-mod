package dev.elytracombat;

/**
 * Pure math for the G-force model. No Minecraft types so it stays unit testable.
 *
 * <p>The model derives the G load from the <em>sudden change in velocity</em> between game
 * ticks, the same quantity a pilot feels as acceleration: {@code Gs = |Δv| * delta_to_gs}.
 * A steady fall therefore stops registering once drag has cancelled gravity out at vanilla
 * terminal velocity (~3.92 blocks/tick), while abrupt events spike hard - the knockback of
 * the hit that started the fall, decelerating into the ground, or snapping a fresh elytra
 * open mid-fall. Vanilla gravity accelerates a fall by 0.0784 blocks/tick each tick at
 * first, so with the default factor of 40 the start of a fall reads about 3.1 Gs, fading to
 * 0 as terminal velocity is reached, while landing out of terminal velocity spikes past
 * 150 Gs for a tick. Only the load above {@code threshold_gs} deals damage.
 */
public final class GForceMath {
    /**
     * Fraction of the previous tick's smoothed load kept when a new tick is mixed in
     * (an exponential moving average). Spikes therefore read as a burst over a few ticks
     * instead of a single-tick flicker.
     */
    public static final double SMOOTHING_ALPHA = 0.35;

    private GForceMath() {
    }

    /** G load for a per-tick velocity change in blocks/tick. */
    public static double gsForDelta(double deltaSpeedBlocksPerTick, double deltaToGs) {
        return Math.max(0.0, deltaSpeedBlocksPerTick) * deltaToGs;
    }

    /** One step of the exponential smoothing applied to the raw per-tick load. */
    public static double smooth(double previous, double instant, double alpha) {
        return previous + (instant - previous) * alpha;
    }

    /** Damage per second at a given load; zero at or below the threshold. */
    public static double damagePerSecond(double gs, double thresholdGs, double damagePerGsPerSecond) {
        return Math.max(0.0, gs - thresholdGs) * damagePerGsPerSecond;
    }

    /** Damage accrued over a single tick. */
    public static double damagePerTick(double gs, double thresholdGs, double damagePerGsPerSecond) {
        return damagePerSecond(gs, thresholdGs, damagePerGsPerSecond) / 20.0;
    }
}

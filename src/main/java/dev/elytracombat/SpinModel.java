package dev.elytracombat;

import java.util.function.DoubleSupplier;

/**
 * Pure math for the free fall tumble. No Minecraft types so it stays unit testable.
 *
 * <p>The turn rates are sampled once when flight is disabled and then decay every tick.
 * Their magnitude is bounded by the speed the player had at that moment, so a slow player
 * tumbles gently while a fast one tumbles faster, and no tumble can exceed the hard
 * cap below (6 deg/tick = 120 deg/s).
 */
public final class SpinModel {
    /** Hard ceiling for the yaw rate regardless of impact speed or intensity. */
    public static final double MAX_YAW_RATE_DEG_PER_TICK = 6.0;
    /** Hard ceiling for the pitch rate; pitch changes are kept subtler than yaw. */
    public static final double MAX_PITCH_RATE_DEG_PER_TICK = 1.5;
    /** Fraction of the rate kept each tick (~16% left after 3 seconds). */
    public static final double DECAY_FACTOR_PER_TICK = 0.97;
    /** Rates below this magnitude are treated as finished. */
    public static final double MIN_RATE_DEG_PER_TICK = 0.05;
    /** Impact speed used for rate bounds is clamped to this (vanilla terminal speed ~3.92). */
    public static final double MAX_IMPACT_SPEED = 5.0;
    /** Yaw rate budget in deg/tick per block/tick of impact speed. */
    private static final double SPEED_TO_RATE = 1.5;
    /** Portion of the yaw budget the pitch rate may use. */
    private static final double PITCH_SHARE = 0.25;

    private SpinModel() {
    }

    public record TurnRates(double yawRate, double pitchRate) {
        public TurnRates decayed() {
            return new TurnRates(yawRate * DECAY_FACTOR_PER_TICK, pitchRate * DECAY_FACTOR_PER_TICK);
        }

        public boolean expired() {
            return Math.abs(yawRate) < MIN_RATE_DEG_PER_TICK && Math.abs(pitchRate) < MIN_RATE_DEG_PER_TICK;
        }
    }

    /** Upper bound for a yaw rate at the given impact speed and intensity, in deg/tick. */
    public static double turnRateCap(double impactSpeedBlocksPerTick, double intensity) {
        double speed = Math.max(0.0, Math.min(impactSpeedBlocksPerTick, MAX_IMPACT_SPEED));
        double factor = Math.max(0.0, intensity);
        return Math.min(speed * SPEED_TO_RATE * factor, MAX_YAW_RATE_DEG_PER_TICK);
    }

    /** Samples one random decaying turn rate set for the whole tumble. */
    public static TurnRates sample(double impactSpeedBlocksPerTick, double intensity, DoubleSupplier nextDouble) {
        double cap = turnRateCap(impactSpeedBlocksPerTick, intensity);
        if (cap <= 0.0) {
            return new TurnRates(0.0, 0.0);
        }
        double yawRate = (nextDouble.getAsDouble() * 2.0 - 1.0) * cap;
        double pitchRate = (nextDouble.getAsDouble() * 2.0 - 1.0) * Math.min(cap * PITCH_SHARE, MAX_PITCH_RATE_DEG_PER_TICK);
        return new TurnRates(yawRate, pitchRate);
    }
}

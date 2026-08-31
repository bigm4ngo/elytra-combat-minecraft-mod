package dev.elytracombat;

/**
 * Pure math for the G-force model. No Minecraft types so it stays unit testable.
 *
 * <p>The model treats the uncontrolled free fall of a body as a G load proportional to its
 * downward speed: {@code Gs = downwardSpeed * speedToGs}. Vanilla terminal velocity for a
 * falling player is ~3.92 blocks/tick, so the default factor of 12 peaks at ~47 Gs, reached
 * after roughly 13 blocks of fall. Only the load above {@code thresholdGs} deals damage.
 */
public final class GForceMath {
    private GForceMath() {
    }

    /** G load for a downward speed in blocks per tick. */
    public static double gsFor(double downwardSpeedBlocksPerTick, double speedToGs) {
        return Math.max(0.0, downwardSpeedBlocksPerTick) * speedToGs;
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

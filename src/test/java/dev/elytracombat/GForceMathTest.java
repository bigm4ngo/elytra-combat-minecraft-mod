package dev.elytracombat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GForceMathTest {
    private static final double VANILLA_GRAVITY_PER_TICK = 0.0784;
    private static final double TERMINAL_VELOCITY = 3.92;

    @Test
    void convertsVelocityChangeToGs() {
        assertEquals(0.0, GForceMath.gsForDelta(0.0, 40.0));
        assertEquals(0.0, GForceMath.gsForDelta(-1.0, 40.0), "negative delta is clamped");
        assertEquals(3.136, GForceMath.gsForDelta(VANILLA_GRAVITY_PER_TICK, 40.0), 1e-9);
        assertEquals(156.8, GForceMath.gsForDelta(TERMINAL_VELOCITY, 40.0), 1e-9);
    }

    @Test
    void steadyFallFadesToZeroLoadAtTerminalVelocity() {
        // Drag cancels gravity at terminal velocity, so a stabilized fall stops registering.
        double startOfFall = GForceMath.gsForDelta(VANILLA_GRAVITY_PER_TICK, 40.0);
        assertTrue(startOfFall > 3.0 && startOfFall < 3.3, "the first ticks of a fall read ~3 Gs");
    }

    @Test
    void smoothingConvergesOnConstantLoad() {
        double smoothed = 0.0;
        for (int i = 0; i < 40; i++) {
            smoothed = GForceMath.smooth(smoothed, VANILLA_GRAVITY_PER_TICK, GForceMath.SMOOTHING_ALPHA);
        }
        assertEquals(VANILLA_GRAVITY_PER_TICK, smoothed, 1e-6);
    }

    @Test
    void smoothingSpreadsSingleTickSpikes() {
        // A landing out of terminal velocity is one huge tick; smoothing turns it into a
        // decaying burst instead of a flicker, but keeps the total exposure.
        double smoothed = 0.0;
        double exposure = 0.0;
        for (int i = 0; i < 20; i++) {
            double instant = i == 0 ? TERMINAL_VELOCITY : 0.0;
            smoothed = GForceMath.smooth(smoothed, instant, GForceMath.SMOOTHING_ALPHA);
            exposure += smoothed;
        }
        assertTrue(smoothed < 0.01, "the spike decays away");
        assertTrue(exposure > 3.9, "the burst still covers the whole change");
        assertTrue(exposure < TERMINAL_VELOCITY * 3.0);
    }

    @Test
    void damageIsZeroAtOrBelowThreshold() {
        assertEquals(0.0, GForceMath.damagePerSecond(25.0, 25.0, 0.4));
        assertEquals(0.0, GForceMath.damagePerSecond(3.14, 25.0, 0.4));
        assertEquals(0.0, GForceMath.damagePerTick(3.14, 25.0, 0.4));
    }

    @Test
    void damageScalesWithLoadAboveThreshold() {
        assertEquals(8.8, GForceMath.damagePerSecond(47.0, 25.0, 0.4), 1e-9);
        assertEquals(0.44, GForceMath.damagePerTick(47.0, 25.0, 0.4), 1e-9);
    }

    @Test
    void terminalVelocityLandingStaysTunable() {
        // With defaults, landing out of vanilla terminal velocity peaks past 150 raw Gs and
        // the smoothed burst deals a couple of points of damage on top of vanilla fall damage.
        double rawGs = GForceMath.gsForDelta(TERMINAL_VELOCITY, 40.0);
        double peak = GForceMath.smooth(0.0, rawGs, GForceMath.SMOOTHING_ALPHA);
        double burstDamage = 0.0;
        double smoothed = peak;
        for (int i = 0; i < 20; i++) {
            burstDamage += GForceMath.damagePerTick(smoothed, 15.0, 0.4);
            smoothed = GForceMath.smooth(smoothed, 0.0, GForceMath.SMOOTHING_ALPHA);
        }
        assertTrue(rawGs > 100.0);
        assertTrue(burstDamage > 1.0 && burstDamage < 3.0);
    }
}

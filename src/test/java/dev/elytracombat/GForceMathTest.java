package dev.elytracombat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GForceMathTest {
    private static final double VANILLA_GRAVITY_PER_TICK = 0.0784;
    private static final double TERMINAL_VELOCITY = 3.92;
    private static final double DELTA_TO_GS = 100.0;
    private static final double THRESHOLD_GS = 25.0;

    @Test
    void convertsVelocityChangeToGs() {
        assertEquals(0.0, GForceMath.gsForDelta(0.0, DELTA_TO_GS));
        assertEquals(0.0, GForceMath.gsForDelta(-1.0, DELTA_TO_GS), "negative delta is clamped");
        assertEquals(7.84, GForceMath.gsForDelta(VANILLA_GRAVITY_PER_TICK, DELTA_TO_GS), 1e-9);
        assertEquals(392.0, GForceMath.gsForDelta(TERMINAL_VELOCITY, DELTA_TO_GS), 1e-9);
    }

    @Test
    void steadyFallReadsAboutEightGsAtStart() {
        // The game-scale conversion: the first ticks of a free fall (vanilla gravity,
        // 0.0784 blocks/tick per tick) read ~7.8 Gs, fading to 0 at terminal velocity.
        double startOfFall = GForceMath.gsForDelta(VANILLA_GRAVITY_PER_TICK, DELTA_TO_GS);
        assertTrue(startOfFall > 7.5 && startOfFall < 8.2, "the first ticks of a fall read ~8 Gs");
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
        assertEquals(0.0, GForceMath.damagePerSecond(THRESHOLD_GS, THRESHOLD_GS, 0.4));
        assertEquals(0.0, GForceMath.damagePerSecond(3.14, THRESHOLD_GS, 0.4), "sustained free fall (~7.8 Gs) never hurts");
        assertEquals(0.0, GForceMath.damagePerTick(3.14, THRESHOLD_GS, 0.4));
    }

    @Test
    void damageScalesWithLoadAboveThreshold() {
        assertEquals(8.8, GForceMath.damagePerSecond(47.0, THRESHOLD_GS, 0.4), 1e-9);
        assertEquals(0.44, GForceMath.damagePerTick(47.0, THRESHOLD_GS, 0.4), 1e-9);
    }

    @Test
    void terminalVelocityLandingStaysTunable() {
        // With defaults, landing out of vanilla terminal velocity peaks at ~392 raw Gs and
        // the smoothed burst (~137 Gs peak) deals a few hearts' worth of damage on top of
        // the lethal vanilla fall damage.
        double rawGs = GForceMath.gsForDelta(TERMINAL_VELOCITY, DELTA_TO_GS);
        double peak = GForceMath.smooth(0.0, rawGs, GForceMath.SMOOTHING_ALPHA);
        double burstDamage = 0.0;
        double smoothed = peak;
        for (int i = 0; i < 20; i++) {
            burstDamage += GForceMath.damagePerTick(smoothed, THRESHOLD_GS, 0.4);
            smoothed = GForceMath.smooth(smoothed, 0.0, GForceMath.SMOOTHING_ALPHA);
        }
        assertTrue(rawGs > 200.0);
        assertTrue(peak > 100.0 && peak < 150.0);
        assertTrue(burstDamage > 3.5 && burstDamage < 6.0);
    }

    @Test
    void hardManeuverLandsJustAboveTheDamageThreshold() {
        // A violent elytra maneuver (one block/tick of velocity redirected at once) smooths
        // to ~35 Gs: enough to hurt and disable, well below the ~137 Gs terminal landing.
        double smoothed = GForceMath.smooth(0.0, 1.0, GForceMath.SMOOTHING_ALPHA);
        double gs = GForceMath.gsForDelta(smoothed, DELTA_TO_GS);
        assertEquals(35.0, gs, 1e-9);
        assertTrue(gs > THRESHOLD_GS);
    }
}

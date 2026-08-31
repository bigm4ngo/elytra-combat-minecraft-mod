package dev.elytracombat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GForceMathTest {
    @Test
    void convertsDownwardSpeedToGs() {
        assertEquals(0.0, GForceMath.gsFor(0.0, 12.0));
        assertEquals(0.0, GForceMath.gsFor(-1.0, 12.0), "negative speed is clamped");
        assertEquals(47.04, GForceMath.gsFor(3.92, 12.0), 1e-9);
    }

    @Test
    void damageIsZeroAtOrBelowThreshold() {
        assertEquals(0.0, GForceMath.damagePerSecond(25.0, 25.0, 0.4));
        assertEquals(0.0, GForceMath.damagePerSecond(20.0, 25.0, 0.4));
        assertEquals(0.0, GForceMath.damagePerTick(20.0, 25.0, 0.4));
    }

    @Test
    void damageScalesWithLoadAboveThreshold() {
        assertEquals(8.8, GForceMath.damagePerSecond(47.0, 25.0, 0.4), 1e-9);
        assertEquals(0.44, GForceMath.damagePerTick(47.0, 25.0, 0.4), 1e-9);
    }

    @Test
    void vanillaTerminalVelocityStaysTunable() {
        // With defaults, a full speed fall (3.92 blocks/tick) is about 47 Gs
        // and deals 8.8 damage per second.
        double gs = GForceMath.gsFor(3.92, 12.0);
        double dps = GForceMath.damagePerSecond(gs, 25.0, 0.4);
        assertTrue(gs > 40.0 && gs < 50.0);
        assertTrue(dps > 8.0 && dps < 9.5);
    }
}

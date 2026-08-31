package dev.elytracombat;

import org.junit.jupiter.api.Test;

import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpinModelTest {
    private static final DoubleSupplier FIXED = () -> 0.75;

    @Test
    void capGrowsWithImpactSpeed() {
        assertTrue(SpinModel.turnRateCap(0.0, 1.0) < SpinModel.turnRateCap(2.0, 1.0));
        assertTrue(SpinModel.turnRateCap(2.0, 1.0) < SpinModel.turnRateCap(5.0, 1.0));
    }

    @Test
    void capNeverExceedsHardLimit() {
        assertEquals(SpinModel.MAX_YAW_RATE_DEG_PER_TICK, SpinModel.turnRateCap(50.0, 1.0));
        assertEquals(SpinModel.MAX_YAW_RATE_DEG_PER_TICK, SpinModel.turnRateCap(3.92, 10.0));
        assertEquals(0.0, SpinModel.turnRateCap(3.92, 0.0));
        assertEquals(0.0, SpinModel.turnRateCap(-1.0, 1.0), "negative speed is clamped");
    }

    @Test
    void zeroIntensityDisablesSpin() {
        SpinModel.TurnRates rates = SpinModel.sample(4.0, 0.0, FIXED);
        assertEquals(0.0, rates.yawRate());
        assertEquals(0.0, rates.pitchRate());
        assertTrue(rates.expired());
    }

    @Test
    void sampledRatesRespectBounds() {
        SpinModel.TurnRates rates = SpinModel.sample(3.92, 1.0, FIXED);
        assertTrue(Math.abs(rates.yawRate()) <= SpinModel.turnRateCap(3.92, 1.0));
        assertTrue(Math.abs(rates.pitchRate()) <= SpinModel.MAX_PITCH_RATE_DEG_PER_TICK);
        assertTrue(Math.abs(rates.pitchRate()) < Math.abs(rates.yawRate()), "pitch stays subtler than yaw");
    }

    @Test
    void ratesDecayAndExpire() {
        SpinModel.TurnRates rates = new SpinModel.TurnRates(4.0, 1.0);
        for (int i = 0; i < 400; i++) {
            rates = rates.decayed();
        }
        assertTrue(rates.expired(), "rates decay below the cutoff eventually");
        assertFalse(new SpinModel.TurnRates(4.0, 0.0).expired());
    }
}

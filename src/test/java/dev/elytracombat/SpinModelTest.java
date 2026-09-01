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
    void terminalVelocitySpinIsNoticeableButTamed() {
        // A full speed fall (~3.92 blocks/tick) gets close to the reduced 120 deg/s cap,
        // clearly weaker than the 1.2.1 spin that peaked at 240 deg/s.
        double cap = SpinModel.turnRateCap(3.92, 1.0);
        assertTrue(cap > 4.5 && cap <= SpinModel.MAX_YAW_RATE_DEG_PER_TICK,
                "spin at terminal velocity stays near the reduced cap");
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
    void sampledTumbleLastsTheFullWindow() {
        // The decay is derived from the sampled magnitudes, so every tumble - however weak -
        // fades across the whole SPIN_DURATION_TICKS window instead of dying off early.
        SpinModel.TurnRates rates = SpinModel.sample(3.92, 1.0, FIXED);
        assertTrue(rates.decayPerTick() > 0.0);
        for (int i = 0; i < SpinModel.SPIN_DURATION_TICKS / 2; i++) {
            rates = rates.decayed();
            assertFalse(rates.expired(), "still fading at half of the window, at tick " + i);
        }
        for (int i = 0; i < SpinModel.SPIN_DURATION_TICKS; i++) {
            rates = rates.decayed();
        }
        assertTrue(rates.expired(), "the tumble lands on zero at the end of the window");
    }

    @Test
    void decayIsLinearAndGradual() {
        // Halfway through the window half the yaw remains: no early collapse and no abrupt
        // cutoff, the fade is even from start to finish. Pitch shares the same decay rate,
        // so it reaches zero at a quarter of the window and stays there while yaw fades on.
        SpinModel.TurnRates rates = new SpinModel.TurnRates(4.0, 1.0, 4.0 / SpinModel.SPIN_DURATION_TICKS);
        for (int i = 0; i < SpinModel.SPIN_DURATION_TICKS / 2; i++) {
            rates = rates.decayed();
        }
        assertEquals(2.0, rates.yawRate(), 1e-9);
        assertEquals(0.0, rates.pitchRate(), 1e-9);
        assertFalse(rates.expired());
    }

    @Test
    void ratesDecayAndExpire() {
        SpinModel.TurnRates rates = new SpinModel.TurnRates(4.0, -1.0, 0.025);
        for (int i = 0; i < 400; i++) {
            rates = rates.decayed();
        }
        assertTrue(rates.expired(), "negative rates decay up toward zero and expire too");
        // No decay means no motion toward zero, which only happens for a sampled zero tumble.
        SpinModel.TurnRates frozen = new SpinModel.TurnRates(4.0, 0.0, 0.0);
        for (int i = 0; i < 400; i++) {
            frozen = frozen.decayed();
        }
        assertFalse(frozen.expired());
    }
}

package dev.elytracombat.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraCombatConfigTest {
    @Test
    void defaultsRepresentCurrentBehavior() {
        ElytraCombatConfig config = new ElytraCombatConfig();
        config.validate();

        assertEquals(600, config.disableDurationTicks());
        assertFalse(config.damageFilter.isAllowlist());
        assertTrue(config.damageFilter.damageTypes.isEmpty());
        assertTrue(config.damageFilter.ignoreFallDamage);
        assertTrue(config.absorptionBypassesDisable);
        assertEquals(4, config.freefall.nauseaStrength);
        assertEquals(3, config.freefall.nauseaDurationSeconds);
        assertTrue(config.freefall.darkness);
        assertTrue(config.freefall.shockSound);
        assertTrue(config.freefall.viewSnap);
        assertEquals(1.0, config.freefall.spinIntensity);
        assertTrue(config.gForce.enabled);
        assertEquals(100.0, config.gForce.deltaToGs);
        assertEquals(25.0, config.gForce.thresholdGs);
        assertEquals(5.0, config.gForce.damagePerGsPerSecond);
        assertEquals(5.0, config.gForce.effectThresholdGs);
        assertTrue(config.durabilityDamage.enabled);
        assertEquals("percent", config.durabilityDamage.mode);
        assertEquals(10.0, config.durabilityDamage.percent);
        assertEquals(30, config.durabilityDamage.flat);
        assertEquals(2.0, config.durabilityDamage.damageScale);
    }

    @Test
    void calculatesAllDurabilityModes() {
        ElytraCombatConfig.DurabilityDamage durability = new ElytraCombatConfig.DurabilityDamage();

        assertEquals(44, durability.calculate(432, 5.0F));
        durability.mode = "flat";
        assertEquals(30, durability.calculate(432, 5.0F));
        durability.mode = "damage_scaled";
        assertEquals(10, durability.calculate(432, 5.0F));
    }

    @Test
    void acceptsPlayerOnlyAllowlistShape() {
        ElytraCombatConfig config = new ElytraCombatConfig();
        config.damageFilter.mode = "ALLOWLIST";
        config.damageFilter.matchDirectPlayerDamage = true;
        config.damageFilter.matchIndirectPlayerDamage = true;
        config.damageFilter.matchPlayerOwnedEntityDamage = true;

        config.validate();

        assertTrue(config.damageFilter.isAllowlist());
        assertEquals("allowlist", config.damageFilter.mode);
    }

    @Test
    void validatesDamageIdentifiersAndDuration() {
        ElytraCombatConfig config = new ElytraCombatConfig();
        config.damageFilter.damageTypes = List.of("minecraft:player_attack", "example:custom/damage");
        config.validate();

        config.damageFilter.damageTypes = List.of("missing_namespace");
        assertThrows(IllegalArgumentException.class, config::validate);

        config.damageFilter.damageTypes = List.of();
        config.disableDurationSeconds = 0;
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void validatesFreefallRanges() {
        ElytraCombatConfig config = new ElytraCombatConfig();

        config.freefall.nauseaStrength = 11;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.freefall.nauseaStrength = 4;
        config.freefall.nauseaDurationSeconds = 0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.freefall.nauseaDurationSeconds = 1;
        config.validate();
        config.freefall.nauseaDurationSeconds = 3;

        config.freefall.spinIntensity = -0.1;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.freefall.spinIntensity = 3.1;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.freefall.spinIntensity = 1.0;
        config.validate();
    }

    @Test
    void validatesGForceRanges() {
        ElytraCombatConfig config = new ElytraCombatConfig();

        config.gForce.deltaToGs = 0.5;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.deltaToGs = 501.0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.deltaToGs = 100.0;
        config.gForce.thresholdGs = 0.0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.thresholdGs = 25.0;
        config.gForce.damagePerGsPerSecond = -1.0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.damagePerGsPerSecond = 5.0;
        config.gForce.effectThresholdGs = -0.1;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.effectThresholdGs = 501.0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.gForce.effectThresholdGs = 0.0;
        config.validate();
        config.gForce.effectThresholdGs = 5.0;
        config.validate();
    }

    @Test
    void validatesDurabilityRanges() {
        ElytraCombatConfig config = new ElytraCombatConfig();

        config.durabilityDamage.damageScale = 20.1;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.durabilityDamage.damageScale = 2.0;
        config.durabilityDamage.percent = -1.0;
        assertThrows(IllegalArgumentException.class, config::validate);

        config.durabilityDamage.percent = 10.0;
        config.durabilityDamage.mode = "random";
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void rejectsUnknownModes() {
        ElytraCombatConfig config = new ElytraCombatConfig();
        config.damageFilter.mode = "sometimes";
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}

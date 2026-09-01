package dev.elytracombat.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ElytraCombatConfig {
    @SerializedName("disable_duration_seconds")
    public int disableDurationSeconds = 30;

    @SerializedName("damage_filter")
    public DamageFilter damageFilter = new DamageFilter();

    @SerializedName("absorption_bypasses_disable")
    public boolean absorptionBypassesDisable = true;

    @SerializedName("freefall")
    public Freefall freefall = new Freefall();

    @SerializedName("g_force")
    public GForce gForce = new GForce();

    @SerializedName("durability_damage")
    public DurabilityDamage durabilityDamage = new DurabilityDamage();

    public int disableDurationTicks() {
        return Math.multiplyExact(disableDurationSeconds, 20);
    }

    public void validate() {
        if (disableDurationSeconds < 1 || disableDurationSeconds > Integer.MAX_VALUE / 20) {
            throw new IllegalArgumentException("disable_duration_seconds must be between 1 and " + (Integer.MAX_VALUE / 20));
        }
        if (damageFilter == null) {
            throw new IllegalArgumentException("damage_filter is required");
        }
        damageFilter.validate();
        if (freefall == null) {
            throw new IllegalArgumentException("freefall is required");
        }
        freefall.validate();
        if (gForce == null) {
            throw new IllegalArgumentException("g_force is required");
        }
        gForce.validate();
        if (durabilityDamage == null) {
            throw new IllegalArgumentException("durability_damage is required");
        }
        durabilityDamage.validate();
    }

    public static final class Freefall {
        /** 0 disables nausea. Applied amplifier is strength - 1. */
        @SerializedName("nausea_strength")
        public int nauseaStrength = 4;

        /**
         * Length of one nausea application. Vanilla blends nausea in over 150 ticks and
         * will not let an instance shorter than its 60 tick blend-out advance hold any
         * distortion at all, so the monitor floors applications at 90 ticks (4.5 s); with
         * the 3 s default the warp holds at ~60% strength while the load persists and is
         * fully gone within a couple of seconds of the load clearing.
         */
        @SerializedName("nausea_duration_seconds")
        public int nauseaDurationSeconds = 3;

        @SerializedName("darkness")
        public boolean darkness = true;

        @SerializedName("shock_sound")
        public boolean shockSound = true;

        @SerializedName("view_snap")
        public boolean viewSnap = true;

        /** 0 disables the tumble; scales the decaying spin, hard capped in SpinModel. */
        @SerializedName("spin_intensity")
        public double spinIntensity = 1.0;

        public void validate() {
            if (nauseaStrength < 0 || nauseaStrength > 10) {
                throw new IllegalArgumentException("freefall.nausea_strength must be between 0 and 10");
            }
            if (nauseaDurationSeconds < 1 || nauseaDurationSeconds > 120) {
                throw new IllegalArgumentException("freefall.nausea_duration_seconds must be between 1 and 120");
            }
            if (!Double.isFinite(spinIntensity) || spinIntensity < 0.0 || spinIntensity > 3.0) {
                throw new IllegalArgumentException("freefall.spin_intensity must be between 0.0 and 3.0");
            }
        }
    }

    public static final class GForce {
        @SerializedName("enabled")
        public boolean enabled = true;

        /**
         * Gs per block/tick of sudden velocity change. 100 is the game-scale conversion:
         * one block/tick of sudden change reads as 100 Gs, so vanilla gravity during a
         * free fall (0.0784 blocks/tick per tick) reads about 7.8 Gs, an arrow's knockback
         * lands near 18 Gs, hard landings and re-entries go far higher, and terminal
         * velocity still cancels the sustained load out toward 0.
         */
        @SerializedName("delta_to_gs")
        public double deltaToGs = 100.0;

        /**
         * Sudden changes below this load never hurt, and any event that pushes the smoothed
         * load past it deals trauma through the {@code elytra_combat:g_force} damage type -
         * which is a regular hurt, so it can also freshly disable a working elytra.
         * Sustained free fall (~7.8 Gs) never reaches this.
         */
        @SerializedName("threshold_gs")
        public double thresholdGs = 25.0;

        /** Damage per second for each G above the threshold, before armor. */
        @SerializedName("damage_per_gs_per_second")
        public double damagePerGsPerSecond = 0.4;

        /**
         * Minimum smoothed G load before the G effects apply: darkness pulses and nausea
         * start the moment the load crosses this and stop shortly after it drops again.
         * 0 keeps them up whenever the player is gliding or falling fast at all.
         */
        @SerializedName("effect_threshold_gs")
        public double effectThresholdGs = 5.0;

        public void validate() {
            if (!Double.isFinite(deltaToGs) || deltaToGs < 1.0 || deltaToGs > 500.0) {
                throw new IllegalArgumentException("g_force.delta_to_gs must be between 1.0 and 500.0");
            }
            if (!Double.isFinite(thresholdGs) || thresholdGs < 1.0 || thresholdGs > 200.0) {
                throw new IllegalArgumentException("g_force.threshold_gs must be between 1.0 and 200.0");
            }
            if (!Double.isFinite(damagePerGsPerSecond) || damagePerGsPerSecond < 0.0 || damagePerGsPerSecond > 10.0) {
                throw new IllegalArgumentException("g_force.damage_per_gs_per_second must be between 0.0 and 10.0");
            }
            if (!Double.isFinite(effectThresholdGs) || effectThresholdGs < 0.0 || effectThresholdGs > 500.0) {
                throw new IllegalArgumentException("g_force.effect_threshold_gs must be between 0.0 and 500.0");
            }
        }
    }

    public static final class DamageFilter {
        public String mode = "denylist";

        /**
         * Landing and crash trauma (fall damage) never disables elytras when true. This keeps
         * the damage of a fall the wearer was already shot down in from disabling a backup
         * elytra swapped in mid-fall.
         */
        @SerializedName("ignore_fall_damage")
        public boolean ignoreFallDamage = true;

        @SerializedName("damage_types")
        public List<String> damageTypes = new ArrayList<>();

        @SerializedName("match_direct_player_damage")
        public boolean matchDirectPlayerDamage;

        @SerializedName("match_indirect_player_damage")
        public boolean matchIndirectPlayerDamage;

        @SerializedName("match_player_owned_entity_damage")
        public boolean matchPlayerOwnedEntityDamage;

        public boolean isAllowlist() {
            return "allowlist".equals(mode.toLowerCase(Locale.ROOT));
        }

        private void validate() {
            if (mode == null || !(mode.equalsIgnoreCase("allowlist") || mode.equalsIgnoreCase("denylist"))) {
                throw new IllegalArgumentException("damage_filter.mode must be 'allowlist' or 'denylist'");
            }
            mode = mode.toLowerCase(Locale.ROOT);
            if (damageTypes == null) {
                throw new IllegalArgumentException("damage_filter.damage_types is required");
            }
            for (String id : damageTypes) {
                if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                    throw new IllegalArgumentException("Invalid namespaced damage type: " + id);
                }
            }
        }
    }

    public static final class DurabilityDamage {
        public boolean enabled = true;
        public String mode = "percent";
        public double percent = 10.0;
        public int flat = 30;
        @SerializedName("damage_scale") public double damageScale = 2.0;
        public int calculate(int maxDurability, float finalDamage) {
            double raw = switch (mode) { case "percent" -> maxDurability * percent / 100.0; case "flat" -> flat; case "damage_scaled" -> finalDamage * damageScale; default -> throw new IllegalStateException("Unknown durability mode: " + mode); };
            return raw <= 0.0 ? 0 : Math.max(1, (int) Math.ceil(raw));
        }
        public void validate() {
            if (mode == null || !(mode.equalsIgnoreCase("percent") || mode.equalsIgnoreCase("flat") || mode.equalsIgnoreCase("damage_scaled"))) throw new IllegalArgumentException("durability_damage.mode must be 'percent', 'flat', or 'damage_scaled'");
            mode = mode.toLowerCase(Locale.ROOT);
            if (!Double.isFinite(percent) || percent < 0.0 || percent > 100.0) throw new IllegalArgumentException("durability_damage.percent must be between 0 and 100");
            if (flat < 0 || flat > 10000) throw new IllegalArgumentException("durability_damage.flat must be between 0 and 10000");
            if (!Double.isFinite(damageScale) || damageScale < 0.1 || damageScale > 20.0) throw new IllegalArgumentException("durability_damage.damage_scale must be between 0.1 and 20.0");
        }
    }
}

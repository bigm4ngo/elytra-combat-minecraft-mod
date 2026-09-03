package dev.elytracombat.compat;

/**
 * A captured item cooldown, version-neutrally: the cooldown length in seconds and the
 * cooldown group as a plain {@code namespace:path} string (empty when ungrouped). The
 * per-version data component types live inside {@link Compat}.
 */
public record CooldownInfo(float seconds, String group) {
}

package dev.elytracombat;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Set;

public final class PhysicalDamage {
    private static final Set<String> NON_PHYSICAL = Set.of(
            "minecraft:magic",
            "minecraft:indirect_magic",
            "minecraft:dragon_breath",
            "minecraft:wither",
            "minecraft:drown",
            "minecraft:starve",
            "minecraft:freeze",
            "minecraft:on_fire",
            "minecraft:in_fire",
            "minecraft:lava",
            "minecraft:hot_floor",
            "minecraft:campfire",
            "minecraft:fall",
            "minecraft:fly_into_wall",
            "minecraft:cramming",
            "minecraft:in_wall",
            "minecraft:dry_out",
            "minecraft:out_of_world",
            "minecraft:outside_border",
            "minecraft:generic_kill"
    );

    private static final Set<String> PHYSICAL_ENVIRONMENT = Set.of(
            "minecraft:cactus",
            "minecraft:sweet_berry_bush",
            "minecraft:falling_anvil",
            "minecraft:falling_block",
            "minecraft:falling_stalactite",
            "minecraft:stalagmite",
            "minecraft:lightning_bolt",
            "minecraft:thorns",
            "minecraft:sonic_boom"
    );

    private PhysicalDamage() {
    }

    public static boolean isExternalPhysical(DamageSource source) {
        Identifier id = source.typeHolder().unwrapKey().orElseThrow().identifier();
        String value = id.toString();
        if (NON_PHYSICAL.contains(value)) {
            return false;
        }
        return source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.getDirectEntity() != null
                || source.getEntity() != null
                || PHYSICAL_ENVIRONMENT.contains(value);
    }
}

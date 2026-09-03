package dev.elytracombat;

import dev.elytracombat.compat.Compat;
import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

public final class DamageFilter {
    private DamageFilter() {
    }

    public static boolean shouldTrigger(DamageSource source) {
        ElytraCombatConfig.DamageFilter filter = ConfigManager.get().damageFilter;
        String damageType = Compat.damageTypeId(source);

        boolean matched = filter.damageTypes.contains(damageType)
                || filter.matchDirectPlayerDamage && isDirectPlayer(source)
                || filter.matchIndirectPlayerDamage && isIndirectPlayer(source)
                || filter.matchPlayerOwnedEntityDamage && isPlayerOwnedEntity(source);

        return filter.isAllowlist() ? matched : !matched;
    }

    private static boolean isDirectPlayer(DamageSource source) {
        return source.getDirectEntity() instanceof Player;
    }

    private static boolean isIndirectPlayer(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return !(direct instanceof Player) && source.getEntity() instanceof Player;
    }

    private static boolean isPlayerOwnedEntity(DamageSource source) {
        return isOwnedByPlayer(source.getDirectEntity()) || isOwnedByPlayer(source.getEntity());
    }

    private static boolean isOwnedByPlayer(Entity entity) {
        return entity instanceof TamableAnimal tameable && tameable.getOwner() instanceof Player;
    }
}

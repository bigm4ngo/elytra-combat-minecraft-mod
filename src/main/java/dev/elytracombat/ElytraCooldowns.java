package dev.elytracombat;

import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.UseCooldown;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Marks a disabled elytra and tracks how long it stays disabled.
 *
 * <p>The expiry is stored as an absolute game time stamp written once when the elytra is
 * disabled and removed when it clears. The item's components never change while the
 * cooldown runs, which keeps the server from resyncing the stack every tick - resyncing
 * is what made held and worn elytras replay their equip animation nonstop. The countdown
 * therefore runs in real time and matches the vanilla cooldown overlay exactly.
 */
public final class ElytraCooldowns {
    private static final String PREFIX = "elytra_combat_";
    private static final String MARKER = PREFIX + "disabled";
    private static final String EXPIRES_AT = PREFIX + "expires_at";
    private static final String GROUP = PREFIX + "cooldown_group";
    private static final String HAD_ORIGINAL = PREFIX + "had_original_cooldown";
    private static final String ORIGINAL_SECONDS = PREFIX + "original_seconds";
    private static final String ORIGINAL_GROUP = PREFIX + "original_group";

    private ElytraCooldowns() {
    }

    public static boolean isElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA);
    }

    public static boolean isDisabled(ServerLevel level, ItemStack stack) {
        CompoundTag tag = readTag(stack);
        return tag.getBooleanOr(MARKER, false) && remainingTicks(level, tag) > 0;
    }

    public static int remainingTicks(ServerLevel level, ItemStack stack) {
        return remainingTicks(level, readTag(stack));
    }

    private static int remainingTicks(ServerLevel level, CompoundTag tag) {
        long remaining = tag.getLongOr(EXPIRES_AT, 0L) - level.getGameTime();
        if (remaining <= 0L) {
            return 0;
        }
        return (int) Math.min(remaining, (long) Integer.MAX_VALUE);
    }

    /**
     * Disables the elytra for the configured duration.
     *
     * @return true when a fresh disable began; false when the elytra is already disabled
     *         (hits never extend an ongoing cooldown) or not an elytra
     */
    public static boolean disable(ServerPlayer player, ItemStack stack) {
        if (!isElytra(stack)) {
            return false;
        }

        CompoundTag existing = readTag(stack);
        if (existing.getBooleanOr(MARKER, false)) {
            if (remainingTicks(player.level(), existing) > 0) {
                return false;
            }
            clear(stack);
        }

        CompoundTag tag = readTag(stack);
        UseCooldown original = stack.get(DataComponents.USE_COOLDOWN);
        tag.putBoolean(HAD_ORIGINAL, original != null);
        if (original != null) {
            tag.putFloat(ORIGINAL_SECONDS, original.seconds());
            original.cooldownGroup().ifPresent(id -> tag.putString(ORIGINAL_GROUP, id.toString()));
        }

        int ticks = ConfigManager.get().disableDurationTicks();
        Identifier group = Identifier.fromNamespaceAndPath(ElytraCombat.MOD_ID, "disabled/" + UUID.randomUUID());
        tag.putBoolean(MARKER, true);
        tag.putString(GROUP, group.toString());
        tag.putLong(EXPIRES_AT, player.level().getGameTime() + ticks);
        writeTag(stack, tag);
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(ticks / 20.0F, Optional.of(group)));
        player.getCooldowns().addCooldown(stack, ticks);
        return true;
    }

    /**
     * Clears markers that have already expired from the slots a player is most likely to
     * have in view. Anything stored away is cleaned lazily when it is next seen worn, held,
     * or disabled again. Purely cosmetic bookkeeping: expired markers never affect gameplay.
     */
    public static void tickExpired(ServerPlayer player) {
        clearIfExpired(player, player.getItemBySlot(EquipmentSlot.CHEST));
        clearIfExpired(player, player.getMainHandItem());
        clearIfExpired(player, player.getItemBySlot(EquipmentSlot.OFFHAND));
        clearIfExpired(player, player.containerMenu.getCarried());
    }

    private static void clearIfExpired(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = readTag(stack);
        if (tag.getBooleanOr(MARKER, false) && tag.getLongOr(EXPIRES_AT, 0L) <= player.level().getGameTime()) {
            clear(stack);
        }
    }

    public static void damageElytra(ServerPlayer player, ItemStack stack, float finalDamage) {
        ElytraCombatConfig.DurabilityDamage config = ConfigManager.get().durabilityDamage;
        if (!config.enabled || !stack.isDamageableItem()) {
            return;
        }
        int amount = config.calculate(stack.getMaxDamage(), finalDamage);
        if (amount > 0) {
            stack.hurtAndBreak(amount, player, EquipmentSlot.CHEST);
        }
    }

    /** Removes every trace of this mod from the player's carried items; used on death. */
    public static void clearAll(ServerPlayer player) {
        Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            clearOnce(player.getInventory().getItem(slot), visited);
        }
        clearOnce(player.getItemBySlot(EquipmentSlot.CHEST), visited);
        clearOnce(player.getItemBySlot(EquipmentSlot.OFFHAND), visited);
        clearOnce(player.containerMenu.getCarried(), visited);
    }

    private static void clearOnce(ItemStack stack, Set<ItemStack> visited) {
        if (!stack.isEmpty() && visited.add(stack) && readTag(stack).getBooleanOr(MARKER, false)) {
            clear(stack);
        }
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag.getBooleanOr(HAD_ORIGINAL, false)) {
            float seconds = tag.getFloatOr(ORIGINAL_SECONDS, 0.0F);
            String originalGroup = tag.getStringOr(ORIGINAL_GROUP, "");
            Optional<Identifier> group = originalGroup.isEmpty()
                    ? Optional.empty()
                    : Optional.of(Identifier.parse(originalGroup));
            stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(seconds, group));
        } else {
            stack.remove(DataComponents.USE_COOLDOWN);
        }

        tag.remove(MARKER);
        tag.remove(EXPIRES_AT);
        tag.remove(GROUP);
        tag.remove(HAD_ORIGINAL);
        tag.remove(ORIGINAL_SECONDS);
        tag.remove(ORIGINAL_GROUP);
        writeTag(stack, tag);
    }

    private static CompoundTag readTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}

package dev.elytracombat;

import dev.elytracombat.compat.Compat;
import dev.elytracombat.compat.CooldownInfo;
import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Wears the "this elytra is disabled" marker on the elytra itself (in the stack's
 * {@code custom_data}), so the state follows the item across inventory moves, and tracks
 * an original cooldown component where the version supports one.
 */
public final class ElytraCooldowns {
    static final String MARKER = "elytra_combat_disabled";
    static final String EXPIRES_AT = "elytra_combat_expires_at";
    static final String GROUP = "elytra_combat_group";
    static final String HAD_ORIGINAL = "elytra_combat_had_original";
    static final String ORIGINAL_SECONDS = "elytra_combat_original_seconds";
    static final String ORIGINAL_GROUP = "elytra_combat_original_group";

    private ElytraCooldowns() {
    }

    public static boolean isElytra(ItemStack stack) {
        return Compat.isElytra(stack);
    }

    public static boolean isDisabled(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = readTag(stack);
        return Compat.tagBool(tag, MARKER, false) && remainingTicks(level, tag) > 0;
    }

    private static int remainingTicks(Level level, CompoundTag tag) {
        long remaining = Compat.tagLong(tag, EXPIRES_AT, 0L) - level.getGameTime();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }

    /**
     * Freshly disables an elytra, or refreshes it once an earlier marker has expired.
     * A hit never extends an ongoing disable: the original marker is left untouched so
     * graceless spammers cannot keep a pilot's elytra down indefinitely. Returns whether
     * the stack now carries a fresh disable.
     */
    public static boolean disable(ServerPlayer player, ItemStack stack) {
        if (!isElytra(stack)) {
            return false;
        }
        CompoundTag existing = readTag(stack);
        if (Compat.tagBool(existing, MARKER, false)) {
            if (remainingTicks(player.level(), existing) > 0) {
                return false;
            }
            clear(stack);
        }

        CompoundTag tag = readTag(stack);
        CooldownInfo original = Compat.getOriginalCooldown(stack);
        tag.putBoolean(HAD_ORIGINAL, original != null);
        if (original != null) {
            tag.putFloat(ORIGINAL_SECONDS, original.seconds());
            if (!original.group().isEmpty()) {
                tag.putString(ORIGINAL_GROUP, original.group());
            }
        }

        int ticks = ConfigManager.get().disableDurationTicks();
        String group = Compat.applyDisableCooldown(player, stack, ticks);
        tag.putBoolean(MARKER, true);
        if (!group.isEmpty()) {
            tag.putString(GROUP, group);
        }
        tag.putLong(EXPIRES_AT, player.level().getGameTime() + ticks);
        writeTag(stack, tag);
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
        if (Compat.tagBool(tag, MARKER, false) && Compat.tagLong(tag, EXPIRES_AT, 0L) <= player.level().getGameTime()) {
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
        if (!stack.isEmpty() && visited.add(stack) && Compat.tagBool(readTag(stack), MARKER, false)) {
            clear(stack);
        }
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (Compat.tagBool(tag, HAD_ORIGINAL, false)) {
            Compat.restoreCooldown(stack, new CooldownInfo(
                    Compat.tagFloat(tag, ORIGINAL_SECONDS, 0.0F),
                    Compat.tagString(tag, ORIGINAL_GROUP, "")));
        } else {
            Compat.restoreCooldown(stack, null);
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

package dev.elytracombat.compat;

import dev.elytracombat.ElytraCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla compatibility surface for Minecraft 1.21.2 - 1.21.4.
 *
 * <p>This generation introduced {@code ServerPlayer#hurtServer} and the
 * {@code use_cooldown} item component with cooldown groups, but still uses
 * {@code ResourceLocation} ids, the {@code location()} accessor on resource keys, the
 * permission bit on {@code CommandSourceStack#hasPermission}, and the old primitive NBT
 * getters (these changed to {@code Optional} returns in 1.21.5).
 */
public final class Compat {
    private Compat() {
    }

    /** Registry key of the mod's G-force damage type. */
    public static ResourceKey<DamageType> gForceDamageKey() {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElytraCombat.MOD_ID, "g_force"));
    }

    /** Whether the stack is an elytra (the glider data component exists from 1.21.2 on). */
    public static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponents.GLIDER) != null;
    }

    /** Builds the mod's G-force damage source. */
    public static DamageSource gForceDamageSource(ServerPlayer player) {
        return player.damageSources().source(gForceDamageKey());
    }

    public static Holder<MobEffect> nauseaEffect() {
        return MobEffects.CONFUSION;
    }

    public static Holder<MobEffect> darknessEffect() {
        return MobEffects.DARKNESS;
    }

    /** Runs a hurt through the server-side pipeline; returns whether it was accepted. */
    public static boolean hurt(ServerPlayer player, DamageSource source, float amount) {
        return player.hurtServer((ServerLevel) player.level(), source, amount);
    }

    /** Whether this command source may manage the mod (vanilla game-master level). */
    public static boolean isGameMaster(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    /** The damage type's {@code namespace:path} id. */
    public static String damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().orElseThrow().location().toString();
    }

    /**
     * Snaps the player's view to an absolute rotation. The {@code forceSetRotation} of this
     * era only takes yaw and pitch and was reshaped in 1.21.9, so the absolute teleport
     * (same position, new rotation) is used instead: it sends the position packet that
     * carries yaw and pitch, with no relative axes.
     */
    public static void forceSetRotation(ServerPlayer player, float yaw, float pitch) {
        player.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(),
                Set.<Relative>of(), yaw, pitch, true);
    }

    public static void stopFallFlying(ServerPlayer player) {
        player.stopFallFlying();
    }

    public static boolean tagBool(CompoundTag tag, String key, boolean def) {
        return tag.getBoolean(key);
    }

    public static long tagLong(CompoundTag tag, String key, long def) {
        return tag.getLong(key);
    }

    public static float tagFloat(CompoundTag tag, String key, float def) {
        return tag.getFloat(key);
    }

    public static String tagString(CompoundTag tag, String key, String def) {
        return tag.getString(key);
    }

    /** The stack's current cooldown component, or null when it has none. */
    public static CooldownInfo getOriginalCooldown(ItemStack stack) {
        UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
        if (cooldown == null) {
            return null;
        }
        String group = cooldown.cooldownGroup().map(ResourceLocation::toString).orElse("");
        return new CooldownInfo(cooldown.seconds(), group);
    }

    /**
     * Starts the item cooldown on a freshly disabled elytra. Returns the cooldown group id
     * ({@code namespace:path}) so the caller can record it, or "" when this version has no
     * cooldown groups.
     */
    public static String applyDisableCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        ResourceLocation group = ResourceLocation.fromNamespaceAndPath(ElytraCombat.MOD_ID, "disabled/" + UUID.randomUUID());
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(ticks / 20.0F, Optional.of(group)));
        player.getCooldowns().addCooldown(stack, ticks);
        return group.toString();
    }

    /** Restores the stack's pre-disable cooldown component; null info removes any cooldown. */
    public static void restoreCooldown(ItemStack stack, CooldownInfo info) {
        if (info == null) {
            stack.remove(DataComponents.USE_COOLDOWN);
            return;
        }
        Optional<ResourceLocation> group = info.group().isEmpty()
                ? Optional.empty()
                : Optional.of(ResourceLocation.parse(info.group()));
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(info.seconds(), group));
    }
}

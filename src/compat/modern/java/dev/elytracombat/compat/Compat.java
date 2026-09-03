package dev.elytracombat.compat;

import dev.elytracombat.ElytraCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;

import java.util.Optional;
import java.util.UUID;

/**
 * Vanilla compatibility surface for Minecraft 1.21.11 and the 26.x line (26.1 - 26.2).
 *
 * <p>This generation renamed {@code ResourceLocation} to {@code Identifier} (the class
 * kept its intermediary identity, so this is a mapping-only change), introduced the
 * {@code Permissions} API in place of the permission bit on
 * {@code CommandSourceStack#hasPermission}, renamed {@code ResourceKey#location()} to
 * {@code identifier()}, and ships the {@code get*Or} NBT accessors. The jars are
 * unobfuscated from 26.x on, so these names are the runtime names there.
 */
public final class Compat {
    private Compat() {
    }

    /** Registry key of the mod's G-force damage type. */
    public static ResourceKey<DamageType> gForceDamageKey() {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                Identifier.fromNamespaceAndPath(ElytraCombat.MOD_ID, "g_force"));
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
        return MobEffects.NAUSEA;
    }

    public static Holder<MobEffect> darknessEffect() {
        return MobEffects.DARKNESS;
    }

    /** Runs a hurt through the server-side pipeline; returns whether it was accepted. */
    public static boolean hurt(ServerPlayer player, DamageSource source, float amount) {
        return player.hurtServer(player.level(), source, amount);
    }

    /** Whether this command source may manage the mod (vanilla game-master level). */
    public static boolean isGameMaster(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** The damage type's {@code namespace:path} id. */
    public static String damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().orElseThrow().identifier().toString();
    }

    /**
     * Snaps the player's view to an absolute rotation through the server-side
     * {@code forceSetRotation} (absolute for both axes), which forwards the new angles to
     * the client without a teleport.
     */
    public static void forceSetRotation(ServerPlayer player, float yaw, float pitch) {
        player.forceSetRotation(yaw, false, pitch, false);
    }

    public static void stopFallFlying(ServerPlayer player) {
        player.stopFallFlying();
    }

    public static boolean tagBool(CompoundTag tag, String key, boolean def) {
        return tag.getBooleanOr(key, def);
    }

    public static long tagLong(CompoundTag tag, String key, long def) {
        return tag.getLongOr(key, def);
    }

    public static float tagFloat(CompoundTag tag, String key, float def) {
        return tag.getFloatOr(key, def);
    }

    public static String tagString(CompoundTag tag, String key, String def) {
        return tag.getStringOr(key, def);
    }

    /** The stack's current cooldown component, or null when it has none. */
    public static CooldownInfo getOriginalCooldown(ItemStack stack) {
        UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
        if (cooldown == null) {
            return null;
        }
        String group = cooldown.cooldownGroup().map(Identifier::toString).orElse("");
        return new CooldownInfo(cooldown.seconds(), group);
    }

    /**
     * Starts the item cooldown on a freshly disabled elytra. Returns the cooldown group id
     * ({@code namespace:path}) so the caller can record it, or "" when this version has no
     * cooldown groups.
     */
    public static String applyDisableCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        Identifier group = Identifier.fromNamespaceAndPath(ElytraCombat.MOD_ID, "disabled/" + UUID.randomUUID());
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
        Optional<Identifier> group = info.group().isEmpty()
                ? Optional.empty()
                : Optional.of(Identifier.parse(info.group()));
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown(info.seconds(), group));
    }
}

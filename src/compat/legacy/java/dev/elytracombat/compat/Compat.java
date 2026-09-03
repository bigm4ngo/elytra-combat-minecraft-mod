package dev.elytracombat.compat;

import dev.elytracombat.ElytraCombat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


/**
 * Vanilla compatibility surface for Minecraft 1.21 - 1.21.1.
 *
 * <p>This is the oldest API generation the mod supports: elytra damage still goes through
 * {@code LivingEntity#hurt}, there is no {@code use_cooldown} item component (item
 * cooldowns take an {@link ItemCooldowns} item, not a stack), the NBT getters return
 * primitives, and ids are {@link ResourceLocation}s.
 */
public final class Compat {
    private Compat() {
    }

    /** Registry key of the mod's G-force damage type. */
    public static ResourceKey<DamageType> gForceDamageKey() {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElytraCombat.MOD_ID, "g_force"));
    }

    /** Whether the stack is an elytra. 1.21/1.21.1 predates the glider data component. */
    public static boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ELYTRA);
    }

    /** Builds the mod's G-force damage source. */
    public static DamageSource gForceDamageSource(ServerPlayer player) {
        var holder = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(gForceDamageKey());
        // source(ResourceKey) is private in this generation; the public constructor takes
        // the damage type holder plus (nullable) direct and causing entities.
        return new DamageSource(holder, null, null);
    }

    public static Holder<MobEffect> nauseaEffect() {
        return MobEffects.CONFUSION;
    }

    public static Holder<MobEffect> darknessEffect() {
        return MobEffects.DARKNESS;
    }

    /** Runs a hurt through the server-side pipeline; returns whether it was accepted. */
    public static boolean hurt(ServerPlayer player, DamageSource source, float amount) {
        return player.hurt(source, amount);
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
     * Snaps the player's view to an absolute rotation. The 1.21/1.21.1 server player has no
     * {@code forceSetRotation}, so the classic absolute teleport (same position, new
     * rotation) does the job: it sends the position packet that carries yaw and pitch.
     */
    public static void forceSetRotation(ServerPlayer player, float yaw, float pitch) {
        player.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), yaw, pitch);
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
        return null; // no use_cooldown component on 1.21/1.21.1
    }

    /**
     * Starts the item cooldown on a freshly disabled elytra. Returns the cooldown group id
     * ({@code namespace:path}) so the caller can record it, or "" when this version has no
     * cooldown groups.
     */
    public static String applyDisableCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        player.getCooldowns().addCooldown(stack.getItem(), ticks);
        return "";
    }

    /** Restores the stack's pre-disable cooldown component; null info removes any cooldown. */
    public static void restoreCooldown(ItemStack stack, CooldownInfo info) {
        // no use_cooldown component on 1.21/1.21.1: the plain item cooldown expires on its own
    }
}

package dev.elytracombat;

import dev.elytracombat.compat.Compat;
import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs the disorientation that follows a mid-flight disable: the shock at the moment of
 * the hit and the tumble that plays out over the fall.
 *
 * <p>A session begins only when a worn elytra is disabled while its wearer is flying
 * (shot down, or swapping in an already disabled elytra mid-air). It ends on landing, in
 * water or lava, when the elytra stops being disabled, or on death. Everything else the
 * old sessions carried - nausea, darkness, and the G-force model itself - moved to
 * {@link GForce}, which monitors every player on every tick and drives those effects from
 * the load alone, so they fire for intense maneuvers and ordinary falls too, not just for
 * disables.
 */
public final class Freefall {
    private static final Map<ServerPlayer, FallState> STATES = new WeakHashMap<>();

    private Freefall() {
    }

    private static final class FallState {
        double impactSpeed;
        SpinModel.TurnRates turnRates;
    }

    /**
     * Starts a mid-flight session and applies the shock: sound, view snap, the first
     * nausea and darkness instances, and the sampled tumble. Call while the player is
     * still flying; {@code eventVelocity} is the velocity from just before the disabling
     * event, so the hit's own violence sets the tumble strength.
     */
    public static FallState beginMidFlight(ServerPlayer player, Vec3 eventVelocity) {
        FallState state = new FallState();
        state.impactSpeed = Mth.clamp(eventVelocity.length(), 0.0, SpinModel.MAX_IMPACT_SPEED);
        STATES.put(player, state);

        ElytraCombatConfig.Freefall config = ConfigManager.get().freefall;
        GForce.applyShockEffects(player);
        if (config.shockSound) {
            player.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 1.0F);
        }
        if (config.viewSnap) {
            float yawOffset = (player.getRandom().nextFloat() * 280.0F) - 140.0F;
            float pitchOffset = (player.getRandom().nextFloat() * 70.0F) - 45.0F;
            Compat.forceSetRotation(player, player.getYRot() + yawOffset,
                    Mth.clamp(player.getXRot() + pitchOffset, -90.0F, 90.0F));
        }
        state.turnRates = SpinModel.sample(state.impactSpeed, config.spinIntensity, player.getRandom()::nextDouble);
        return state;
    }

    public static void tick(ServerPlayer player) {
        ElytraCooldowns.tickExpired(player);

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean disabled = ElytraCooldowns.isDisabled(player.level(), chest);
        FallState state = STATES.get(player);

        if (state != null && (player.isDeadOrDying() || player.isRemoved() || !disabled || fallOver(player))) {
            // Landing, water, climbing, or the disabled elytra leaving the chest slot.
            STATES.remove(player);
            return;
        }

        // Flying with a disabled elytra on (e.g. swapping it in mid-air) ends the flight
        // and counts as a mid-flight disable.
        if (disabled && player.isFallFlying()) {
            state = beginMidFlight(player, player.getDeltaMovement());
            Compat.stopFallFlying(player);
        }
        if (state != null) {
            applySpin(player, state);
        }
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player);
    }

    /** The fall ended because the victim reached something solid, liquid, or climbable. */
    private static boolean fallOver(ServerPlayer player) {
        return player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable();
    }

    private static void applySpin(ServerPlayer player, FallState state) {
        SpinModel.TurnRates rates = state.turnRates;
        if (rates == null) {
            return;
        }
        if (rates.expired()) {
            state.turnRates = null;
            return;
        }
        double nextPitch = player.getXRot() + rates.pitchRate();
        double pitchRate = nextPitch > 90.0 || nextPitch < -90.0 ? 0.0 : rates.pitchRate();
        Compat.forceSetRotation(player, player.getYRot() + (float) rates.yawRate(),
                player.getXRot() + (float) pitchRate);
        state.turnRates = rates.decayed();
    }
}

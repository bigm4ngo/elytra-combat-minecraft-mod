package dev.elytracombat.mixin;

import dev.elytracombat.ElytraCooldowns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class LivingEntityFlightMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void elytraCombat$preventDisabledFlight(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (ElytraCooldowns.isDisabled(player.level(), chest)) {
                cir.setReturnValue(false);
            }
        }
    }
}

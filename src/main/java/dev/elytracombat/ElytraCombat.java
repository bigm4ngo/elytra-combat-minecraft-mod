package dev.elytracombat;

import com.mojang.brigadier.Command;
import dev.elytracombat.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElytraCombat implements ModInitializer {
    public static final String MOD_ID = "elytra_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.loadAtStartup();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                // The G monitor first: its damage can freshly disable an elytra, and the
                // shock session that starts there is ticked right after in the same pass.
                GForce.tick(player);
                Freefall.tick(player);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("elytracombat")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("reload").executes(context -> {
                            try {
                                ConfigManager.reload();
                                context.getSource().sendSuccess(() -> Component.literal("Elytra Combat config reloaded."), false);
                                return Command.SINGLE_SUCCESS;
                            } catch (Exception exception) {
                                context.getSource().sendFailure(Component.literal("Elytra Combat config is invalid: " + exception.getMessage()));
                                return 0;
                            }
                        }))
        ));

        LOGGER.info("Elytra Combat initialized");
    }
}

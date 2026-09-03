package dev.elytracombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Client-side vanilla compatibility for Minecraft 1.21 - 1.21.10: the two-argument
 * {@code CycleButton.builder} overload only exists from 1.21.11 on, and
 * {@code Minecraft#setScreenAndShow} from 1.21.10 on (vanilla removed plain
 * {@code setScreen} again in 26.2).
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static <T> CycleButton.Builder<T> cycleButton(Function<T, Component> labeler, T initial) {
        return CycleButton.<T>builder(labeler).withInitialValue(initial);
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }
}

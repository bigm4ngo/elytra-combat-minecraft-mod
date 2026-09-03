package dev.elytracombat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/**
 * Client-side vanilla compatibility for Minecraft 1.21.11 and 26.x: the two-argument
 * {@code CycleButton.builder} overload (labeler + initial value) exists from 1.21.11 on,
 * and vanilla removed plain {@code Minecraft#setScreen} in 26.2 in favour of
 * {@code setScreenAndShow} (present from 1.21.10 on).
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static <T> CycleButton.Builder<T> cycleButton(Function<T, Component> labeler, T initial) {
        return CycleButton.builder(labeler, initial);
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreenAndShow(screen);
    }
}

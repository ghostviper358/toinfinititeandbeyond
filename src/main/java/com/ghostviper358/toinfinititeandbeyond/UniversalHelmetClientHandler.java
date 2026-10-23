package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for the Universal Helmet's two triggered abilities - Cosmic Eye Beam (R by
 * default) and Time Stop (G by default). Both can be rebound under Options > Controls, in this
 * mod's own section. See docs/requirements/universal_helmet.md.
 */
public final class UniversalHelmetClientHandler {

    private static final String CATEGORY = "key.categories." + ToInfinititeAndBeyond.MOD_ID;

    public static final KeyMapping EYE_BEAM_KEY = new KeyMapping(
        "key." + ToInfinititeAndBeyond.MOD_ID + ".eye_beam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    public static final KeyMapping TIME_STOP_KEY = new KeyMapping(
        "key." + ToInfinititeAndBeyond.MOD_ID + ".time_stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private UniversalHelmetClientHandler() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(EYE_BEAM_KEY);
        event.register(TIME_STOP_KEY);
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean wearing = player != null && UniversalHelmetItem.isWearing(player);
        // Always drain the presses, so a press made without the helmet on doesn't fire later.
        while (EYE_BEAM_KEY.consumeClick()) {
            if (wearing) {
                UniversalHelmetNetworking.sendEyeBeamToServer();
            }
        }
        while (TIME_STOP_KEY.consumeClick()) {
            if (wearing) {
                UniversalHelmetNetworking.sendTimeStopToServer();
            }
        }
    }
}

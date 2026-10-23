package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for a Universal God's powers - Meteor (H), Lightning Storm (J), Delete (K), and
 * World Control (N for day/night, Shift+N for weather) by default. All can be rebound under
 * Options > Controls, in this mod's own section. See docs/requirements/universal_core.md.
 *
 * Also hides the "you're inside a block" screen overlay while a god is flying through walls.
 */
public final class UniversalCoreClientHandler {

    private static final String CATEGORY = "key.categories." + ToInfinititeAndBeyond.MOD_ID;

    public static final KeyMapping METEOR_KEY = key("meteor", GLFW.GLFW_KEY_H);
    public static final KeyMapping LIGHTNING_STORM_KEY = key("lightning_storm", GLFW.GLFW_KEY_J);
    public static final KeyMapping DELETE_KEY = key("delete", GLFW.GLFW_KEY_K);
    public static final KeyMapping WORLD_CONTROL_KEY = key("world_control", GLFW.GLFW_KEY_N);

    private UniversalCoreClientHandler() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ToInfinititeAndBeyond.MOD_ID + "." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(METEOR_KEY);
        event.register(LIGHTNING_STORM_KEY);
        event.register(DELETE_KEY);
        event.register(WORLD_CONTROL_KEY);
    }

    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean god = player != null && UniversalCoreItem.isGod(player);
        // Always drain the presses, so a press made before becoming a god doesn't fire later.
        while (METEOR_KEY.consumeClick()) {
            if (god) {
                UniversalCoreNetworking.sendMeteorToServer();
            }
        }
        while (LIGHTNING_STORM_KEY.consumeClick()) {
            if (god) {
                UniversalCoreNetworking.sendLightningStormToServer();
            }
        }
        while (DELETE_KEY.consumeClick()) {
            if (god) {
                UniversalCoreNetworking.sendDeleteToServer();
            }
        }
        while (WORLD_CONTROL_KEY.consumeClick()) {
            if (god) {
                UniversalCoreNetworking.sendWorldControlToServer(player.isShiftKeyDown());
            }
        }
    }

    /** @return true to cancel - hides the block texture filling the screen while a god flies through walls */
    public static boolean onBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        return event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK
            && player != null && player.getAbilities().flying && UniversalCoreItem.isGod(player);
    }
}

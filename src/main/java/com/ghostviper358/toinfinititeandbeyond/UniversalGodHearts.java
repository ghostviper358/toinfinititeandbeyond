package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/**
 * A Universal God's hearts are gold and white instead of red. See docs/requirements/universal_core.md.
 *
 * Minecraft draws hearts as part of the hotbar, with no hook to change their colour, so this adds
 * a GUI layer straight after the hotbar that draws a gold heart over every heart in exactly the
 * same spot - the same layout maths as vanilla's Gui.renderHearts (rows squeezing closer together
 * the more hearts you have, and the little hop a heart does while regenerating). Absorption
 * hearts come out white.
 */
public final class UniversalGodHearts {

    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_god_hearts");

    private static final ResourceLocation CONTAINER = sprite("container");
    private static final ResourceLocation FULL = sprite("full");
    private static final ResourceLocation HALF = sprite("half");
    private static final ResourceLocation ABSORBING_FULL = sprite("absorbing_full");
    private static final ResourceLocation ABSORBING_HALF = sprite("absorbing_half");

    private UniversalGodHearts() {
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "hud/god_heart/" + name);
    }

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, LAYER_ID, ForgeLayeredDraw.HOTBAR_AND_DECOS,
            UniversalGodHearts::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        Player player = minecraft.gui.getCameraPlayer();
        if (player == null || !UniversalCoreItem.isGod(player)) {
            return;
        }

        int health = Mth.ceil(player.getHealth());
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int rows = Mth.ceil((maxHealth + absorption) / 2.0f / 10.0f);
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        int left = graphics.guiWidth() / 2 - 91;
        int bottom = graphics.guiHeight() - 39;
        int hoppingHeart = player.hasEffect(MobEffects.REGENERATION)
            ? minecraft.gui.getGuiTicks() % Mth.ceil(maxHealth + 5.0f) : -1;

        int healthHearts = Mth.ceil(maxHealth / 2.0f);
        int absorptionHearts = Mth.ceil(absorption / 2.0f);
        for (int i = healthHearts + absorptionHearts - 1; i >= 0; i--) {
            int x = left + (i % 10) * 8;
            int y = bottom - (i / 10) * rowSpacing;
            if (i < healthHearts && i == hoppingHeart) {
                y -= 2;
            }
            draw(graphics, CONTAINER, x, y);

            int halfHearts = i * 2;
            if (i >= healthHearts) {
                int absorbed = halfHearts - healthHearts * 2;
                if (absorbed < absorption) {
                    draw(graphics, absorbed + 1 == absorption ? ABSORBING_HALF : ABSORBING_FULL, x, y);
                }
            } else if (halfHearts < health) {
                draw(graphics, halfHearts + 1 == health ? HALF : FULL, x, y);
            }
        }
    }

    private static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 9, 9);
    }
}

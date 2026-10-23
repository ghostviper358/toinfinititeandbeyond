package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

import java.util.Comparator;

/**
 * The Infinitite Monstrosity's boss bar, at the top of the screen: a rock-framed bar with spiked
 * ends and a cracked purple fill (blazing pink once it's enraged), a pixel-art portrait of the
 * golem sitting right on top of it, and its name underneath. The portrait's eyes, core and cracks
 * pulse with light. Sprites are painted by gen_monstrosity_boss_bar.py.
 *
 * Drawn entirely client-side, straight from the nearest Monstrosity's synced health and phase,
 * whenever one is within {@link #SHOW_WITHIN_BLOCKS} - so there's no vanilla boss bar to hide.
 */
public final class MonstrosityBossBar {

    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "monstrosity_boss_bar");

    private static final ResourceLocation FRAME = sprite("frame");
    private static final ResourceLocation EMPTY = sprite("empty");
    private static final ResourceLocation FILL = sprite("fill");
    private static final ResourceLocation FILL_ENRAGED = sprite("fill_enraged");
    private static final ResourceLocation PORTRAIT = sprite("portrait");
    private static final ResourceLocation PORTRAIT_ENRAGED = sprite("portrait_enraged");
    private static final ResourceLocation PORTRAIT_GLOW = sprite("portrait_glow");
    private static final ResourceLocation PORTRAIT_GLOW_ENRAGED = sprite("portrait_glow_enraged");

    private static final double SHOW_WITHIN_BLOCKS = 64.0;

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int PORTRAIT_SIZE = 32;
    private static final int PORTRAIT_TOP = 2;
    /** The frame starts this far up inside the portrait, so the portrait sits on the bar. */
    private static final int PORTRAIT_OVERLAP = 4;

    private MonstrosityBossBar() {
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "boss_bar/monstrosity/" + name);
    }

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, LAYER_ID, ForgeLayeredDraw.BOSS_OVERLAY,
            MonstrosityBossBar::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        InfinititeMonstrosity boss = minecraft.level.getEntitiesOfClass(InfinititeMonstrosity.class,
                player.getBoundingBox().inflate(SHOW_WITHIN_BLOCKS), InfinititeMonstrosity::isAlive)
            .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (boss == null) {
            return;
        }

        boolean enraged = boss.isEnraged();
        float progress = Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0.0f, 1.0f);
        float seconds = (minecraft.gui.getGuiTicks() + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20.0f;
        int centerX = graphics.guiWidth() / 2;
        int barX = centerX - BAR_WIDTH / 2;
        int frameY = PORTRAIT_TOP + PORTRAIT_SIZE - PORTRAIT_OVERLAP;
        int barY = frameY + 4;

        // The bar: drained part, the fill up to its health, then the rock frame around it.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY, barX, barY, BAR_WIDTH, BAR_HEIGHT);
        int filled = Mth.ceil(progress * BAR_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, enraged ? FILL_ENRAGED : FILL,
                BAR_WIDTH, BAR_HEIGHT, 0, 0, barX, barY, filled, BAR_HEIGHT);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, barX - 11, frameY, 204, 13);

        // The golem sitting on top of the bar, its glowing parts pulsing (faster once enraged).
        int portraitX = centerX - PORTRAIT_SIZE / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, enraged ? PORTRAIT_ENRAGED : PORTRAIT,
            portraitX, PORTRAIT_TOP, PORTRAIT_SIZE, PORTRAIT_SIZE);
        float pulse = 0.55f + 0.45f * Mth.sin(seconds * (enraged ? 9.0f : 4.0f));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, enraged ? PORTRAIT_GLOW_ENRAGED : PORTRAIT_GLOW,
            portraitX, PORTRAIT_TOP, PORTRAIT_SIZE, PORTRAIT_SIZE, ARGB.white(pulse));

        // Its name, under the bar.
        Component name = boss.hasCustomName() ? boss.getDisplayName()
            : Component.translatable(enraged ? "entity.toinfinititeandbeyond.infinitite_monstrosity.enraged"
                : "entity.toinfinititeandbeyond.infinitite_monstrosity");
        graphics.drawCenteredString(minecraft.font, name, centerX, frameY + 15, enraged ? 0xFFFF9BEB : 0xFFE2B6FF);
    }
}

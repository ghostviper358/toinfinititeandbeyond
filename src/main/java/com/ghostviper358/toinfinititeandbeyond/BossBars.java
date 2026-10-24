package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The mod's custom boss bars, at the top of the screen. Each is a decorated frame around the bar,
 * the fill draining as the boss is hurt, a pixel-art portrait of the boss sitting right on top of
 * the bar (its glowing parts pulsing), and the boss's name underneath. If more than one boss is
 * nearby, their bars stack downward.
 *
 * Drawn entirely client-side, straight from each nearby boss's synced health - so there's no
 * vanilla boss bar to hide. Sprites live in textures/gui/sprites/boss_bar/<boss>/ (painted by
 * gen_monstrosity_boss_bar.py, gen_living_void_texture.py and gen_universal_destroyer_texture.py).
 */
public final class BossBars {

    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "boss_bars");

    /** A boss bar is shown for a boss within this many blocks. */
    private static final double SHOW_WITHIN_BLOCKS = 64.0;

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int PORTRAIT_SIZE = 32;
    private static final int TOP = 2;
    /** The frame starts this far up inside the portrait, so the portrait sits on the bar. */
    private static final int PORTRAIT_OVERLAP = 4;
    /** Room each bar takes, so a second one goes underneath. */
    private static final int SPACING = 62;

    /**
     * How one boss's bar looks. {@code angry} picks the "_enraged" sprites, name and colour (only
     * bosses with an enraged phase have those).
     */
    private record Style(Class<? extends LivingEntity> type, String folder, String nameKey,
                         int nameColor, int angryNameColor, Predicate<LivingEntity> angry) {

        ResourceLocation sprite(String name, boolean angry) {
            return ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID,
                "boss_bar/" + folder + "/" + name + (angry ? "_enraged" : ""));
        }
    }

    private static final List<Style> STYLES = List.of(
        new Style(InfinititeMonstrosity.class, "monstrosity", "entity.toinfinititeandbeyond.infinitite_monstrosity",
            0xFFE2B6FF, 0xFFFF9BEB, boss -> ((InfinititeMonstrosity) boss).isEnraged()),
        new Style(LivingVoid.class, "living_void", "entity.toinfinititeandbeyond.living_void",
            0xFFC9A6FF, 0xFFC9A6FF, boss -> false),
        new Style(UniversalDestroyer.class, "universal_destroyer", "entity.toinfinititeandbeyond.universal_destroyer",
            0xFFD9B8FF, 0xFFFF5A5A, boss -> ((UniversalDestroyer) boss).getPhase() >= 3)
    );

    private BossBars() {
    }

    public static void onAddLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, LAYER_ID, ForgeLayeredDraw.BOSS_OVERLAY,
            BossBars::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        float seconds = (minecraft.gui.getGuiTicks() + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20.0f;
        int top = TOP;
        for (Style style : STYLES) {
            LivingEntity boss = minecraft.level.getEntitiesOfClass(style.type(),
                    player.getBoundingBox().inflate(SHOW_WITHIN_BLOCKS), LivingEntity::isAlive)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            if (boss != null) {
                draw(graphics, minecraft, style, boss, top, seconds);
                top += SPACING;
            }
        }
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, Style style, LivingEntity boss, int top, float seconds) {
        boolean angry = style.angry().test(boss);
        float progress = boss instanceof ModBoss modBoss ? modBoss.getBossHealthFraction()
            : Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0.0f, 1.0f);
        int centerX = graphics.guiWidth() / 2;
        int barX = centerX - BAR_WIDTH / 2;
        int frameY = top + PORTRAIT_SIZE - PORTRAIT_OVERLAP;
        int barY = frameY + 4;

        // The bar: drained part, the fill up to its health, then the frame around it.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("empty", false), barX, barY, BAR_WIDTH, BAR_HEIGHT);
        int filled = Mth.ceil(progress * BAR_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("fill", angry),
                BAR_WIDTH, BAR_HEIGHT, 0, 0, barX, barY, filled, BAR_HEIGHT);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("frame", false), barX - 11, frameY, 204, 13);

        // The boss sitting on top of the bar, its glowing parts pulsing (faster when angry).
        int portraitX = centerX - PORTRAIT_SIZE / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("portrait", angry), portraitX, top, PORTRAIT_SIZE, PORTRAIT_SIZE);
        float pulse = 0.55f + 0.45f * Mth.sin(seconds * (angry ? 9.0f : 4.0f));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("portrait_glow", angry),
            portraitX, top, PORTRAIT_SIZE, PORTRAIT_SIZE, ARGB.white(pulse));

        // Its name, under the bar.
        Component name = boss.hasCustomName() ? boss.getDisplayName()
            : Component.translatable(angry ? style.nameKey() + ".enraged" : style.nameKey());
        graphics.drawCenteredString(minecraft.font, name, centerX, frameY + 15, angry ? style.angryNameColor() : style.nameColor());
    }
}

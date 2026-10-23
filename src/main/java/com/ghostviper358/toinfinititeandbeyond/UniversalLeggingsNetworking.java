package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Client -> server packets for the Universal Leggings' key-triggered abilities.
 * See docs/requirements/universal_leggings.md.
 *
 * Same reason as UniversalSwordNetworking: only the client knows when keys are pressed, so
 * UniversalLeggingsClientHandler spots the double-tapped sneak (Warp Dash) and mid-air jumps
 * (Star Jumps) and sends these data-less "it happened" signals. The server checks the wearer
 * really has the leggings on before doing anything (see UniversalLeggingsItem).
 */
public final class UniversalLeggingsNetworking {

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_leggings"))
        .networkProtocolVersion(1)
        .simpleChannel();

    private UniversalLeggingsNetworking() {
    }

    /** Registers this mod's leggings packets. Must be called once, during mod construction. */
    public static void register() {
        CHANNEL.messageBuilder(WarpDashPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new WarpDashPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalLeggingsItem.handleWarpDash(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(ExtraJumpPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new ExtraJumpPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalLeggingsItem.handleExtraJump(sender);
                }
            })
            .add();
    }

    /** Client -> server: the wearer double-tapped sneak. */
    public static final class WarpDashPacket {
    }

    /** Client -> server: the wearer just jumped in mid-air. */
    public static final class ExtraJumpPacket {
    }

    public static void sendWarpDashToServer() {
        CHANNEL.send(new WarpDashPacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendExtraJumpToServer() {
        CHANNEL.send(new ExtraJumpPacket(), PacketDistributor.SERVER.noArg());
    }
}

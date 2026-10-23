package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Client -> server packets for the Universal Helmet's key-triggered abilities.
 * See docs/requirements/universal_helmet.md.
 *
 * UniversalHelmetClientHandler sends these data-less "key pressed" signals; the server checks the
 * helmet is really being worn and handles cooldowns (see UniversalHelmetItem).
 */
public final class UniversalHelmetNetworking {

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_helmet"))
        .networkProtocolVersion(1)
        .simpleChannel();

    private UniversalHelmetNetworking() {
    }

    /** Registers this mod's helmet packets. Must be called once, during mod construction. */
    public static void register() {
        CHANNEL.messageBuilder(EyeBeamPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new EyeBeamPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalHelmetItem.handleEyeBeam(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(TimeStopPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new TimeStopPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalHelmetItem.handleTimeStop(sender);
                }
            })
            .add();
    }

    /** Client -> server: the wearer pressed the Eye Beam key. */
    public static final class EyeBeamPacket {
    }

    /** Client -> server: the wearer pressed the Time Stop key. */
    public static final class TimeStopPacket {
    }

    public static void sendEyeBeamToServer() {
        CHANNEL.send(new EyeBeamPacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendTimeStopToServer() {
        CHANNEL.send(new TimeStopPacket(), PacketDistributor.SERVER.noArg());
    }
}

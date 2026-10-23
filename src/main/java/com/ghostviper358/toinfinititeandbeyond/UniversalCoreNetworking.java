package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Client -> server packets for a Universal God's key powers. See docs/requirements/universal_core.md.
 *
 * UniversalCoreClientHandler sends these "key pressed" signals; the server checks the sender
 * really is a god and handles cooldowns (see UniversalCoreItem).
 */
public final class UniversalCoreNetworking {

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_core"))
        .networkProtocolVersion(1)
        .simpleChannel();

    private UniversalCoreNetworking() {
    }

    /** Registers this mod's god-power packets. Must be called once, during mod construction. */
    public static void register() {
        CHANNEL.messageBuilder(MeteorPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new MeteorPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalCoreItem.handleMeteor(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(LightningStormPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new LightningStormPacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalCoreItem.handleLightningStorm(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(DeletePacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new DeletePacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalCoreItem.handleDelete(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(WorldControlPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> buf.writeBoolean(packet.weather()))
            .decoder(buf -> new WorldControlPacket(buf.readBoolean()))
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalCoreItem.handleWorldControl(sender, packet.weather());
                }
            })
            .add();
    }

    /** Client -> server: the god pressed the Meteor key. */
    public static final class MeteorPacket {
    }

    /** Client -> server: the god pressed the Lightning Storm key. */
    public static final class LightningStormPacket {
    }

    /** Client -> server: the god pressed the Delete key. */
    public static final class DeletePacket {
    }

    /** Client -> server: the god pressed the World Control key - weather if they were sneaking, else day/night. */
    public record WorldControlPacket(boolean weather) {
    }

    public static void sendMeteorToServer() {
        CHANNEL.send(new MeteorPacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendLightningStormToServer() {
        CHANNEL.send(new LightningStormPacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendDeleteToServer() {
        CHANNEL.send(new DeletePacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendWorldControlToServer(boolean weather) {
        CHANNEL.send(new WorldControlPacket(weather), PacketDistributor.SERVER.noArg());
    }
}

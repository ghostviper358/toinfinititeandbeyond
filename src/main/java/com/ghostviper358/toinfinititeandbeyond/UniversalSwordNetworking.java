package com.ghostviper358.toinfinititeandbeyond;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * Minimal networking support for the Universal Sword's charge-up shockwave ability.
 * See docs/requirements/universal_sword_shockwave.md ("Part 2: The Shockwave Ability").
 *
 * Why this exists: whether the attack key is currently held down is client-only information
 * (there's no vanilla server-side "is this key down" concept), so the client has to track the
 * charge itself and then tell the server once the charge is complete. This is the only packet
 * needed - it carries no data, it's purely a "the charge finished, fire the shockwave now"
 * signal. Everything the ability actually does (the five rings, weather, instant-kills, player
 * floating) happens entirely server-side once this packet is received, so nothing needs to be
 * sent back down to clients.
 *
 * This project had no networking/packet infrastructure before this feature (no SimpleChannel,
 * PayloadRegistrar, etc. existed anywhere else in the codebase). Forge 58.1.0 (this project's
 * version, for Minecraft 1.21.8 - see gradle.properties) still ships the classic
 * ChannelBuilder/SimpleChannel API (net.minecraftforge.network.ChannelBuilder), which is what's
 * used here - this is Forge, not NeoForge, so the newer NeoForge-only
 * RegisterPayloadHandlersEvent API doesn't apply to this project.
 */
public final class UniversalSwordNetworking {

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(ToInfinititeAndBeyond.MOD_ID, "universal_sword"))
        .networkProtocolVersion(1)
        .simpleChannel();

    private UniversalSwordNetworking() {
    }

    /** Registers this mod's packet(s). Must be called once, during mod construction. */
    public static void register() {
        CHANNEL.messageBuilder(TriggerShockwavePacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields - the packet itself is the whole message */ })
            .decoder(buf -> new TriggerShockwavePacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalSwordItem.handleShockwaveTrigger(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(StartChargePacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new StartChargePacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalSwordItem.handleStartCharge(sender);
                }
            })
            .add();

        CHANNEL.messageBuilder(StopChargePacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder((packet, buf) -> { /* no fields */ })
            .decoder(buf -> new StopChargePacket())
            .consumerMainThread((packet, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    UniversalSwordItem.handleStopCharge(sender);
                }
            })
            .add();
    }

    /** Client -> server: sent once, when the charge-up completes, to fire the shockwave. */
    public static final class TriggerShockwavePacket {
    }

    /** Client -> server: sent once, the instant the player starts holding attack with this sword out - kicks off the storm. */
    public static final class StartChargePacket {
    }

    /** Client -> server: sent once, the instant the player stops holding attack (or switches away) - clears the storm. */
    public static final class StopChargePacket {
    }

    /** Convenience for the client charge handler. */
    public static void sendTriggerToServer() {
        CHANNEL.send(new TriggerShockwavePacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendStartChargeToServer() {
        CHANNEL.send(new StartChargePacket(), PacketDistributor.SERVER.noArg());
    }

    public static void sendStopChargeToServer() {
        CHANNEL.send(new StopChargePacket(), PacketDistributor.SERVER.noArg());
    }
}
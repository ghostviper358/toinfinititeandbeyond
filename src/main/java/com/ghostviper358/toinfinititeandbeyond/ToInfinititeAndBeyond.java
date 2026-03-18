package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ToInfinititeAndBeyond.MOD_ID)
public final class ToInfinititeAndBeyond {
    // Define mod id in a common place
    // for everything to reference
    public static final String MOD_ID = "toinfinititeandbeyond";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // Create a Deferred Register to hold Entity Types
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    // Creates the Sapphire item
    public static final RegistryObject<Item> SAPPHIRE = ITEMS.register("sapphire",
        () -> new Item(new Item.Properties()
            .setId(ITEMS.key("sapphire"))
        )
    );

    // Creates the Infinitite Ingot item for the Infinitite Sword
    public static final RegistryObject<Item> INFINITITE_INGOT = ITEMS.register("infinitite_ingot",
        () -> new Item(new Item.Properties()
            .setId(ITEMS.key("infinitite_ingot"))
        )
    );

    // Creates the Infinitite Sword with special combat effects
    public static final RegistryObject<Item> INFINITITE_SWORD = ITEMS.register("infinitite_sword",
        () -> new InfinititeSwordItem(new Item.Properties()
            .setId(ITEMS.key("infinitite_sword"))
            .durability(3124) // Double diamond sword durability (1562 * 2)
            .attributes(ItemAttributeModifiers.builder()
                .add(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("infinitite_attack_damage"),
                        7.0, // +7 damage (4 base + 7 = 11 total)
                        AttributeModifier.Operation.ADD_VALUE
                    ),
                    EquipmentSlotGroup.MAINHAND
                )
                .build()
            )
        )
    );

    // Creates the Infinitite Bow with lock-on targeting
    public static final RegistryObject<Item> INFINITITE_BOW = ITEMS.register("infinitite_bow",
        () -> new InfinititeBowItem(new Item.Properties()
            .setId(ITEMS.key("infinitite_bow"))
            .durability(3124) // Same durability as Infinitite Sword
        )
    );

    // Creates the Infinitite Arrow - special ammo that works extra well with Infinitite Bow
    public static final RegistryObject<Item> INFINITITE_ARROW_ITEM = ITEMS.register("infinitite_arrow",
        () -> new Item(new Item.Properties()
            .setId(ITEMS.key("infinitite_arrow"))
        )
    );

    // Register the custom Infinitite Arrow entity type
    public static final RegistryObject<EntityType<InfinititeArrowEntity>> INFINITITE_ARROW = ENTITY_TYPES.register("infinitite_arrow",
        () -> EntityType.Builder.<InfinititeArrowEntity>of(InfinititeArrowEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(4)
            .updateInterval(20)
            .build(ENTITY_TYPES.key("infinitite_arrow"))
    );

    // Creates the Infinitite Chest Plate with permanent buffs, damage reflection, and +5 hearts
    public static final RegistryObject<Item> INFINITITE_CHESTPLATE = ITEMS.register("infinitite_chestplate",
        () -> new InfinititeArmorItem(
            new Item.Properties()
                .setId(ITEMS.key("infinitite_chestplate"))
                .durability(InfinititeArmorMaterial.DURABILITY)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_health_boost"),
                            10.0, // +10 health (5 hearts)
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_armor"),
                            (double) InfinititeArmorMaterial.ARMOR, // +12 armor points
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_toughness"),
                            (double) InfinititeArmorMaterial.TOUGHNESS, // +5 toughness
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_knockback_resistance"),
                            (double) InfinititeArmorMaterial.KNOCKBACK_RESISTANCE, // 20% knockback resistance
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .build()
                )
        )
    );

    // Creates the Infinitite Leggings - Rocket Legs + Speed Demon + Ghost Walker + Gravity Master
    public static final RegistryObject<Item> INFINITITE_LEGGINGS = ITEMS.register("infinitite_leggings",
        () -> new InfinititeLeggingsItem(
            new Item.Properties()
                .setId(ITEMS.key("infinitite_leggings"))
                .durability(InfinititeArmorMaterial.DURABILITY)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_leggings_armor"),
                            10.0, // +10 armor (netherite legs = 6)
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_leggings_toughness"),
                            4.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_leggings_speed"),
                            0.04, // +40% move speed
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.JUMP_STRENGTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_leggings_jump"),
                            0.5, // higher jumps
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .build()
                )
        )
    );

    // Helper method to create ResourceLocation
    public static ResourceLocation resourceLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public ToInfinititeAndBeyond(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modBusGroup);

        // Register the entity types
        ENTITY_TYPES.register(modBusGroup);

        // Register the item to a creative tab
        BuildCreativeModeTabContentsEvent.getBus(modBusGroup).addListener(ToInfinititeAndBeyond::addCreative);

        // Register game event for Infinitite Chest Plate abilities (ranged immunity + damage reflection)
        // Using Predicate form: returning true cancels the event (blocks the damage)
        LivingHurtEvent.BUS.addListener((java.util.function.Predicate<LivingHurtEvent>) ToInfinititeAndBeyond::onLivingHurt);

        // Register events for Infinitite Leggings abilities
        LivingFallEvent.BUS.addListener((java.util.function.Predicate<LivingFallEvent>) ToInfinititeAndBeyond::onLivingFall);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(ToInfinititeAndBeyond::onPlayerTick);
    }

    // Add our custom items to creative tabs
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SAPPHIRE.get());
            event.accept(INFINITITE_INGOT.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(INFINITITE_SWORD.get());
            event.accept(INFINITITE_BOW.get());
            event.accept(INFINITITE_ARROW_ITEM.get());
            event.accept(INFINITITE_CHESTPLATE.get());
            event.accept(INFINITITE_LEGGINGS.get());
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    /**
     * ROCKET LEGS: cancel fall damage and trigger landing shockwave.
     */
    public static boolean onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && InfinititeLeggingsItem.isWearing(player)) {
            return InfinititeLeggingsItem.handleLanding(player, event.getDistance());
        }
        return false;
    }

    /**
     * GRAVITY MASTER: slam down when sneaking in mid-air.
     */
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player;
        if (!player.level().isClientSide()) {
            InfinititeLeggingsItem.handleGravitySlam(player);
        }
    }

    /**
     * Called when a living entity takes damage.
     * Handles two Infinitite Chest Plate abilities:
     * 1. Invincibility to ranged (projectile) attacks - returns true to cancel
     * 2. 25% damage reflection back to melee attackers
     *
     * @return true to cancel the damage event (block the hit entirely)
     */
    public static boolean onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        // Only apply if wearing the Infinitite Chest Plate
        if (!(entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof InfinititeArmorItem)) {
            return false;
        }

        Entity directEntity = event.getSource().getDirectEntity();

        // Block all projectile (ranged) damage completely
        if (directEntity instanceof Projectile) {
            return true; // cancel the event = no damage taken
        }

        // Reflect 25% of melee damage back to attacker
        Entity attacker = event.getSource().getEntity();
        if (attacker != null) {
            InfinititeArmorItem.handleDamageReflection(entity, attacker, event.getAmount());
        }
        return false;
    }
}

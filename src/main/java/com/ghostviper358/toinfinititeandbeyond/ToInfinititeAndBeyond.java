package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
}

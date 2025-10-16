package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
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
@Mod(JasperTutorial.MOD_ID)
public final class JasperTutorial {
    // Define mod id in a common place
    // for everything to reference
    public static final String MOD_ID = "jaspertutorial";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

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

    public JasperTutorial(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modBusGroup);

        // Register the item to a creative tab
        BuildCreativeModeTabContentsEvent.getBus(modBusGroup).addListener(JasperTutorial::addCreative);
    }

    // Add our custom items to creative tabs
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SAPPHIRE.get());
            event.accept(INFINITITE_INGOT.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(INFINITITE_SWORD.get());
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

package com.ghostviper358.toinfinititeandbeyond;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.util.Unit;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

    // Create a Deferred Register to hold Blocks
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);

    // Create a Deferred Register to hold Entity Types
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    // Create a Deferred Register to hold Mob Effects
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);

    // Cosmic Invincibility - custom effect granted by the Cosmic Apple. Complete damage
    // immunity (not just resistance) is handled in onLivingHurt() below, since there's no
    // vanilla mob effect that fully zeroes out damage.
    public static final RegistryObject<MobEffect> COSMIC_INVINCIBILITY = MOB_EFFECTS.register("cosmic_invincibility",
        () -> new CosmicInvincibilityEffect(MobEffectCategory.BENEFICIAL, 0xB565F5)
    );

    // Universal God - the permanent mark of a player who ate a Universal Core (see UniversalGodEffect).
    public static final RegistryObject<MobEffect> UNIVERSAL_GOD = MOB_EFFECTS.register("universal_god",
        () -> new UniversalGodEffect(MobEffectCategory.BENEFICIAL, 0xFFD24A)
    );

    // Creates the Void Ore block - glows at light level 7, drops 3-7 XP, requires correct tool
    public static final RegistryObject<Block> VOID_ORE = BLOCKS.register("void_ore",
        () -> new DropExperienceBlock(
            net.minecraft.util.valueproviders.UniformInt.of(3, 7),
            BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_ore")))
                .strength(3.0f, 3.0f)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)
                .sound(net.minecraft.world.level.block.SoundType.STONE)
        )
    );

    // Creates the Void Ore block item (so players can hold and place it)
    public static final RegistryObject<Item> VOID_ORE_ITEM = ITEMS.register("void_ore",
        () -> new BlockItem(VOID_ORE.get(), new Item.Properties().setId(ITEMS.key("void_ore")))
    );

    // Creates the Void Block - a compressed block of void energy, crafted from 9 Void Ingots
    public static final RegistryObject<Block> VOID_BLOCK = BLOCKS.register("void_block",
        () -> new Block(
            BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_block")))
                .strength(50.0f, 1200.0f)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 3)
                .sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK)
        )
    );

    public static final RegistryObject<Item> VOID_BLOCK_ITEM = ITEMS.register("void_block",
        () -> new BlockItem(VOID_BLOCK.get(), new Item.Properties().setId(ITEMS.key("void_block")))
    );

    // Creates the Void Ingot - a rare crafting material from Void Ore
    public static final RegistryObject<Item> VOID_INGOT = ITEMS.register("void_ingot",
        () -> new Item(new Item.Properties().setId(ITEMS.key("void_ingot")))
    );

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

    // Creates the Universal Shard - top-tier legendary material, dropped only by the future
    // Universe Destroyer boss. Not minable/craftable; this is just the item existing so later
    // recipes (Cosmic Apple, sword, armor) can reference it. Glows via the foil/glint shimmer.
    public static final RegistryObject<Item> UNIVERSAL_SHARD = ITEMS.register("universal_shard",
        () -> new UniversalShardItem(new Item.Properties()
            .setId(ITEMS.key("universal_shard"))
            .rarity(Rarity.EPIC)
        )
    );

    // Creates the Cosmic Apple - the first item crafted from Universal Shards. An
    // emergency-use "ultimate" food: temporary speed/strength/regen/fire resistance/
    // true invincibility/glowing, plus a permanent, uncapped, stacking +30 max health
    // bonus. Modeled on the vanilla Enchanted Golden Apple's rarity/preciousness, but
    // capped to a smaller stack size (16, not 64) so it still reads as a rare
    // emergency item rather than a stackable snack.
    public static final RegistryObject<Item> COSMIC_APPLE = ITEMS.register("cosmic_apple",
        () -> new CosmicAppleItem(new Item.Properties()
            .setId(ITEMS.key("cosmic_apple"))
            .rarity(Rarity.EPIC)
            .stacksTo(16)
            .food(new FoodProperties.Builder()
                .nutrition(4)
                .saturationModifier(1.2F)
                .alwaysEdible()
                .build()
            )
            .component(DataComponents.CONSUMABLE, Consumable.builder()
                .consumeSeconds(1.6F)
                .animation(ItemUseAnimation.EAT)
                .build()
            )
        )
    );

    // Creates the Universal Core - eat it to become a Universal God, forever (see
    // UniversalCoreItem and docs/requirements/universal_core.md). A 3D End Crystal-style GeckoLib
    // model. Only one per stack - nobody needs more than one.
    public static final RegistryObject<Item> UNIVERSAL_CORE = ITEMS.register("universal_core",
        () -> new UniversalCoreItem(new Item.Properties()
            .setId(ITEMS.key("universal_core"))
            .rarity(Rarity.EPIC)
            .stacksTo(1)
            .food(new FoodProperties.Builder()
                .nutrition(20)
                .saturationModifier(1.0F)
                .alwaysEdible()
                .build()
            )
            .component(DataComponents.CONSUMABLE, Consumable.builder()
                .consumeSeconds(3.0F) // long enough to watch the rings wind up
                .animation(ItemUseAnimation.EAT)
                .build()
            )
        )
    );

    // Creates the Void Helmet - Night Vision + Void Sight + Wither Aura
    public static final RegistryObject<Item> VOID_HELMET = ITEMS.register("void_helmet",
        () -> new VoidHelmetItem(
            new Item.Properties()
                .setId(ITEMS.key("void_helmet"))
                .durability(2000)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_helmet_armor"),
                            5.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_helmet_toughness"),
                            3.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .build()
                )
        )
    );

    // Creates the Void Leggings - Void Step + Shadow Trail + Gravity Pull + Phase + Shadow Strike + Void Buffs
    public static final RegistryObject<Item> VOID_LEGGINGS = ITEMS.register("void_leggings",
        () -> new VoidLeggingsItem(
            new Item.Properties()
                .setId(ITEMS.key("void_leggings"))
                .durability(2100)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_leggings_armor"),
                            8.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_leggings_toughness"),
                            3.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .build()
                )
        )
    );

    // Creates the Void Chestplate - Soul Drain + Void Shield + Wither Burst + Dark Shroud + Armor Shatter + Void Healing
    public static final RegistryObject<Item> VOID_CHESTPLATE = ITEMS.register("void_chestplate",
        () -> new VoidChestplateItem(
            new Item.Properties()
                .setId(ITEMS.key("void_chestplate"))
                .durability(2200)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_chestplate_armor"),
                            9.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("void_chestplate_toughness"),
                            3.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .build()
                )
        )
    );

    // Creates the Void Boots - Feather Fall + Dark Landing + Void Creep + Soul Drain Step + Void Sprint + Void Buffs
    public static final RegistryObject<Item> VOID_BOOTS = ITEMS.register("void_boots",
        () -> new VoidBootsItem(
            new Item.Properties()
                .setId(ITEMS.key("void_boots"))
                .durability(1900)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("void_boots_armor"), 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
                    .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("void_boots_toughness"), 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
                    .build()
                )
        )
    );

    // Creates the Void Sword - corrupted blade with wither, soul drain, void pull, darkness, and heart curse
    public static final RegistryObject<Item> VOID_SWORD = ITEMS.register("void_sword",
        () -> new VoidSwordItem(new Item.Properties()
            .setId(ITEMS.key("void_sword"))
            .durability(2500)
            .attributes(ItemAttributeModifiers.builder()
                .add(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("void_attack_damage"),
                        8.0, // +8 damage (4 base + 8 = 12 total)
                        AttributeModifier.Operation.ADD_VALUE
                    ),
                    EquipmentSlotGroup.MAINHAND
                )
                .build()
            )
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

    // Creates the Universal Sword - the mod's ultimate weapon, crafted from Universal
    // Shards. 999 attack damage (4 base + 995 bonus, matching the Void/Infinitite
    // swords' "base + bonus" convention) and truly unbreakable via the vanilla
    // minecraft:unbreakable data component (Unit.INSTANCE is the marker value for that
    // component type - see DataComponents.UNBREAKABLE) rather than a durability number,
    // so no .durability(...) call is needed at all: ItemStack.isDamageableItem() and
    // hurtAndBreak() both already special-case this component to skip damage entirely.
    public static final RegistryObject<Item> UNIVERSAL_SWORD = ITEMS.register("universal_sword",
        () -> new UniversalSwordItem(new Item.Properties()
            .setId(ITEMS.key("universal_sword"))
            .rarity(Rarity.EPIC)
            .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
            .attributes(ItemAttributeModifiers.builder()
                .add(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                        ResourceLocation.withDefaultNamespace("universal_attack_damage"),
                        995.0, // +995 damage (4 base + 995 = 999 total)
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

    // Register the Infinitite Monstrosity mob entity type
    // Size 1.5 x 2.9 (pre-scale; the SCALE attribute multiplies it to ~4.5 x 8.7 in-world)
    public static final RegistryObject<EntityType<InfinititeMonstrosity>> INFINITITE_MONSTROSITY = ENTITY_TYPES.register("infinitite_monstrosity",
        () -> EntityType.Builder.<InfinititeMonstrosity>of(InfinititeMonstrosity::new, MobCategory.MONSTER)
            .sized(1.5F, 2.9F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build(ENTITY_TYPES.key("infinitite_monstrosity"))
    );

    // Spawn egg for the Infinitite Monstrosity
    public static final RegistryObject<Item> INFINITITE_MONSTROSITY_SPAWN_EGG = ITEMS.register("infinitite_monstrosity_spawn_egg",
        () -> new SpawnEggItem(
            INFINITITE_MONSTROSITY.get(),
            new Item.Properties().setId(ITEMS.key("infinitite_monstrosity_spawn_egg"))
        )
    );

    // Creates the Infinitite Chest Plate with permanent buffs, damage reflection, and +5 hearts
    public static final RegistryObject<Item> INFINITITE_CHESTPLATE = ITEMS.register("infinitite_chestplate",
        () -> new InfinititeArmorItem(
            new Item.Properties()
                .setId(ITEMS.key("infinitite_chestplate"))
                .durability(InfinititeArmorMaterial.DURABILITY)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "infinitite_armor")))
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
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "infinitite_armor")))
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

    // Creates the Infinitite Helmet - Mind Shield + Star Vision + Cosmic Echo + Ore Vision
    public static final RegistryObject<Item> INFINITITE_HELMET = ITEMS.register("infinitite_helmet",
        () -> new InfinititeHelmetItem(
            new Item.Properties()
                .setId(ITEMS.key("infinitite_helmet"))
                .durability(InfinititeArmorMaterial.DURABILITY)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "infinitite_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_helmet_armor"),
                            6.0, // +6 armor (netherite helmet = 3)
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_helmet_toughness"),
                            4.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .build()
                )
        )
    );

    // Creates the Infinitite Boots - Void Step + Cosmic Dash + Gravity Defiance + Star Stomp + Phase Cannon
    public static final RegistryObject<Item> INFINITITE_BOOTS = ITEMS.register("infinitite_boots",
        () -> new InfinititeBootsItem(
            new Item.Properties()
                .setId(ITEMS.key("infinitite_boots"))
                .durability(InfinititeArmorMaterial.DURABILITY)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "infinitite_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_boots_armor"),
                            5.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_boots_toughness"),
                            4.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("infinitite_boots_knockback"),
                            1.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .build()
                )
        )
    );

    // Creates the Universal Chestplate - the first piece of the Universal armor set, crafted
    // from Universal Shards. Matches the Universal Sword's "absurdly overpowered on purpose"
    // scale (see docs/requirements/universal_chestplate.md): 999 armor (mirroring the sword's
    // 999 attack damage), 6 toughness, +40 max health while worn, 40% knockback resistance,
    // and truly unbreakable via the same minecraft:unbreakable data component used by
    // UNIVERSAL_SWORD (Unit.INSTANCE - see the comment on UNIVERSAL_SWORD above) rather than
    // a .durability() call.
    public static final RegistryObject<Item> UNIVERSAL_CHESTPLATE = ITEMS.register("universal_chestplate",
        () -> new UniversalChestplateItem(
            new Item.Properties()
                .setId(ITEMS.key("universal_chestplate"))
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "universal_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_chestplate_armor"),
                            999.0, // the set's signature over-the-top number, matching the sword's 999 damage
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_chestplate_toughness"),
                            6.0, // Void chestplate: 3, Infinitite chestplate: 5 - clearly on top
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_chestplate_health_boost"),
                            40.0, // +40 health (20 hearts) while worn - Infinitite chestplate: +10
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_chestplate_knockback_resistance"),
                            0.40, // 40% knockback resistance - Infinitite chestplate: 20%
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.CHEST
                    )
                    .build()
                )
        )
    );

    // Creates the Universal Leggings - the second piece of the Universal armor set (see
    // docs/requirements/universal_leggings.md). Same 999 armor / 6 toughness / 40% knockback
    // resistance / unbreakable stats as UNIVERSAL_CHESTPLATE, another +40 max health (20 hearts)
    // that stacks on top of the chestplate's, and Star Speed's +50% movement speed. The
    // abilities live in UniversalLeggingsItem.
    public static final RegistryObject<Item> UNIVERSAL_LEGGINGS = ITEMS.register("universal_leggings",
        () -> new UniversalLeggingsItem(
            new Item.Properties()
                .setId(ITEMS.key("universal_leggings"))
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "universal_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_leggings_armor"),
                            999.0, // the set's signature over-the-top number
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_leggings_toughness"),
                            6.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_leggings_health_boost"),
                            40.0, // +40 health (20 hearts) while worn, on top of the chestplate's 20
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_leggings_knockback_resistance"),
                            0.40,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .add(
                        Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_leggings_star_speed"),
                            0.50, // Star Speed: +50% running speed, on top of the Speed effect
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                        ),
                        EquipmentSlotGroup.LEGS
                    )
                    .build()
                )
        )
    );

    // Creates the Universal Boots - the third piece of the Universal armor set (see
    // docs/requirements/universal_boots.md). Same 999 armor / 6 toughness / 40% knockback
    // resistance / +40 max health / unbreakable stats as the chestplate and leggings, each
    // piece's extra hearts stacking. The abilities live in UniversalBootsItem.
    public static final RegistryObject<Item> UNIVERSAL_BOOTS = ITEMS.register("universal_boots",
        () -> new UniversalBootsItem(
            new Item.Properties()
                .setId(ITEMS.key("universal_boots"))
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "universal_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_boots_armor"),
                            999.0, // the set's signature over-the-top number
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_boots_toughness"),
                            6.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .add(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_boots_health_boost"),
                            40.0, // +40 health (20 hearts) while worn, stacking with the other pieces
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_boots_knockback_resistance"),
                            0.40,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.FEET
                    )
                    .build()
                )
        )
    );

    // Creates the Universal Helmet - the final and most powerful piece of the Universal armor
    // set (see docs/requirements/universal_helmet.md). Same 999 armor / 6 toughness / 40%
    // knockback resistance / unbreakable stats as the rest of the set, but +100 max health
    // (50 hearts) - the most of any piece - stacking with the others. The abilities live in
    // UniversalHelmetItem.
    public static final RegistryObject<Item> UNIVERSAL_HELMET = ITEMS.register("universal_helmet",
        () -> new UniversalHelmetItem(
            new Item.Properties()
                .setId(ITEMS.key("universal_helmet"))
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(net.minecraft.resources.ResourceKey.create(EquipmentAssets.ROOT_ID,
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "universal_armor")))
                    .build()
                )
                .attributes(ItemAttributeModifiers.builder()
                    .add(
                        Attributes.ARMOR,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_helmet_armor"),
                            999.0, // the set's signature over-the-top number
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .add(
                        Attributes.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_helmet_toughness"),
                            6.0,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .add(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_helmet_health_boost"),
                            100.0, // +100 health (50 hearts) while worn - the most of any piece
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
                    )
                    .add(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                            ResourceLocation.withDefaultNamespace("universal_helmet_knockback_resistance"),
                            0.40,
                            AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.HEAD
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

        // Register the blocks
        BLOCKS.register(modBusGroup);

        // Register the entity types
        ENTITY_TYPES.register(modBusGroup);

        // Register the mob effects (Cosmic Invincibility, etc.)
        MOB_EFFECTS.register(modBusGroup);

        // Register the item to a creative tab
        BuildCreativeModeTabContentsEvent.getBus(modBusGroup).addListener(ToInfinititeAndBeyond::addCreative);

        // Register entity attributes (must happen on the mod event bus)
        EntityAttributeCreationEvent.getBus(modBusGroup).addListener(ToInfinititeAndBeyond::onEntityAttributeCreation);

        // Register game event for Infinitite Chest Plate abilities (ranged immunity + damage reflection)
        // Using Predicate form: returning true cancels the event (blocks the damage)
        LivingHurtEvent.BUS.addListener((java.util.function.Predicate<LivingHurtEvent>) ToInfinititeAndBeyond::onLivingHurt);

        // Register events for Infinitite Leggings abilities
        LivingFallEvent.BUS.addListener((java.util.function.Predicate<LivingFallEvent>) ToInfinititeAndBeyond::onLivingFall);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(ToInfinititeAndBeyond::onPlayerTick);

        // Universal Sword shockwave ultimate: networking (client -> server "charge complete"
        // packet - see UniversalSwordNetworking), a once-per-server-tick hook to advance any
        // in-flight shockwave rings, and a client-only hook to track the held attack key (only
        // the client knows whether that key is currently down).
        UniversalSwordNetworking.register();
        TickEvent.ServerTickEvent.Post.BUS.addListener(ToInfinititeAndBeyond::onServerTick);
        TickEvent.ClientTickEvent.Post.BUS.addListener(UniversalSwordClientHandler::onClientTick);

        // Universal Leggings: Warp Dash (double-tap sneak) and Star Jumps (mid-air jumps) are
        // spotted client-side and sent to the server - see UniversalLeggingsNetworking.
        UniversalLeggingsNetworking.register();
        TickEvent.ClientTickEvent.Post.BUS.addListener(UniversalLeggingsClientHandler::onClientTick);

        // Universal Helmet: Eye Beam / Time Stop key packets (the keys themselves are client-only
        // - see ClientModEvents), Mind Control's target redirect, and two safety nets so Time
        // Stop never leaves a mob frozen for good: unfreeze everything when the server stops, and
        // unfreeze any mob that was saved to disk mid-freeze as it loads back in.
        UniversalHelmetNetworking.register();
        LivingChangeTargetEvent.BUS.addListener((java.util.function.Predicate<LivingChangeTargetEvent>) UniversalHelmetItem::onChangeTarget);
        EntityJoinLevelEvent.BUS.addListener((java.util.function.Consumer<EntityJoinLevelEvent>) UniversalHelmetItem::onEntityJoin);
        ServerStoppingEvent.BUS.addListener(event -> UniversalHelmetItem.endAllTimeStops());

        // Universal Core: god-power key packets, true invincibility (nothing but another god can
        // hurt or kill a god), and noclip while flying - see UniversalCoreItem.
        UniversalCoreNetworking.register();
        LivingAttackEvent.BUS.addListener((java.util.function.Predicate<LivingAttackEvent>) UniversalCoreItem::onLivingAttack);
        LivingDeathEvent.BUS.addListener((java.util.function.Predicate<LivingDeathEvent>) UniversalCoreItem::onLivingDeath);
        LivingEvent.LivingTickEvent.BUS.addListener((java.util.function.Consumer<LivingEvent.LivingTickEvent>) UniversalCoreItem::onLivingTick);

        // Cosmic Apple's permanent max-health bonus: a fresh Player entity (created on
        // respawn, or on login) gets a brand-new AttributeMap that won't remember a
        // modifier that was only ever added to the old instance, so we re-sync it from
        // the persistent stack counter in both cases.
        PlayerEvent.Clone.BUS.addListener(ToInfinititeAndBeyond::onPlayerClone);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(ToInfinititeAndBeyond::onPlayerLoggedIn);
    }

    // Add our custom items to creative tabs
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(SAPPHIRE.get());
            event.accept(INFINITITE_INGOT.get());
            event.accept(VOID_INGOT.get());
            event.accept(UNIVERSAL_SHARD.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(COSMIC_APPLE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(VOID_ORE_ITEM.get());
            event.accept(VOID_BLOCK_ITEM.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(VOID_HELMET.get());
            event.accept(VOID_CHESTPLATE.get());
            event.accept(VOID_LEGGINGS.get());
            event.accept(VOID_BOOTS.get());
            event.accept(VOID_SWORD.get());
            event.accept(INFINITITE_SWORD.get());
            event.accept(UNIVERSAL_SWORD.get());
            event.accept(INFINITITE_BOW.get());
            event.accept(INFINITITE_ARROW_ITEM.get());
            event.accept(INFINITITE_HELMET.get());
            event.accept(INFINITITE_CHESTPLATE.get());
            event.accept(INFINITITE_LEGGINGS.get());
            event.accept(INFINITITE_BOOTS.get());
            event.accept(UNIVERSAL_CHESTPLATE.get());
            event.accept(UNIVERSAL_LEGGINGS.get());
            event.accept(UNIVERSAL_BOOTS.get());
            event.accept(UNIVERSAL_HELMET.get());
            event.accept(UNIVERSAL_CORE.get());
            event.accept(INFINITITE_MONSTROSITY_SPAWN_EGG.get());
        }
    }

    /**
     * Register entity attribute sets - called once during startup on the mod event bus.
     * Every custom mob entity needs its attribute map registered here.
     */
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(INFINITITE_MONSTROSITY.get(), InfinititeMonstrosity.createAttributes().build());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

            // Universal Sword attack combo / charge pose: switches off vanilla's swing and turns
            // the player (and, in first person, the camera) for the spin move (see
            // UniversalSwordAnimations).
            RenderPlayerEvent.Pre.BUS.addListener((java.util.function.Consumer<RenderPlayerEvent.Pre>) UniversalSwordAnimations::onRenderPlayerPre);
            RenderPlayerEvent.Post.BUS.addListener(UniversalSwordAnimations::onRenderPlayerPost);
            ViewportEvent.ComputeCameraAngles.BUS.addListener(UniversalSwordAnimations::onComputeCameraAngles);

            // Universal Helmet: Eye Beam / Time Stop key presses.
            TickEvent.ClientTickEvent.Post.BUS.addListener(UniversalHelmetClientHandler::onClientTick);

            // Universal Core: god-power keys, and no block overlay while flying through walls.
            TickEvent.ClientTickEvent.Post.BUS.addListener(UniversalCoreClientHandler::onClientTick);
            RenderBlockScreenEffectEvent.BUS.addListener((java.util.function.Predicate<RenderBlockScreenEffectEvent>) UniversalCoreClientHandler::onBlockScreenEffect);
        }

        @SubscribeEvent
        public static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
            // Universal Core: a god's hearts are gold and white.
            UniversalGodHearts.onAddLayers(event);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            UniversalHelmetClientHandler.onRegisterKeyMappings(event);
            UniversalCoreClientHandler.onRegisterKeyMappings(event);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(INFINITITE_MONSTROSITY.get(), InfinititeMonstrosityRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                InfinititeMonstrosityRenderer.LAYER_LOCATION,
                InfinititeMonstrosityModel::createBodyLayer
            );
        }
    }

    /**
     * ROCKET LEGS: cancel fall damage and trigger landing shockwave.
     */
    public static boolean onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (VoidBootsItem.isWearing(player)) {
                return VoidBootsItem.handleBootsFall(player, event.getDistance());
            }
            if (InfinititeLeggingsItem.isWearing(player)) {
                return InfinititeLeggingsItem.handleLanding(player, event.getDistance());
            }
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
            // Universal Sword shockwave: enforce any active "floating, no control" lock from
            // being caught in the blast (see UniversalSwordItem.applyFloatLock/tickFloatLock).
            UniversalSwordItem.tickFloatLock(player);
        }
        if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
            InfinititeBootsItem.tick(player, serverLevel);
            // Universal Chestplate: Cosmic Aegis (status immunity + passive regen) + Starlight Aura (particles)
            UniversalChestplateItem.tick(player, serverLevel);
            // Universal Leggings: every good effect, Star Speed trail, Star Jump refills
            UniversalLeggingsItem.tick(player, serverLevel);
            // Universal Boots: Cosmic Flight, Meteor Slam, Supernova Trail, no fall damage
            UniversalBootsItem.tick(player, serverLevel);
            // Universal Helmet: Mind Control
            UniversalHelmetItem.tick(player, serverLevel);
            // Universal Core: keeps a god's status, flight, food and god-form look going
            UniversalCoreItem.tick(player, serverLevel);
        }
        if (player.level().isClientSide() && player.isLocalPlayer()) {
            // Universal Boots: Liquid Walking has to happen on the wearer's own client, since
            // that's where a player's movement is decided.
            UniversalBootsItem.tickLiquidWalking(player);
        }
    }

    /**
     * Advances any in-flight Universal Sword shockwaves by one tick. Fires exactly once per
     * server tick (unlike PlayerTickEvent, which fires once per player per tick), which is what
     * the ring-expansion timing in UniversalSwordItem.ShockwaveState assumes.
     */
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        UniversalSwordItem.tickActiveShockwaves();
        UniversalSwordItem.tickActiveChargers();
        UniversalSwordItem.tickActiveSpins();
        UniversalHelmetItem.tickTimeStops();
        UniversalCoreItem.tickPowers();
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
        Entity directEntity = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();

        // --- Cosmic Apple: complete damage immunity while Cosmic Invincibility is active ---
        // There's no vanilla "true invincibility" effect (Resistance only reduces damage),
        // so this custom effect is just a marker and this is where it actually does something.
        if (COSMIC_INVINCIBILITY.getHolder().map(entity::hasEffect).orElse(false)) {
            return true;
        }

        // --- Universal Chestplate: Cosmic Ward (25% full block, any damage type) + Cosmic
        // Rebuke (always blocks ranged, reflects 50% of melee back) - when WEARER takes damage ---
        if (entity instanceof Player universalPlayer && UniversalChestplateItem.isWearing(universalPlayer)
                && entity.level() instanceof ServerLevel universalLevel) {
            if (UniversalChestplateItem.handleChestplateHurt(event, universalPlayer, universalLevel, attacker)) {
                return true; // Cosmic Ward or Cosmic Rebuke fully blocked the hit
            }
        }

        // --- Universal Leggings: Cosmic Phase (30% chance to dodge any attack from a mob or player) ---
        if (entity instanceof Player phasePlayer && UniversalLeggingsItem.isWearing(phasePlayer)
                && entity.level() instanceof ServerLevel phaseLevel) {
            LivingEntity phaseAttacker = attacker instanceof LivingEntity living ? living : null;
            if (UniversalLeggingsItem.handleLeggingsHurt(phasePlayer, phaseLevel, phaseAttacker)) {
                return true; // phased out and dodged the attack
            }
        }

        // --- Void Leggings: Phase dodge + Shadow Strike (when WEARER takes damage) ---
        if (entity instanceof Player legsPlayer && VoidLeggingsItem.isWearing(legsPlayer)
                && entity.level() instanceof ServerLevel legsLevel) {
            boolean isMelee = !(directEntity instanceof net.minecraft.world.entity.projectile.Projectile);
            if (VoidLeggingsItem.handleLeggingsHurt(legsPlayer, legsLevel, isMelee)) {
                return true; // Phase dodged the hit
            }
        }

        // --- Void Chestplate abilities (when WEARER takes damage) ---
        if (entity instanceof Player voidPlayer && VoidChestplateItem.isWearing(voidPlayer)
                && entity.level() instanceof ServerLevel voidLevel) {
            if (VoidChestplateItem.handleChestplateHurt(event, voidPlayer, voidLevel)) {
                return true; // Dark Shroud cancelled the hit
            }
        }

        // --- Void Chestplate: Armor Shatter (when ATTACKER wears it and hits someone) ---
        if (attacker instanceof Player chestAttacker && VoidChestplateItem.isWearing(chestAttacker)
                && entity instanceof LivingEntity livingTarget
                && entity.level() instanceof ServerLevel shatterLevel) {
            VoidChestplateItem.handleArmorShatter(chestAttacker, livingTarget, shatterLevel);
        }

        // --- Infinitite Chestplate abilities ---
        if (!(entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof InfinititeArmorItem)) {
            // Not wearing Infinitite, skip Infinitite-specific logic but still check other things below
        } else {
            // Block all projectile (ranged) damage completely
            if (directEntity instanceof Projectile) {
                return true; // cancel the event = no damage taken
            }

            // Reflect 25% of melee damage back to attacker (chest plate)
            if (attacker != null) {
                InfinititeArmorItem.handleDamageReflection(entity, attacker, event.getAmount());
            }
        }

        // COSMIC ECHO (helmet): lightning strikes nearby mobs when player is hit
        if (entity instanceof Player player && InfinititeHelmetItem.isWearing(player)
                && entity.level() instanceof ServerLevel serverLevel) {
            InfinititeHelmetItem.handleCosmicEcho(player, event.getAmount(), serverLevel);
        }

        // VOID HELMET HEART DRAIN: every 5 hits player deals, permanently remove 2 hearts
        if (attacker instanceof Player helmetPlayer && VoidHelmetItem.isWearing(helmetPlayer)
                && entity instanceof LivingEntity livingTarget2
                && entity.level() instanceof ServerLevel helmetLevel) {
            VoidHelmetItem.handleHelmetHitCurse(helmetPlayer, livingTarget2, helmetLevel);
        }

        return false;
    }

    /**
     * Cosmic Apple's permanent max-health bonus survives death: PlayerEvent.Clone fires
     * when a player respawns (or changes dimension), handing us the OLD player instance
     * right before it's discarded and the NEW one takes over. Forge's persistent NBT data
     * (player.getPersistentData()) is NOT copied to the new instance automatically, so we
     * copy it manually here - this is the standard Forge idiom for surviving death - and
     * then re-apply the max-health modifier onto the new instance's (fresh) AttributeMap.
     */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        newPlayer.getPersistentData().merge(oldPlayer.getPersistentData());
        CosmicAppleItem.resyncMaxHealthModifier(newPlayer);
    }

    /**
     * Defensive re-sync on every login: whether or not vanilla's normal entity NBT
     * save/load already restored the max-health modifier, recomputing it from the
     * persistent stack counter is idempotent and guarantees it's always correct.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        CosmicAppleItem.resyncMaxHealthModifier(event.getEntity());
    }
}

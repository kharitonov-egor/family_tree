package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.mixin.CatVariantInvoker;
import com.egakh.familytree.mixin.HorseVariantInvoker;
import com.egakh.familytree.mixin.LlamaVariantInvoker;
//? if >=1.21 {
import com.egakh.familytree.mixin.WolfVariantInvoker;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
//? if >=1.21.11 {
import net.minecraft.world.entity.EntitySpawnReason;
//?}
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.wolf.Wolf;
//? if >=1.21 {
import net.minecraft.world.entity.animal.wolf.WolfVariant;
//?}

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PetFaceRenderer {
    // Tunable framing for the in-GUI 3D model. SCALE_FACTOR sizes the model to the
    // avatar box; raising it zooms in toward the head.
    private static final float SCALE_FACTOR = 1.3f;
    // Mounts are much larger than cats and wolves, so they are fitted by their largest dimension.
    private static final float MOUNT_FILL = 0.7f;
    private static final Set<String> MOUNTS = Set.of("minecraft:horse", "minecraft:donkey", "minecraft:mule",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:camel", "minecraft:camel_husk",
            "minecraft:skeleton_horse", "minecraft:zombie_horse");

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();
    private static ClientLevel cachedLevel;

    private PetFaceRenderer() {}

    public static void clearCache() { CACHE.clear(); cachedLevel = null; }

    public static boolean hasFace(AnimalRecord record) {
        return isCat(record) || isWolf(record) || MOUNTS.contains(record.speciesId());
    }

    public static void drawFace(GuiGraphicsExtractor gfx, Font font, AnimalRecord record, int x, int y, int size) {
        LivingEntity entity = entityFor(record);
        if (entity != null) {
            renderEntity(gfx, entity, x, y, size);
        } else {
            drawInitial(gfx, font, record, x, y, size);
        }
    }

    private static void renderEntity(GuiGraphicsExtractor gfx, LivingEntity entity, int x, int y, int size) {
        int x1 = x + size;
        int y1 = y + size;
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;
        boolean mount = MOUNTS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        int scale = mount
                ? Math.max(8, Math.round(size * MOUNT_FILL / Math.max(entity.getBbHeight(), entity.getBbWidth())))
                : Math.max(16, Math.round(size * SCALE_FACTOR));
        gfx.enableScissor(x, y, x1, y1);
        try {
            // Looking toward the left edge turns a mount's head into a three-quarter view.
            float lookX = mount ? x - size : centerX;
            //? if >=1.21 {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    gfx, x, y, x1, y1, scale, 0.0625f, lookX, centerY, entity);
            //?} else {
            /*// 1.20.1 places the model's feet at the given point and takes the look offset from the model.
            int feetY = Math.round(centerY + scale * (entity.getBbHeight() / 2f + 0.0625f));
            InventoryScreen.renderEntityInInventoryFollowsMouse(gfx, Math.round(centerX), feetY, scale, centerX - lookX, 0, entity);
            *///?}
        } finally {
            gfx.disableScissor();
        }
    }

    private static void drawInitial(GuiGraphicsExtractor gfx, Font font, AnimalRecord record, int x, int y, int size) {
        String name = record.name();
        String initial = name == null || name.isBlank()
                ? "?"
                : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        gfx.centeredText(font, Component.literal(initial),
                x + size / 2, y + (size - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    private static LivingEntity entityFor(AnimalRecord record) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (level != cachedLevel) {
            CACHE.clear();
            cachedLevel = level;
        }
        String key = record.speciesId() + "|" + record.variantId();
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        LivingEntity entity = createEntity(level, record);
        // Preview entities never enter the level, so assign a local ID for the renderer.
        if (entity != null) entity.setId(-CACHE.size() - 1);
        CACHE.put(key, entity);
        return entity;
    }

    private static LivingEntity createEntity(ClientLevel level, AnimalRecord record) {
        try {
            if (isCat(record)) {
                Cat cat = EntityType.CAT.create(level, EntitySpawnReason.LOAD);
                if (cat == null) {
                    return null;
                }
                //? if >=1.21 {
                catVariant(level, record.variantId())
                        .ifPresent(holder -> ((CatVariantInvoker) (Object) cat).familytree$setVariant(holder));
                //?} else {
                /*catVariant(level, record.variantId())
                        .ifPresent(holder -> ((CatVariantInvoker) (Object) cat).familytree$setVariant(holder.value()));
                *///?}
                return cat;
            }
            if (isWolf(record)) {
                Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.LOAD);
                if (wolf == null) {
                    return null;
                }
                //? if >=1.21 {
                wolfVariant(level, record.variantId())
                        .ifPresent(holder -> ((WolfVariantInvoker) (Object) wolf).familytree$setVariant(holder));
                //?}
                return wolf;
            }
            if (MOUNTS.contains(record.speciesId())) return createMount(level, record);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static LivingEntity createMount(ClientLevel level, AnimalRecord record) {
        Identifier id = Identifier.tryParse(record.speciesId());
        var type = id == null ? Optional.<EntityType<?>>empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || !(type.get().create(level, EntitySpawnReason.LOAD) instanceof LivingEntity mount)) return null;
        String[] variant = record.variantId() == null ? new String[0] : record.variantId().split("/", 2);
        if (mount instanceof Horse horse && variant.length == 2) {
            for (Variant coat : Variant.values()) {
                if (!coat.getSerializedName().equals(variant[0])) continue;
                for (Markings markings : Markings.values()) {
                    if (markings.name().equalsIgnoreCase(variant[1]))
                        ((HorseVariantInvoker) (Object) horse).familytree$setVariantAndMarkings(coat, markings);
                }
            }
        }
        if (mount instanceof Llama llama && variant.length == 1) {
            for (Llama.Variant coat : Llama.Variant.values()) {
                if (coat.getSerializedName().equals(variant[0]))
                    ((LlamaVariantInvoker) (Object) llama).familytree$setVariant(coat);
            }
        }
        return mount;
    }

    private static Optional<Holder.Reference<CatVariant>> catVariant(ClientLevel level, String variantId) {
        Identifier id = variantId == null ? null : Identifier.tryParse(variantId);
        if (id == null) {
            return Optional.empty();
        }
        Registry<CatVariant> registry = level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
        return registry.get(ResourceKey.create(Registries.CAT_VARIANT, id));
    }

    //? if >=1.21 {
    private static Optional<Holder.Reference<WolfVariant>> wolfVariant(ClientLevel level, String variantId) {
        Identifier id = variantId == null ? null : Identifier.tryParse(variantId);
        if (id == null) {
            return Optional.empty();
        }
        Registry<WolfVariant> registry = level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
        return registry.get(ResourceKey.create(Registries.WOLF_VARIANT, id));
    }
    //?}

    private static boolean isCat(AnimalRecord record) {
        return "minecraft:cat".equals(record.speciesId());
    }

    private static boolean isWolf(AnimalRecord record) {
        return "minecraft:wolf".equals(record.speciesId());
    }
}

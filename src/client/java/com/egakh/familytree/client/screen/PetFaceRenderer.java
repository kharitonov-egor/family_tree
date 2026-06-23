package com.egakh.familytree.client.screen;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.mixin.CatVariantInvoker;
import com.egakh.familytree.mixin.WolfVariantInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PetFaceRenderer {
    // Tunable framing for the in-GUI 3D model. SCALE_FACTOR sizes the model to the
    // avatar box; raising it zooms in toward the head.
    private static final float SCALE_FACTOR = 1.3f;

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();
    private static ClientLevel cachedLevel;

    private PetFaceRenderer() {}

    public static boolean hasFace(AnimalRecord record) {
        return isCat(record) || isWolf(record);
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
        int scale = Math.max(16, Math.round(size * SCALE_FACTOR));
        gfx.enableScissor(x, y, x1, y1);
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    gfx, x, y, x1, y1, scale, 0.0625f, centerX, centerY, entity);
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
                catVariant(level, record.variantId())
                        .ifPresent(holder -> ((CatVariantInvoker) (Object) cat).familytree$setVariant(holder));
                return cat;
            }
            if (isWolf(record)) {
                Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.LOAD);
                if (wolf == null) {
                    return null;
                }
                wolfVariant(level, record.variantId())
                        .ifPresent(holder -> ((WolfVariantInvoker) (Object) wolf).familytree$setVariant(holder));
                return wolf;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Optional<Holder.Reference<CatVariant>> catVariant(ClientLevel level, String variantId) {
        Identifier id = variantId == null ? null : Identifier.tryParse(variantId);
        if (id == null) {
            return Optional.empty();
        }
        Registry<CatVariant> registry = level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
        return registry.get(ResourceKey.create(Registries.CAT_VARIANT, id));
    }

    private static Optional<Holder.Reference<WolfVariant>> wolfVariant(ClientLevel level, String variantId) {
        Identifier id = variantId == null ? null : Identifier.tryParse(variantId);
        if (id == null) {
            return Optional.empty();
        }
        Registry<WolfVariant> registry = level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
        return registry.get(ResourceKey.create(Registries.WOLF_VARIANT, id));
    }

    private static boolean isCat(AnimalRecord record) {
        return "minecraft:cat".equals(record.speciesId());
    }

    private static boolean isWolf(AnimalRecord record) {
        return "minecraft:wolf".equals(record.speciesId());
    }
}

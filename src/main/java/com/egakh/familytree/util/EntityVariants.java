package com.egakh.familytree.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.panda.Panda;
//? if >=1.21.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
//?} else {
/*import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
*///?}
//? if <1.21 {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.FrogVariant;
*///?}
//? if >=1.21.5 && <26.1 {
/*import net.minecraft.world.item.EitherHolder;
*///?}

/**
 * Reads and applies variants without code for each species, so modded animals that use
 * Minecraft's variant system work too. Enum variants use their serialized name and registry
 * variants their full key, matching what earlier versions saved for cats, wolves, and llamas.
 */
public final class EntityVariants {
    //? if >=1.21.5 {
    private static List<DataComponentType<?>> variantComponents;
    //?}

    private EntityVariants() {}

    public static String read(Entity entity) {
        if (entity instanceof Panda panda)
            return panda.getMainGene().getSerializedName() + "/" + panda.getHiddenGene().getSerializedName();
        //? if >=1.21.5 {
        for (DataComponentType<?> type : variantComponents()) {
            Object value = entity.get(type);
            if (value != null) return format(value);
        }
        //?} else {
        /*if (entity instanceof Sheep sheep) return sheep.getColor().getSerializedName();
        // Forge 1.20.1 removes VariantHolder from mooshrooms but keeps the methods.
        if (entity instanceof MushroomCow mooshroom) return format(mooshroom.getVariant());
        if (entity instanceof VariantHolder<?> holder) return format(holder.getVariant());
        *///?}
        return null;
    }

    /** Returns false when the variant is unknown on this version. */
    public static boolean apply(Entity entity, String variant, HolderLookup.Provider access) {
        if (variant == null) return false;
        if (entity instanceof Panda panda) {
            String[] genes = VariantRules.pandaGenes(variant);
            Panda.Gene main = genes == null ? null : gene(genes[0]), hidden = genes == null ? null : gene(genes[1]);
            if (main == null || hidden == null) return false;
            panda.setMainGene(main);
            panda.setHiddenGene(hidden);
            return true;
        }
        //? if >=1.21.5 {
        for (DataComponentType<?> type : variantComponents()) {
            Object current = entity.get(type);
            if (current == null) continue;
            Object parsed = parse(current, variant, access);
            if (parsed == null) return false;
            set(entity, type, parsed);
            return true;
        }
        //?} else {
        /*if (entity instanceof Sheep sheep) {
            DyeColor color = DyeColor.byName(variant, null);
            if (color != null) sheep.setColor(color);
            return color != null;
        }
        if (entity instanceof MushroomCow mooshroom) {
            var current = mooshroom.getVariant();
            Object parsed = parse(current, variant, access);
            if (parsed != null) mooshroom.setVariant(current.getClass().cast(parsed));
            return parsed != null;
        }
        if (entity instanceof VariantHolder<?> holder) {
            Object parsed = parse(holder.getVariant(), variant, access);
            if (parsed != null) setVariant(holder, parsed);
            return parsed != null;
        }
        *///?}
        return false;
    }

    private static String format(Object value) {
        if (value instanceof Holder<?> holder)
            return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
        //? if >=1.21.5 && <26.1 {
        /*if (value instanceof EitherHolder<?> either) return either.key().map(key -> key.identifier().toString()).orElse(null);
        *///?}
        if (value instanceof StringRepresentable named) return named.getSerializedName();
        //? if <1.21 {
        /*if (value instanceof FrogVariant frog) {
            var key = BuiltInRegistries.FROG_VARIANT.getKey(frog);
            return key == null ? null : key.toString();
        }
        *///?}
        if (value instanceof Enum<?> constant) return constant.name().toLowerCase(java.util.Locale.ROOT);
        return null;
    }

    private static Object parse(Object current, String variant, HolderLookup.Provider access) {
        if (current instanceof Holder<?> holder) {
            Identifier id = Identifier.tryParse(variant);
            return id == null ? null : sameRegistry(holder, id, access);
        }
        //? if >=1.21.5 && <26.1 {
        /*// Chicken variants before 26.1 are stored as either a holder or a registry key.
        if (current instanceof EitherHolder<?> either) {
            Identifier id = Identifier.tryParse(variant);
            return id == null ? null : sameRegistry(either, id, access);
        }
        *///?}
        if (current instanceof Enum<?> constant) {
            for (Object candidate : constant.getDeclaringClass().getEnumConstants()) {
                String name = candidate instanceof StringRepresentable named ? named.getSerializedName()
                        : ((Enum<?>) candidate).name().toLowerCase(java.util.Locale.ROOT);
                if (name.equals(variant)) return candidate;
            }
            return null;
        }
        //? if <1.21 {
        /*if (current instanceof FrogVariant) {
            Identifier id = Identifier.tryParse(variant);
            return id == null ? null : BuiltInRegistries.FROG_VARIANT.get(id);
        }
        *///?}
        return null;
    }

    private static <T> Holder<T> sameRegistry(Holder<T> current, Identifier id, HolderLookup.Provider access) {
        ResourceKey<T> key = current.unwrapKey().orElse(null);
        if (key == null) return null;
        ResourceKey<Registry<T>> registryKey = ResourceKey.createRegistryKey(key.registry());
        HolderLookup.RegistryLookup<T> lookup = access.lookupOrThrow(registryKey);
        return lookup.get(ResourceKey.create(registryKey, id)).<Holder<T>>map(found -> found).orElse(null);
    }

    //? if >=1.21.5 && <26.1 {
    /*private static <T> EitherHolder<T> sameRegistry(EitherHolder<T> current, Identifier id, HolderLookup.Provider access) {
        ResourceKey<T> key = current.key().orElse(null);
        if (key == null) return null;
        ResourceKey<Registry<T>> registryKey = ResourceKey.createRegistryKey(key.registry());
        return access.lookupOrThrow(registryKey).get(ResourceKey.create(registryKey, id)).map(EitherHolder::new).orElse(null);
    }
    *///?}

    private static Panda.Gene gene(String name) {
        for (Panda.Gene gene : Panda.Gene.values()) if (gene.getSerializedName().equals(name)) return gene;
        return null;
    }

    //? if >=1.21.5 {
    @SuppressWarnings("unchecked")
    private static <T> void set(Entity entity, DataComponentType<T> type, Object value) {
        entity.setComponent(type, (T) value);
    }

    /** Variant components such as {@code minecraft:axolotl/variant} and {@code minecraft:sheep/color}. */
    private static List<DataComponentType<?>> variantComponents() {
        if (variantComponents == null) variantComponents = BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet().stream()
                .filter(entry -> {
                    String path = entry.getKey().identifier().getPath();
                    return path.endsWith("/variant") || path.endsWith("/color");
                })
                .<DataComponentType<?>>map(java.util.Map.Entry::getValue)
                .toList();
        return variantComponents;
    }
    //?} else {
    /*@SuppressWarnings("unchecked")
    private static <T> void setVariant(VariantHolder<T> holder, Object value) {
        holder.setVariant((T) value);
    }
    *///?}
}

package com.egakh.familytree.util;

import com.egakh.familytree.FamilyTreeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/** Entity type tags that datapacks and KubeJS use to choose species. The settings file wins over them. */
public final class SpeciesTags {
    public static final TagKey<EntityType<?>> TRACKED = tag("tracked");
    public static final TagKey<EntityType<?>> IGNORED = tag("ignored");

    private SpeciesTags() {}

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FamilyTreeMod.MOD_ID, path));
    }

    public static boolean has(EntityType<?> type, TagKey<EntityType<?>> tag) {
        return type.builtInRegistryHolder().is(tag);
    }

    public static List<String> members(TagKey<EntityType<?>> tag) {
        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (has(type, tag)) ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        }
        return ids;
    }
}

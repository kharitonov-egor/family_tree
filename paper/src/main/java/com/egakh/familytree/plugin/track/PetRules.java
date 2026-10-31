package com.egakh.familytree.plugin.track;

import com.egakh.familytree.settings.TrackingRules;
import com.egakh.familytree.util.AnimalCategory;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;

import java.util.function.Supplier;

/** The plugin's version of the mod's {@code PetFilter}: which entities the history records. */
public final class PetRules {
    public static final NamespacedKey TRACKED = new NamespacedKey("familytree", "tracked");
    public static final NamespacedKey IGNORED = new NamespacedKey("familytree", "ignored");
    private final Supplier<TrackingRules> rules;

    public PetRules(Supplier<TrackingRules> rules) {
        this.rules = rules;
    }

    /** Pets enter the history on their own once tamed. Other animals need a player to breed, name, or add them. */
    public boolean isTrackable(Entity entity) {
        boolean tamed = entity instanceof AbstractHorse horse ? horse.isTamed() && Entities.owner(horse) != null
                : entity instanceof Tameable tameable && tameable.isTamed();
        return tamed && records(entity, AnimalCategory.PETS);
    }

    /** Applies the settings, then the {@code familytree:tracked} and {@code familytree:ignored} datapack tags, then the categories. */
    public boolean records(Entity entity, AnimalCategory category) {
        return rules.get().records(Entities.species(entity), category,
                tagged(TRACKED, entity.getType()), tagged(IGNORED, entity.getType()));
    }

    public static boolean isTameableSpecies(Entity entity) {
        return entity instanceof Tameable;
    }

    public boolean recordsNewAnimal(Entity entity) {
        return entity instanceof Animals && !isTameableSpecies(entity) && records(entity, category(entity));
    }

    public static AnimalCategory category(Entity entity) {
        String species = Entities.species(entity);
        AnimalCategory.learn(species, isTameableSpecies(entity));
        return AnimalCategory.of(species);
    }

    /**
     * Camels always report as tamed, and skeleton trap horses are tamed without an owner.
     * These mounts are recorded once a player rides or breeds them.
     */
    public static boolean isUnclaimedMount(Entity entity) {
        return entity instanceof AbstractHorse horse && horse.isTamed() && Entities.owner(horse) == null;
    }

    public static boolean tagged(NamespacedKey key, EntityType type) {
        try {
            Tag<EntityType> tag = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, key, EntityType.class);
            return tag != null && tag.isTagged(type);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}

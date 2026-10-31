package com.egakh.familytree.menu;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.permissions.PetAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The animals one player may view in the chest menu and each one's children, built for one view grant. The plugin
 * shares this class, so it uses no Minecraft classes.
 */
public final class FamilyIndex {
    public static final int MAX_RECORDS = 100_000;
    private static final Comparator<AnimalRecord> BY_NAME = Comparator
            .comparing(AnimalRecord::name, String.CASE_INSENSITIVE_ORDER).thenComparing(AnimalRecord::id);

    private final boolean viewAll;
    private final List<UUID> visible;
    private final Map<UUID, List<UUID>> children;
    private final Map<UUID, Integer> hiddenChildren;
    private final List<String> species;

    private FamilyIndex(boolean viewAll, List<UUID> visible, Map<UUID, List<UUID>> children,
                        Map<UUID, Integer> hiddenChildren, List<String> species) {
        this.viewAll = viewAll;
        this.visible = visible;
        this.children = children;
        this.hiddenChildren = hiddenChildren;
        this.species = species;
    }

    public static FamilyIndex empty() {
        return new FamilyIndex(false, List.of(), Map.of(), Map.of(), List.of());
    }

    public static FamilyIndex build(Collection<AnimalRecord> records, UUID viewer, boolean viewAll) {
        List<AnimalRecord> shown = new ArrayList<>();
        List<AnimalRecord> hidden = new ArrayList<>();
        for (AnimalRecord record : records) (PetAccess.canView(record, viewer, viewAll) ? shown : hidden).add(record);
        shown.sort(BY_NAME);
        if (shown.size() > MAX_RECORDS) shown = shown.subList(0, MAX_RECORDS);
        Set<UUID> ids = new HashSet<>();
        for (AnimalRecord record : shown) ids.add(record.id());
        List<UUID> visible = new ArrayList<>(shown.size());
        Map<UUID, List<UUID>> children = new HashMap<>();
        Set<String> kinds = new LinkedHashSet<>();
        for (AnimalRecord record : shown) {
            visible.add(record.id());
            kinds.add(record.speciesId());
            for (UUID parent : parents(record)) {
                if (ids.contains(parent)) children.computeIfAbsent(parent, key -> new ArrayList<>()).add(record.id());
            }
        }
        Map<UUID, Integer> hiddenChildren = new HashMap<>();
        for (AnimalRecord record : hidden) {
            for (UUID parent : parents(record)) {
                if (ids.contains(parent)) hiddenChildren.merge(parent, 1, Integer::sum);
            }
        }
        List<String> species = kinds.stream().sorted(Comparator.comparing(FamilyIndex::speciesSortKey)).toList();
        return new FamilyIndex(viewAll, visible, children, hiddenChildren, species);
    }

    private static Set<UUID> parents(AnimalRecord record) {
        Set<UUID> parents = new LinkedHashSet<>();
        if (record.parentA() != null) parents.add(record.parentA());
        if (record.parentB() != null) parents.add(record.parentB());
        return parents;
    }

    private static String speciesSortKey(String speciesId) {
        return switch (speciesId) {
            case "minecraft:wolf" -> "0";
            case "minecraft:cat" -> "1";
            case "minecraft:parrot" -> "2";
            default -> "3" + speciesId;
        };
    }

    /** The grant this index was built for. A session rebuilds the index when the grant changes. */
    public boolean viewAll() {
        return viewAll;
    }

    public List<UUID> visible() {
        return visible;
    }

    public List<UUID> children(UUID parent) {
        return children.getOrDefault(parent, List.of());
    }

    /** Recorded children of a visible animal that the player may not view. */
    public int hiddenChildren(UUID parent) {
        return hiddenChildren.getOrDefault(parent, 0);
    }

    public List<String> species() {
        return species;
    }
}

package com.egakh.familytree.plugin.track;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FarmRecords;
import com.egakh.familytree.data.HorseStats;
import com.egakh.familytree.naming.NameGenerator;
import com.egakh.familytree.plugin.FamilyTreePlugin;
import com.egakh.familytree.plugin.WorldClock;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import com.egakh.familytree.settings.TrackingRules;
import com.egakh.familytree.util.AnimalCategory;
import com.egakh.familytree.util.VariantRules;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** The plugin's version of the mod's {@code PetLifecycleListeners}. Bukkit events call it on the main thread. */
public final class Tracker {
    private static final long CLEANUP_RETRY_NANOS = 30_000_000_000L;
    private final FamilyTreePlugin plugin;
    private final PetRules rules;

    public Tracker(FamilyTreePlugin plugin) {
        this.plugin = plugin;
        this.rules = new PetRules(() -> plugin.settings().tracking());
    }

    public PetRules rules() {
        return rules;
    }

    /** Paper's listener for frogs and sniffers, which lay spawn or eggs. Spigot has no event for them. */
    public org.bukkit.event.Listener paperEvents() {
        try {
            Class.forName("io.papermc.paper.event.entity.EntityFertilizeEggEvent");
            return (org.bukkit.event.Listener) Class.forName("com.egakh.familytree.plugin.track.PaperEvents")
                    .getDeclaredConstructor(Tracker.class).newInstance(this);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private FamilyTreeStore store() {
        return plugin.store();
    }

    public void onLoad(Entity entity) {
        if (!(entity instanceof Animals)) return;
        FamilyTreeStore state = store();
        if (!plugin.settings().discoverPetsOnLoad() && !state.contains(entity.getUniqueId())) return;
        ensureRecord(state, entity);
    }

    public void onUnload(Entity entity) {
        if (!(entity instanceof Animals)) return;
        FamilyTreeStore state = store();
        if (state.contains(entity.getUniqueId())) stampLastSeen(state, entity);
    }

    /** {@code breeder} is the player who fed either parent, or null. */
    public void onBred(Animals parentA, Animals parentB, Animals child, Player breeder) {
        if (!PetRules.isTameableSpecies(child)) {
            recordBirth(parentA, parentB, child,
                    breeder == null ? null : breeder.getUniqueId(), breeder == null ? null : breeder.getName());
            return;
        }
        FamilyTreeStore state = store();
        if (!rules.records(child, AnimalCategory.PETS)) return;

        rememberParent(state, parentA, breeder);
        rememberParent(state, parentB, breeder);

        UUID childId = child.getUniqueId();
        String existingName = Entities.customName(child);
        boolean autoNamed = existingName == null;
        String name = autoNamed ? NameGenerator.generate(childId, state.all()) : existingName;

        UUID owner = Entities.owner(child);
        String ownerName = Entities.ownerName(owner);
        if (owner == null) {
            UUID parentOwner = Entities.owner(parentA);
            String parentOwnerName = parentOwner == null ? null : Entities.ownerName(parentOwner);
            if (parentOwner == null) {
                parentOwner = Entities.owner(parentB);
                parentOwnerName = parentOwner == null ? null : Entities.ownerName(parentOwner);
            }
            if (parentOwner == null) {
                AnimalRecord recordA = state.get(parentA.getUniqueId());
                if (recordA != null && recordA.ownerId() != null) {
                    parentOwner = recordA.ownerId();
                    parentOwnerName = recordA.ownerName();
                }
            }
            if (parentOwner == null) {
                AnimalRecord recordB = state.get(parentB.getUniqueId());
                if (recordB != null && recordB.ownerId() != null) {
                    parentOwner = recordB.ownerId();
                    parentOwnerName = recordB.ownerName();
                }
            }
            if (parentOwner == null && breeder != null) {
                parentOwner = breeder.getUniqueId();
                parentOwnerName = breeder.getName();
            }
            owner = parentOwner;
            ownerName = parentOwnerName;
        }

        String origin = variantOrigin(child, parentA, parentB);
        if (state.contains(childId)) {
            UUID inheritedOwner = owner;
            String inheritedOwnerName = ownerName;
            state.update(childId, record -> {
                record.setParents(parentA.getUniqueId(), parentB.getUniqueId());
                record.setVariantOrigin(origin);
                if (inheritedOwner != null) record.setOwner(inheritedOwner, inheritedOwnerName);
            });
            stampLastSeen(state, child);
            return;
        }

        AnimalRecord record = new AnimalRecord(childId, Entities.species(child), name, autoNamed,
                parentA.getUniqueId(), parentB.getUniqueId(), WorldClock.currentWorldDay(), WorldClock.currentEpochMillis(),
                false, null, null, owner, ownerName, Variants.read(child), null, null);
        record.setVariantOrigin(origin);
        state.put(record);
        stampLastSeen(state, child);
    }

    /** Judged from the live parents at birth, because dye and lightning can change their variants later. */
    private static String variantOrigin(Entity child, Entity parentA, Entity parentB) {
        VariantRules.Outcome outcome = VariantRules.outcome(Entities.species(child), Variants.read(child),
                Variants.read(parentA), Variants.read(parentB));
        return outcome == VariantRules.Outcome.UNKNOWN ? null : outcome.key();
    }

    private void rememberParent(FamilyTreeStore state, Animals parent, Player breeder) {
        if (breeder != null && PetRules.isUnclaimedMount(parent)) claim(state, parent, breeder);
        else ensureRecord(state, parent);
    }

    /**
     * Records a birth of animals other than pets. A player's breeding brings the baby and both parents into the
     * history, owned by the breeder. Without a player, only families already in the history continue.
     */
    public void recordBirth(Animals parentA, Animals parentB, Animals child, UUID breeder, String breederName) {
        FamilyTreeStore state = store();
        if (state.readOnly() || !rules.recordsNewAnimal(child)) return;
        UUID childId = child.getUniqueId();
        String origin = variantOrigin(child, parentA, parentB);
        if (state.contains(childId)) {
            state.update(childId, record -> {
                record.setParents(parentA.getUniqueId(), parentB.getUniqueId());
                record.setVariantOrigin(origin);
            });
            return;
        }
        boolean knownA = state.contains(parentA.getUniqueId()), knownB = state.contains(parentB.getUniqueId());
        if (breeder == null && !knownA && !knownB) return;
        if (!roomForFarmRecords(state, 1 + (knownA ? 0 : 1) + (knownB ? 0 : 1))) {
            noticeFarmLimit(state, breeder, "familytree.farm.limit");
            return;
        }
        AnimalRecord a = recordParent(state, parentA, parentB, breeder, breederName);
        AnimalRecord b = recordParent(state, parentB, parentA, breeder, breederName);
        AnimalRecord owner = a.ownerId() != null ? a : b.ownerId() != null ? b : null;
        AnimalRecord record = newRecord(state, child);
        record.setParents(parentA.getUniqueId(), parentB.getUniqueId());
        record.setVariantOrigin(origin);
        if (owner != null) record.setOwner(owner.ownerId(), owner.ownerName());
        else record.setOwner(breeder, breederName);
        state.put(record);
        stampLastSeen(state, child);
    }

    /** Frogs and sniffers lay spawn or an egg instead of a baby, so a player's breeding records only the parents. */
    public void onBredWithoutBaby(Animals parentA, Animals parentB, Player breeder) {
        if (breeder == null || PetRules.isTameableSpecies(parentA) || !rules.recordsNewAnimal(parentA)) return;
        FamilyTreeStore state = store();
        if (state.readOnly()) return;
        int needed = (state.contains(parentA.getUniqueId()) ? 0 : 1) + (state.contains(parentB.getUniqueId()) ? 0 : 1);
        if (needed > 0 && !roomForFarmRecords(state, needed)) {
            noticeFarmLimit(state, breeder.getUniqueId(), "familytree.farm.limit_parents");
            return;
        }
        recordParent(state, parentA, parentB, breeder.getUniqueId(), breeder.getName());
        recordParent(state, parentB, parentA, breeder.getUniqueId(), breeder.getName());
    }

    private AnimalRecord recordParent(FamilyTreeStore state, Entity parent, Entity mate, UUID breeder, String breederName) {
        UUID id = parent.getUniqueId();
        if (!state.contains(id)) {
            AnimalRecord record = newRecord(state, parent);
            AnimalRecord mateRecord = state.get(mate.getUniqueId());
            if (breeder != null) record.setOwner(breeder, breederName);
            else if (mateRecord != null) record.setOwner(mateRecord.ownerId(), mateRecord.ownerName());
            state.put(record);
        } else if (breeder != null && state.get(id).ownerId() == null) {
            state.update(id, record -> record.setOwner(breeder, breederName));
        }
        ensureRecord(state, parent);
        return state.get(id);
    }

    private boolean roomForFarmRecords(FamilyTreeStore state, int needed) {
        TrackingRules tracking = plugin.settings().tracking();
        int limit = tracking.farmRecordLimit();
        if (state.unnamedFarmRecords() + needed <= limit) return true;
        return tracking.autoPruneFarm() && autoPrune(state, limit) > 0 && state.unnamedFarmRecords() + needed <= limit;
    }

    /** The mod's {@code FarmCleanup}: back up first, then remove the oldest deceased unnamed animals other than pets. */
    int autoPrune(FamilyTreeStore state, int limit) {
        long now = System.nanoTime();
        if (state.readOnly() || !state.cleanupDue(now)) return 0;
        int excess = state.recountUnnamedFarmRecords() - FarmRecords.cleanupTarget(limit);
        if (excess <= 0) return 0;
        List<UUID> ids = FarmRecords.removable(state.all()).stream().limit(excess).map(AnimalRecord::id).toList();
        if (ids.isEmpty()) {
            state.delayCleanup(now + CLEANUP_RETRY_NANOS);
            return 0;
        }
        Path backup;
        try {
            backup = state.backup(plugin.files().backups(), "before-farm-cleanup-", false);
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Farm record cleanup canceled because the backup failed", failure);
            return 0;
        }
        int removed = state.removeAll(ids);
        plugin.getLogger().info("Family Tree removed " + removed + " deceased unnamed animals other than pets. Backup: " + backup);
        return removed;
    }

    private void noticeFarmLimit(FamilyTreeStore state, UUID player, String message) {
        int limit = plugin.settings().tracking().farmRecordLimit();
        if (state.firstFarmLimitNotice(new UUID(0, 0)))
            plugin.getLogger().warning("Family Tree reached farmRecordLimit=" + limit + " and stopped recording new unnamed animals other than pets.");
        if (!plugin.settings().farmLimitMessage()) return;
        Player online = player == null ? null : Bukkit.getPlayer(player);
        if (online != null && state.firstFarmLimitNotice(player)) plugin.chat().send(online, message, limit);
    }

    public void onNameTagged(Entity target, Player player) {
        if (Entities.customName(target) == null || PetRules.isTameableSpecies(target)) return;
        addAnimal(store(), target, player.getUniqueId(), player.getName());
    }

    public enum AddResult { ADDED, NOT_RECORDED, FULL }

    /** Unnamed additions count toward the farm limit; named ones never do. */
    public AddResult addAnimal(FamilyTreeStore state, Entity entity, UUID owner, String ownerName) {
        UUID id = entity.getUniqueId();
        if (state.readOnly()) return AddResult.NOT_RECORDED;
        if (!state.contains(id)) {
            if (!rules.recordsNewAnimal(entity)) return AddResult.NOT_RECORDED;
            if (Entities.customName(entity) == null && !roomForFarmRecords(state, 1)) return AddResult.FULL;
            state.put(newRecord(state, entity));
        }
        state.update(id, record -> {
            if (record.ownerId() == null) record.setOwner(owner, ownerName);
        });
        ensureRecord(state, entity);
        return AddResult.ADDED;
    }

    public void onCustomNameChanged(Entity entity) {
        if (!(entity instanceof Animals)) return;
        FamilyTreeStore state = store();
        UUID id = entity.getUniqueId();
        String displayName = Entities.customName(entity);
        if (displayName == null) return;
        if (state.contains(id)) {
            state.update(id, record -> record.rename(displayName, false));
            return;
        }
        if (rules.isTrackable(entity)) {
            ensureRecord(state, entity);
            state.update(id, record -> record.rename(displayName, false));
        }
    }

    /** Wolves, cats, parrots, and horse-type mounts. The tamer becomes the owner of a pet new to the history. */
    public void onTamed(Entity entity, UUID player, String playerName) {
        FamilyTreeStore state = store();
        boolean wasTracked = state.contains(entity.getUniqueId());
        ensureRecord(state, entity);
        if (!wasTracked) state.update(entity.getUniqueId(), record -> record.setOwner(player, playerName));
    }

    /** The first rider of an unclaimed mount becomes its owner in Family Tree. Vanilla ownership is unchanged. */
    public void onMountRidden(Entity mount, Player rider) {
        if (!PetRules.isUnclaimedMount(mount)) return;
        claim(store(), mount, rider);
    }

    private void claim(FamilyTreeStore state, Entity entity, Player player) {
        UUID id = entity.getUniqueId();
        if (!state.contains(id)) state.put(newRecord(state, entity));
        state.update(id, record -> {
            if (record.ownerId() == null) record.setOwner(player.getUniqueId(), player.getName());
        });
        ensureRecord(state, entity);
    }

    public void onDeath(Entity entity, AnimalRecord.DeathCause cause) {
        if (!(entity instanceof Animals)) return;
        FamilyTreeStore state = store();
        UUID id = entity.getUniqueId();
        if (!state.contains(id)) return;
        state.update(id, record -> record.markDeceased(WorldClock.currentWorldDay(), WorldClock.currentEpochMillis(), cause));
        stampLastSeen(state, entity);
    }

    /** Animals already in the history stay current even when Minecraft no longer reports them as tamed, such as foals. */
    private boolean tracked(FamilyTreeStore state, Entity entity) {
        return rules.isTrackable(entity) || entity instanceof Animals && state.contains(entity.getUniqueId());
    }

    public void ensureRecord(FamilyTreeStore state, Entity entity) {
        if (!tracked(state, entity)) return;
        UUID id = entity.getUniqueId();
        PetRules.category(entity);
        if (state.contains(id)) {
            UUID owner = Entities.owner(entity);
            if (owner != null) {
                String ownerName = Entities.ownerName(owner);
                state.update(id, record -> record.setOwner(owner, ownerName != null ? ownerName : record.ownerName()));
            }
            String variantId = Variants.read(entity);
            if (variantId != null) state.update(id, record -> record.setVariantId(variantId));
            AnimalRecord record = state.get(id);
            if (record != null && record.nameAutogenerated() && NameGenerator.isLegacyOverflowName(record.originalName())) {
                state.update(id, rec -> rec.rename(NameGenerator.generate(id, state.all()), true));
                record = state.get(id);
            }
            String currentName = Entities.customName(entity);
            if (currentName != null && record != null && (record.nameAutogenerated() || !record.originalName().equals(currentName)))
                state.update(id, rec -> rec.rename(currentName, false));
            stampLastSeen(state, entity);
            return;
        }
        state.put(newRecord(state, entity));
        stampLastSeen(state, entity);
    }

    private AnimalRecord newRecord(FamilyTreeStore state, Entity entity) {
        UUID id = entity.getUniqueId();
        String species = Entities.species(entity);
        String existingName = Entities.customName(entity);
        boolean autoNamed = existingName == null;
        String name = !autoNamed ? existingName : PetRules.isTameableSpecies(entity)
                ? NameGenerator.generate(id, state.all()) : NameGenerator.farmName(species, id);
        UUID owner = Entities.owner(entity);
        return new AnimalRecord(id, species, name, autoNamed, null, null,
                WorldClock.currentWorldDay(), WorldClock.currentEpochMillis(), false, null, null,
                owner, Entities.ownerName(owner), Variants.read(entity), null, null);
    }

    /** Records where the animal was seen and, for horse-type mounts, its current base stats. */
    public void stampLastSeen(FamilyTreeStore state, Entity entity) {
        HorseStats stats = Entities.stats(entity);
        var location = entity.getLocation();
        String dimension = WorldClock.dimension(entity.getWorld());
        long day = WorldClock.currentWorldDay();
        state.update(entity.getUniqueId(), record -> {
            record.updateLastSeen(location.getX(), location.getY(), location.getZ(), dimension, day);
            if (stats != null) record.setHorseStats(stats);
        });
    }

    public record ScanResult(int imported, int refreshed) {
        public int totalTouched() {
            return imported + refreshed;
        }
    }

    public ScanResult scanLoadedPets(Predicate<AnimalRecord> visible) {
        FamilyTreeStore state = store();
        int imported = 0;
        int refreshed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!tracked(state, entity)) continue;
                UUID id = entity.getUniqueId();
                boolean existed = state.contains(id);
                ensureRecord(state, entity);
                if (state.contains(id) && visible.test(state.get(id))) {
                    if (existed) refreshed++;
                    else imported++;
                }
            }
        }
        return new ScanResult(imported, refreshed);
    }
}

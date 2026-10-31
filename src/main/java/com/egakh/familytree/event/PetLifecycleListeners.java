package com.egakh.familytree.event;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.FarmCleanup;
import com.egakh.familytree.data.HorseStats;
//? if >=1.21 {
import com.egakh.familytree.mixin.WolfVariantInvoker;
//?}
import com.egakh.familytree.naming.NameGenerator;
import com.egakh.familytree.settings.FamilyTreeServerSettings;
import com.egakh.familytree.settings.TrackingRules;
import com.egakh.familytree.text.ServerText;
import com.egakh.familytree.util.AnimalCategory;
import com.egakh.familytree.util.EntityVariants;
import com.egakh.familytree.util.PetFilter;
import com.egakh.familytree.util.VariantRules;
import com.egakh.familytree.util.TimeUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PetLifecycleListeners {

    private PetLifecycleListeners() {}

    /** A player who fed an animal, kept so an animal fed by someone who has since logged off still goes to them. */
    private record Feeder(UUID id, String name) {}

    private static final Map<Animal, Feeder> FEEDERS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void onFedForLove(Animal animal, Player player) {
        if (player instanceof ServerPlayer fed) FEEDERS.put(animal, new Feeder(fed.getUUID(), fed.getName().getString()));
    }

    /** Minecraft's love cause, which only resolves while that player is online, or else the player who last fed it. */
    private static Feeder feeder(Animal animal) {
        ServerPlayer online = animal.getLoveCause();
        return online != null ? new Feeder(online.getUUID(), online.getName().getString()) : FEEDERS.get(animal);
    }

    public static void onLoad(Entity entity, ServerLevel world) {
        if (!(entity instanceof Animal)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        if (!FamilyTreeServerSettings.current().discoverPetsOnLoad() && !state.contains(entity.getUUID())) return;
        ensureRecord(world, state, entity);
    }

    public static void onUnload(Entity entity, ServerLevel world) {
        if (!(entity instanceof Animal)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        if (state.contains(entity.getUUID())) stampLastSeen(world, state, entity);
    }

    public static void onBred(ServerLevel world, Animal parentA, Animal parentB, Animal child) {
        Feeder feederA = feeder(parentA), feederB = feeder(parentB);
        Feeder breeder = feederA != null ? feederA : feederB;
        if (!PetFilter.isTameableSpecies(child)) {
            recordBirth(world, parentA, parentB, child, breeder == null ? null : breeder.id(), breeder == null ? null : breeder.name());
            return;
        }

        FamilyTreeState state = FamilyTreeState.get(world);
        if (!PetFilter.records(child, AnimalCategory.PETS)) return;

        rememberParent(world, state, parentA, feederA != null ? feederA : breeder);
        rememberParent(world, state, parentB, feederB != null ? feederB : breeder);

        UUID childId = child.getUUID();

        String species = BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()).toString();
        String existingName = nameOrNull(child);
        boolean autoNamed = existingName == null;
        String name = autoNamed ? NameGenerator.generate(childId, state.all()) : existingName;

        UUID owner = ownerOf(child);
        String ownerName = ownerNameOf(world, owner);
        if (owner == null) {
            UUID parentOwner = ownerOf(parentA);
            String parentOwnerName = parentOwner == null ? null : ownerNameOf(world, parentOwner);
            if (parentOwner == null) {
                parentOwner = ownerOf(parentB);
                parentOwnerName = parentOwner == null ? null : ownerNameOf(world, parentOwner);
            }
            if (parentOwner == null) {
                AnimalRecord parentARecord = state.get(parentA.getUUID());
                if (parentARecord != null && parentARecord.ownerId() != null) {
                    parentOwner = parentARecord.ownerId();
                    parentOwnerName = parentARecord.ownerName();
                }
            }
            if (parentOwner == null) {
                AnimalRecord parentBRecord = state.get(parentB.getUUID());
                if (parentBRecord != null && parentBRecord.ownerId() != null) {
                    parentOwner = parentBRecord.ownerId();
                    parentOwnerName = parentBRecord.ownerName();
                }
            }
            if (parentOwner == null && breeder != null) {
                parentOwner = breeder.id();
                parentOwnerName = breeder.name();
            }
            owner = parentOwner;
            ownerName = parentOwnerName;
        }

        String origin = variantOrigin(child, parentA, parentB);
        // Entity-load discovery can register the baby before the breeding hook runs.
        if (state.contains(childId)) {
            UUID inheritedOwner = owner;
            String inheritedOwnerName = ownerName;
            state.update(childId, record -> {
                record.setParents(parentA.getUUID(), parentB.getUUID());
                record.setVariantOrigin(origin);
                if (inheritedOwner != null) record.setOwner(inheritedOwner, inheritedOwnerName);
            });
            stampLastSeen(world, state, child);
            return;
        }

        AnimalRecord rec = new AnimalRecord(
                childId,
                species,
                name,
                autoNamed,
                parentA.getUUID(),
                parentB.getUUID(),
                TimeUtil.currentWorldDay(world),
                TimeUtil.currentEpochMillis(),
                false, null, null,
                owner, ownerName, variantOf(child),
                null, null
        );
        rec.setVariantOrigin(origin);
        state.put(rec);
        stampLastSeen(world, state, child);
    }

    /** Judged from the live parents at birth, because dye and lightning can change their variants later. */
    private static String variantOrigin(Animal child, Animal parentA, Animal parentB) {
        VariantRules.Outcome outcome = VariantRules.outcome(PetFilter.species(child), variantOf(child), variantOf(parentA), variantOf(parentB));
        return outcome == VariantRules.Outcome.UNKNOWN ? null : outcome.key();
    }

    private static void rememberParent(ServerLevel world, FamilyTreeState state, Animal parent, Feeder feeder) {
        if (feeder != null && PetFilter.isUnclaimedMount(parent)) claim(world, state, parent, feeder.id(), feeder.name());
        else ensureRecord(world, state, parent);
    }

    /**
     * Records a birth of animals other than pets. A player's breeding brings the baby and both parents into the
     * history. Each new parent belongs to the player who fed it, or to the breeder when nobody fed it. Without a
     * player, only families already in the history continue, so wild animals stay out. The baby belongs to its
     * first parent's owner, or to the breeder when neither parent has one.
     */
    public static void recordBirth(ServerLevel world, Animal parentA, Animal parentB, Animal child,
                                   UUID breeder, String breederName) {
        FamilyTreeState state = FamilyTreeState.get(world);
        if (state.readOnly() || !PetFilter.recordsNewAnimal(child)) return;
        UUID childId = child.getUUID();
        String origin = variantOrigin(child, parentA, parentB);
        if (state.contains(childId)) {
            state.update(childId, record -> {
                record.setParents(parentA.getUUID(), parentB.getUUID());
                record.setVariantOrigin(origin);
            });
            return;
        }
        boolean knownA = state.contains(parentA.getUUID()), knownB = state.contains(parentB.getUUID());
        if (breeder == null && !knownA && !knownB) return;
        if (!roomForFarmRecords(world, state, 1 + (knownA ? 0 : 1) + (knownB ? 0 : 1))) {
            noticeFarmLimit(world, state, breeder, "familytree.farm.limit");
            return;
        }

        Feeder feederA = feeder(parentA), feederB = feeder(parentB);
        AnimalRecord a = recordParent(world, state, parentA, parentB, feederA != null ? feederA.id() : breeder,
                feederA != null ? feederA.name() : breederName);
        AnimalRecord b = recordParent(world, state, parentB, parentA, feederB != null ? feederB.id() : breeder,
                feederB != null ? feederB.name() : breederName);
        AnimalRecord owner = a.ownerId() != null ? a : b.ownerId() != null ? b : null;
        AnimalRecord record = newRecord(world, state, child);
        record.setParents(parentA.getUUID(), parentB.getUUID());
        record.setVariantOrigin(origin);
        if (owner != null) record.setOwner(owner.ownerId(), owner.ownerName());
        else record.setOwner(breeder, breederName);
        state.put(record);
        stampLastSeen(world, state, child);
    }

    /** Frogs, sniffers, and turtles lay spawn or an egg instead of a baby, so a player's breeding records only the parents. */
    public static void onBredWithoutBaby(ServerLevel world, Animal parentA, Animal parentB) {
        Feeder feederA = feeder(parentA), feederB = feeder(parentB);
        Feeder breeder = feederA != null ? feederA : feederB;
        if (breeder == null || PetFilter.isTameableSpecies(parentA) || !PetFilter.recordsNewAnimal(parentA)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        if (state.readOnly()) return;
        int needed = (state.contains(parentA.getUUID()) ? 0 : 1) + (state.contains(parentB.getUUID()) ? 0 : 1);
        if (needed > 0 && !roomForFarmRecords(world, state, needed)) {
            noticeFarmLimit(world, state, breeder.id(), "familytree.farm.limit_parents");
            return;
        }
        Feeder fedB = feederB != null ? feederB : breeder;
        recordParent(world, state, parentA, parentB, breeder.id(), breeder.name());
        recordParent(world, state, parentB, parentA, fedB.id(), fedB.name());
    }

    private static AnimalRecord recordParent(ServerLevel world, FamilyTreeState state, Animal parent, Animal mate,
                                             UUID breeder, String breederName) {
        UUID id = parent.getUUID();
        if (!state.contains(id)) {
            AnimalRecord record = newRecord(world, state, parent);
            AnimalRecord mateRecord = state.get(mate.getUUID());
            if (breeder != null) record.setOwner(breeder, breederName);
            else if (mateRecord != null) record.setOwner(mateRecord.ownerId(), mateRecord.ownerName());
            state.put(record);
        } else if (breeder != null && state.get(id).ownerId() == null) {
            state.update(id, record -> record.setOwner(breeder, breederName));
        }
        ensureRecord(world, state, parent);
        return state.get(id);
    }

    private static boolean roomForFarmRecords(ServerLevel world, FamilyTreeState state, int needed) {
        TrackingRules rules = FamilyTreeServerSettings.tracking();
        int limit = rules.farmRecordLimit();
        if (state.unnamedFarmRecords() + needed <= limit) return true;
        return rules.autoPruneFarm() && FarmCleanup.autoPrune(world.getServer(), state, limit) > 0
                && state.unnamedFarmRecords() + needed <= limit;
    }

    private static void noticeFarmLimit(ServerLevel world, FamilyTreeState state, UUID player, String message) {
        int limit = FamilyTreeServerSettings.tracking().farmRecordLimit();
        if (state.firstFarmLimitNotice(new UUID(0, 0)))
            FamilyTreeMod.LOGGER.warn("Family Tree reached farmRecordLimit={} and stopped recording new unnamed animals other than pets.", limit);
        if (!FamilyTreeServerSettings.current().farmLimitMessage()) return;
        ServerPlayer online = player == null ? null : world.getServer().getPlayerList().getPlayer(player);
        if (online != null && state.firstFarmLimitNotice(player)) ServerText.send(online, Component.translatable(message, limit));
    }

    public static void onNameTagged(Entity target, ServerPlayer player) {
        if (!(target.level() instanceof ServerLevel world) || !target.hasCustomName() || PetFilter.isTameableSpecies(target)) return;
        addAnimal(world, FamilyTreeState.get(world), target, player.getUUID(), player.getName().getString());
    }

    public enum AddResult { ADDED, NOT_RECORDED, FULL }

    /** Unnamed additions count toward the farm limit; named ones never do. */
    public static AddResult addAnimal(ServerLevel world, FamilyTreeState state, Entity entity, UUID owner, String ownerName) {
        UUID id = entity.getUUID();
        if (state.readOnly()) return AddResult.NOT_RECORDED;
        if (!state.contains(id)) {
            if (!PetFilter.recordsNewAnimal(entity)) return AddResult.NOT_RECORDED;
            if (!entity.hasCustomName() && !roomForFarmRecords(world, state, 1)) return AddResult.FULL;
            state.put(newRecord(world, state, entity));
        }
        state.update(id, record -> {
            if (record.ownerId() == null) record.setOwner(owner, ownerName);
        });
        ensureRecord(world, state, entity);
        return AddResult.ADDED;
    }

    public static void onCustomNameChanged(Entity entity, Component newName) {
        if (!(entity instanceof Animal)) return;
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        FamilyTreeState state = FamilyTreeState.get(serverLevel);
        UUID id = entity.getUUID();
        AnimalRecord existing = state.get(id);
        String displayName = newName == null ? null : newName.getString();
        if (displayName == null || displayName.isBlank()) return;

        if (existing != null) {
            state.update(id, r -> r.rename(displayName, false));
            return;
        }
        if (PetFilter.isTrackable(entity)) {
            ensureRecord(serverLevel, state, entity);
            state.update(id, r -> r.rename(displayName, false));
        }
    }

    public static void onTamableAnimalTamed(TamableAnimal entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel world)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        boolean wasTracked = state.contains(entity.getUUID());
        ensureRecord(world, state, entity);
        if (!wasTracked) {
            state.update(entity.getUUID(), record -> record.setOwner(player.getUUID(), player.getName().getString()));
        }
    }

    /** The first rider of an unclaimed mount becomes its owner in Family Tree. Vanilla ownership is unchanged. */
    public static void onMountRidden(AbstractHorse mount, ServerPlayer rider) {
        if (!(mount.level() instanceof ServerLevel world) || !PetFilter.isUnclaimedMount(mount)) return;
        claim(world, FamilyTreeState.get(world), mount, rider.getUUID(), rider.getName().getString());
    }

    private static void claim(ServerLevel world, FamilyTreeState state, Entity entity, UUID owner, String ownerName) {
        UUID id = entity.getUUID();
        if (!state.contains(id)) {
            if (!PetFilter.records(entity, AnimalCategory.PETS)) return;
            state.put(newRecord(world, state, entity));
        }
        state.update(id, record -> {
            if (record.ownerId() == null) record.setOwner(owner, ownerName);
        });
        ensureRecord(world, state, entity);
    }

    public static void onHorseTamed(AbstractHorse entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel world)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        boolean wasTracked = state.contains(entity.getUUID());
        ensureRecord(world, state, entity);
        if (!wasTracked) {
            state.update(entity.getUUID(), record -> record.setOwner(player.getUUID(), player.getName().getString()));
        }
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel world)) return;
        if (!(entity instanceof Animal)) return;

        FamilyTreeState state = FamilyTreeState.get(world);
        UUID id = entity.getUUID();
        if (!state.contains(id)) return;
        Entity killer = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        String attacker = killer != null ? killer.getDisplayName().getString() : null;
        AnimalRecord.DeathCause cause = new AnimalRecord.DeathCause(attacker, source.getMsgId());
        state.update(id, r -> r.markDeceased(TimeUtil.currentWorldDay(world), TimeUtil.currentEpochMillis(), cause));
        stampLastSeen(world, state, entity);
    }

    /** Animals already in the history stay current even when Minecraft no longer reports them as tamed, such as foals. */
    private static boolean tracked(FamilyTreeState state, Entity entity) {
        return PetFilter.isTrackable(entity) || entity instanceof Animal && state.contains(entity.getUUID());
    }

    public static void ensureRecord(ServerLevel world, FamilyTreeState state, Entity entity) {
        if (!tracked(state, entity)) return;
        UUID id = entity.getUUID();
        PetFilter.category(entity);
        if (state.contains(id)) {
            UUID owner = ownerOf(entity);
            if (owner != null) {
                String ownerName = ownerNameOf(world, owner);
                state.update(id, r -> r.setOwner(owner, ownerName != null ? ownerName : r.ownerName()));
            }
            String variantId = variantOf(entity);
            if (variantId != null) {
                state.update(id, r -> r.setVariantId(variantId));
            }
            AnimalRecord r = state.get(id);
            if (r != null && r.nameAutogenerated() && NameGenerator.isLegacyOverflowName(r.originalName())) {
                state.update(id, rec -> rec.rename(NameGenerator.generate(id, state.all()), true));
                r = state.get(id);
            }
            String currentName = nameOrNull(entity);
            if (currentName != null && r != null) {
                if (r.nameAutogenerated() || !r.originalName().equals(currentName)) {
                    state.update(id, rec -> rec.rename(currentName, false));
                }
            }
            stampLastSeen(world, state, entity);
            return;
        }

        state.put(newRecord(world, state, entity));
        stampLastSeen(world, state, entity);
    }

    private static AnimalRecord newRecord(ServerLevel world, FamilyTreeState state, Entity entity) {
        UUID id = entity.getUUID();
        String species = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String existingName = nameOrNull(entity);
        boolean autoNamed = existingName == null;
        String name = !autoNamed ? existingName : PetFilter.isTameableSpecies(entity)
                ? NameGenerator.generate(id, state.all())
                : NameGenerator.farmName(species, id);
        return new AnimalRecord(
                id, species, name, autoNamed,
                null, null,
                TimeUtil.currentWorldDay(world),
                TimeUtil.currentEpochMillis(),
                false, null, null,
                ownerOf(entity), ownerNameOf(world, ownerOf(entity)), variantOf(entity),
                null, null
        );
    }

    /** Records where the pet was seen and, for horse-type mounts, its current base stats. */
    public static void stampLastSeen(ServerLevel world, FamilyTreeState state, Entity entity) {
        HorseStats stats = statsOf(entity);
        state.update(entity.getUUID(), r -> {
            r.updateLastSeen(entity.getX(), entity.getY(), entity.getZ(),
                    world.dimension().identifier().toString(),
                    TimeUtil.currentWorldDay(world));
            if (stats != null) r.setHorseStats(stats);
        });
    }

    private static HorseStats statsOf(Entity entity) {
        if (!(entity instanceof AbstractHorse horse)) return null;
        return new HorseStats(
                baseValue(horse.getAttribute(Attributes.MOVEMENT_SPEED)),
                baseValue(horse.getAttribute(Attributes.JUMP_STRENGTH)),
                baseValue(horse.getAttribute(Attributes.MAX_HEALTH)),
                horse instanceof Llama llama ? llama.getStrength() : null);
    }

    private static Double baseValue(AttributeInstance attribute) {
        return attribute == null ? null : attribute.getBaseValue();
    }

    public static ScanResult scanLoadedPets(net.minecraft.server.MinecraftServer server) {
        return scanLoadedPets(server, record -> true);
    }

    public static ScanResult scanLoadedPets(net.minecraft.server.MinecraftServer server,
                                           java.util.function.Predicate<AnimalRecord> visible) {
        FamilyTreeState state = FamilyTreeState.get(server);
        int imported = 0;
        int refreshed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!tracked(state, entity)) continue;
                UUID id = entity.getUUID();
                boolean existed = state.contains(id);
                ensureRecord(level, state, entity);
                if (state.contains(id) && visible.test(state.get(id))) {
                    if (existed) {
                        refreshed++;
                    } else {
                        imported++;
                    }
                }
            }
        }

        return new ScanResult(imported, refreshed);
    }

    private static String nameOrNull(Entity entity) {
        if (!entity.hasCustomName()) return null;
        Component name = entity.getCustomName();
        if (name == null) return null;
        String s = name.getString();
        return s.isBlank() ? null : s;
    }

    private static UUID ownerOf(Entity entity) {
        return PetFilter.ownerOf(entity);
    }

    private static String ownerNameOf(ServerLevel world, UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(ownerId);
        if (player != null) {
            return player.getName().getString();
        }
        return null;
    }

    private static String variantOf(Entity entity) {
        //? if >=1.21 {
        if (entity instanceof Cat cat) {
            return variantKey(cat.getVariant());
        }
        if (entity instanceof Wolf wolf) {
            return variantKey(((WolfVariantInvoker) (Object) wolf).familytree$getVariant());
        }
        //?} else {
        /*if (entity instanceof Cat cat) {
            var key = BuiltInRegistries.CAT_VARIANT.getKey(cat.getVariant());
            return key == null ? null : key.toString();
        }
        *///?}
        if (entity instanceof Horse horse) {
            return horse.getVariant().getSerializedName() + "/" + horse.getMarkings().name().toLowerCase(Locale.ROOT);
        }
        if (entity instanceof Llama llama) {
            return llama.getVariant().getSerializedName();
        }
        return EntityVariants.read(entity);
    }

    private static String variantKey(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
    }

    public record ScanResult(int imported, int refreshed) {
        public int totalTouched() {
            return imported + refreshed;
        }
    }
}

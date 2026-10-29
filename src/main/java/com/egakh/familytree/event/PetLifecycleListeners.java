package com.egakh.familytree.event;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.data.HorseStats;
//? if >=1.21 {
import com.egakh.familytree.mixin.WolfVariantInvoker;
//?}
import com.egakh.familytree.naming.NameGenerator;
import com.egakh.familytree.util.PetFilter;
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

import java.util.Locale;
import java.util.UUID;

public final class PetLifecycleListeners {

    private PetLifecycleListeners() {}

    public static void onLoad(Entity entity, ServerLevel world) {
        if (!PetFilter.isTameableSpecies(entity)) return;
        ensureRecord(world, FamilyTreeState.get(world), entity);
    }

    public static void onUnload(Entity entity, ServerLevel world) {
        if (!PetFilter.isTameableSpecies(entity)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        if (state.contains(entity.getUUID())) stampLastSeen(world, state, entity);
    }

    public static void onBred(ServerLevel world, Animal parentA, Animal parentB, Animal child) {
        if (!PetFilter.isTameableSpecies(child)) return;

        FamilyTreeState state = FamilyTreeState.get(world);
        ServerPlayer breeder = parentA.getLoveCause() != null ? parentA.getLoveCause() : parentB.getLoveCause();

        rememberParent(world, state, parentA, breeder);
        rememberParent(world, state, parentB, breeder);

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
                parentOwner = breeder.getUUID();
                parentOwnerName = breeder.getName().getString();
            }
            owner = parentOwner;
            ownerName = parentOwnerName;
        }

        // Entity-load discovery can register the baby before the breeding hook runs.
        if (state.contains(childId)) {
            UUID inheritedOwner = owner;
            String inheritedOwnerName = ownerName;
            state.update(childId, record -> {
                record.setParents(parentA.getUUID(), parentB.getUUID());
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
        state.put(rec);
        stampLastSeen(world, state, child);
    }

    private static void rememberParent(ServerLevel world, FamilyTreeState state, Animal parent, ServerPlayer breeder) {
        if (breeder != null && PetFilter.isUnclaimedMount(parent)) claim(world, state, parent, breeder);
        else ensureRecord(world, state, parent);
    }

    public static void onCustomNameChanged(Entity entity, Component newName) {
        if (!PetFilter.isTameableSpecies(entity)) return;
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
        claim(world, FamilyTreeState.get(world), mount, rider);
    }

    private static void claim(ServerLevel world, FamilyTreeState state, Entity entity, ServerPlayer player) {
        UUID id = entity.getUUID();
        if (!state.contains(id)) state.put(newRecord(world, state, entity));
        state.update(id, record -> {
            if (record.ownerId() == null) record.setOwner(player.getUUID(), player.getName().getString());
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
        if (!PetFilter.isTameableSpecies(entity)) return;

        FamilyTreeState state = FamilyTreeState.get(world);
        UUID id = entity.getUUID();
        if (!state.contains(id)) return;
        Entity killer = source.getEntity();
        String attacker = killer != null ? killer.getDisplayName().getString() : null;
        AnimalRecord.DeathCause cause = new AnimalRecord.DeathCause(attacker, source.getMsgId());
        state.update(id, r -> r.markDeceased(TimeUtil.currentWorldDay(world), TimeUtil.currentEpochMillis(), cause));
        stampLastSeen(world, state, entity);
    }

    /** Pets already in the history stay current even when Minecraft no longer reports them as tamed, such as foals. */
    private static boolean tracked(FamilyTreeState state, Entity entity) {
        return PetFilter.isTrackable(entity) || PetFilter.isTameableSpecies(entity) && state.contains(entity.getUUID());
    }

    public static void ensureRecord(ServerLevel world, FamilyTreeState state, Entity entity) {
        if (!tracked(state, entity)) return;
        UUID id = entity.getUUID();
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
        String name = autoNamed ? NameGenerator.generate(id, state.all()) : existingName;
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
        return null;
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

package com.egakh.familytree.event;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.data.FamilyTreeState;
import com.egakh.familytree.mixin.WolfVariantInvoker;
import com.egakh.familytree.naming.NameGenerator;
import com.egakh.familytree.util.PetFilter;
import com.egakh.familytree.util.TimeUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.Objects;
import java.util.UUID;

public final class PetLifecycleListeners {

    private PetLifecycleListeners() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(PetLifecycleListeners::onDeath);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!PetFilter.isTrackable(entity)) return;
            FamilyTreeState state = FamilyTreeState.get(world);
            if (state.contains(entity.getUUID())) {
                stampPosition(world, state, entity);
            }
        });
    }

    public static void onBred(ServerLevel world, Animal parentA, Animal parentB, Animal child) {
        if (!PetFilter.isTameableSpecies(child)) return;

        FamilyTreeState state = FamilyTreeState.get(world);

        ensureRecord(world, state, parentA);
        ensureRecord(world, state, parentB);

        UUID childId = child.getUUID();
        if (state.contains(childId)) return;

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
            owner = parentOwner;
            ownerName = parentOwnerName;
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
        stampPosition(world, state, child);
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

    public static void onHorseTamed(AbstractHorse entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel world)) return;
        FamilyTreeState state = FamilyTreeState.get(world);
        boolean wasTracked = state.contains(entity.getUUID());
        ensureRecord(world, state, entity);
        if (!wasTracked) {
            state.update(entity.getUUID(), record -> record.setOwner(player.getUUID(), player.getName().getString()));
        }
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
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
        stampPosition(world, state, entity);
    }

    public static void ensureRecord(ServerLevel world, FamilyTreeState state, Entity entity) {
        if (!PetFilter.isTrackable(entity)) return;
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
            if (r != null && r.nameAutogenerated() && NameGenerator.isLegacyOverflowName(r.name())) {
                state.update(id, rec -> rec.rename(NameGenerator.generate(id, state.all()), true));
                r = state.get(id);
            }
            String currentName = nameOrNull(entity);
            if (currentName != null && r != null) {
                if (r.nameAutogenerated() || !r.name().equals(currentName)) {
                    state.update(id, rec -> rec.rename(currentName, false));
                }
            }
            stampPosition(world, state, entity);
            return;
        }

        String species = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String existingName = nameOrNull(entity);
        boolean autoNamed = existingName == null;
        String name = autoNamed ? NameGenerator.generate(id, state.all()) : existingName;

        AnimalRecord rec = new AnimalRecord(
                id, species, name, autoNamed,
                null, null,
                TimeUtil.currentWorldDay(world),
                TimeUtil.currentEpochMillis(),
                false, null, null,
                ownerOf(entity), ownerNameOf(world, ownerOf(entity)), variantOf(entity),
                null, null
        );
        state.put(rec);
        stampPosition(world, state, entity);
    }

    public static void stampPosition(ServerLevel world, FamilyTreeState state, Entity entity) {
        state.update(entity.getUUID(), r -> r.updateLastSeen(
                entity.getX(), entity.getY(), entity.getZ(),
                world.dimension().identifier().toString(),
                TimeUtil.currentWorldDay(world)));
    }

    public static ScanResult scanLoadedPets(net.minecraft.server.MinecraftServer server) {
        FamilyTreeState state = FamilyTreeState.get(server);
        int imported = 0;
        int refreshed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!PetFilter.isTrackable(entity)) continue;
                UUID id = entity.getUUID();
                boolean existed = state.contains(id);
                ensureRecord(level, state, entity);
                if (state.contains(id)) {
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
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return ownable.getOwnerReference().getUUID();
        }
        return null;
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
        if (entity instanceof Cat cat) {
            return variantKey(cat.getVariant());
        }
        if (entity instanceof Wolf wolf) {
            return variantKey(((WolfVariantInvoker) (Object) wolf).familytree$getVariant());
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

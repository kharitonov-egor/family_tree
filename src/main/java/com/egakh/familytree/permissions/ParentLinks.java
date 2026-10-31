package com.egakh.familytree.permissions;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.util.Genealogy;
import com.egakh.familytree.util.PetRelations;
import java.util.UUID;
import java.util.function.Function;

public final class ParentLinks {
    public enum Result { SUCCESS, NOT_ALLOWED, NOT_FOUND, DISTINCT, SPECIES, CYCLE, READ_ONLY, BUSY }
    private ParentLinks() {}

    public static Result validate(UUID childId, UUID aId, UUID bId, boolean clear, UUID player,
                                   boolean operator, Function<UUID, AnimalRecord> lookup) {
        AnimalRecord child = lookup.apply(childId);
        if (child == null) return Result.NOT_FOUND;
        if (!PetAccess.canManage(child, player, operator)) return Result.NOT_ALLOWED;
        if (clear) return Result.SUCCESS;
        AnimalRecord a = lookup.apply(aId), b = lookup.apply(bId);
        if (a == null || b == null) return Result.NOT_FOUND;
        if (!PetAccess.canManage(a, player, operator) || !PetAccess.canManage(b, player, operator)) return Result.NOT_ALLOWED;
        if (childId.equals(aId) || childId.equals(bId) || aId.equals(bId)) return Result.DISTINCT;
        if (!PetRelations.speciesMatch(a, b, child)) return Result.SPECIES;
        if (Genealogy.wouldCreateCycle(childId, aId, bId, lookup)) return Result.CYCLE;
        return Result.SUCCESS;
    }

    /**
     * Whether a parent picker should offer {@code candidate}, so the player only sees choices {@link #validate} accepts:
     * the child's species (a horse and a donkey for a mule, since mules don't breed), not the child, not the other
     * chosen parent, and not one of the child's descendants. An {@code other} parent that could never fit is ignored.
     */
    public static boolean mayPick(AnimalRecord child, AnimalRecord candidate, AnimalRecord other,
                                  Function<UUID, AnimalRecord> lookup) {
        if (candidate.id().equals(child.id())) return false;
        if (other != null && candidate.id().equals(other.id())) return false;
        boolean otherFits = other != null && !other.id().equals(child.id()) && breedsInto(other.speciesId(), child.speciesId());
        boolean species = breedsInto(candidate.speciesId(), child.speciesId())
                && (!otherFits || PetRelations.speciesMatch(candidate, other, child));
        return species && !Genealogy.wouldCreateCycle(child.id(), candidate.id(), null, lookup);
    }

    private static boolean breedsInto(String parent, String child) {
        return child.equals("minecraft:mule") ? parent.equals("minecraft:horse") || parent.equals("minecraft:donkey") : parent.equals(child);
    }
}

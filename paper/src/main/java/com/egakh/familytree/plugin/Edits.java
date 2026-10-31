package com.egakh.familytree.plugin;

import com.egakh.familytree.data.AnimalRecord;
import com.egakh.familytree.naming.PetRenaming;
import com.egakh.familytree.permissions.FamilyTreePermission;
import com.egakh.familytree.permissions.ParentLinks;
import com.egakh.familytree.plugin.storage.FamilyTreeStore;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Renames and parent edits shared by the chest menu and the client mod's screens, with the mod's checks in its order. */
public final class Edits {
    private Edits() {}

    /** An empty name restores the original name. */
    public static PetRenaming.Result rename(FamilyTreePlugin plugin, Player player, UUID petId, String name) {
        if (!plugin.cooldowns().edit(player.getUniqueId())) return PetRenaming.Result.BUSY;
        FamilyTreeStore state = plugin.store();
        if (state.readOnly()) return PetRenaming.Result.READ_ONLY;
        AnimalRecord record = state.get(petId);
        if (!plugin.access().allowed(player, FamilyTreePermission.RENAME)) return PetRenaming.Result.NOT_ALLOWED;
        PetRenaming.Result result = PetRenaming.rename(record, player.getUniqueId(), plugin.access().manageAll(player), name);
        if (result == PetRenaming.Result.SUCCESS) state.recordEdited();
        return result;
    }

    public static ParentLinks.Result link(FamilyTreePlugin plugin, Player player, UUID child, UUID parentA, UUID parentB, boolean clear) {
        FamilyTreeStore state = plugin.store();
        ParentLinks.Result result = !plugin.cooldowns().edit(player.getUniqueId()) ? ParentLinks.Result.BUSY
                : state.readOnly() ? ParentLinks.Result.READ_ONLY
                : !plugin.access().allowed(player, FamilyTreePermission.PARENTS) ? ParentLinks.Result.NOT_ALLOWED
                : ParentLinks.validate(child, parentA, parentB, clear, player.getUniqueId(), plugin.access().manageAll(player), state::get);
        if (result == ParentLinks.Result.SUCCESS)
            state.update(child, pet -> pet.setParents(clear ? null : parentA, clear ? null : parentB));
        return result;
    }
}

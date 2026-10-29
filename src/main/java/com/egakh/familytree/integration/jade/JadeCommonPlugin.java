package com.egakh.familytree.integration.jade;

import com.egakh.familytree.integration.PetInsight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade loads this only when it is installed. The server adds the family details each viewer may see. */
@WailaPlugin
public final class JadeCommonPlugin implements IWailaPlugin {
    public static final Identifier UID = Identifier.fromNamespaceAndPath("familytree", "pet_family");

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(PetData.INSTANCE, Animal.class);
    }

    private enum PetData implements IServerDataProvider<EntityAccessor> {
        INSTANCE;

        @Override public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (accessor.getPlayer() instanceof ServerPlayer player)
                PetInsight.write(data, PetInsight.forViewer(player, accessor.getEntity().getUUID()));
        }

        @Override public Identifier getUid() { return UID; }
    }
}

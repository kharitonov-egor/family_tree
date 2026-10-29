package com.egakh.familytree.integration.wthit;

import com.egakh.familytree.integration.PetInsight;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.entity.animal.Animal;

/** WTHIT reads this class from waila_plugins.json only when it is installed. */
public final class WthitCommonPlugin implements IWailaCommonPlugin {
    @Override public void register(ICommonRegistrar registrar) {
        registrar.entityData(PetData.INSTANCE, Animal.class);
    }

    private enum PetData implements IDataProvider<Animal> {
        INSTANCE;

        @Override public void appendData(IDataWriter data, IServerAccessor<Animal> accessor, IPluginConfig config) {
            PetInsight.write(data.raw(), PetInsight.forViewer(accessor.getPlayer(), accessor.getTarget().getUUID()));
        }
    }
}

package com.egakh.familytree.client.integration.wthit;

import com.egakh.familytree.client.integration.PetTooltip;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.entity.animal.Animal;

public final class WthitClientPlugin implements IWailaClientPlugin {
    @Override public void register(IClientRegistrar registrar) {
        registrar.body(PetFamily.INSTANCE, Animal.class);
    }

    private enum PetFamily implements IEntityComponentProvider {
        INSTANCE;

        @Override public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            PetTooltip.lines(accessor.getData().raw(), accessor.getEntity().getUUID()).forEach(tooltip::addLine);
        }
    }
}

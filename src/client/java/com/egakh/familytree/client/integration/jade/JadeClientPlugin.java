package com.egakh.familytree.client.integration.jade;

import com.egakh.familytree.client.integration.PetTooltip;
import com.egakh.familytree.integration.jade.JadeCommonPlugin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.Animal;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class JadeClientPlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(PetFamily.INSTANCE, Animal.class);
    }

    private enum PetFamily implements IEntityComponentProvider {
        INSTANCE;

        @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            PetTooltip.lines(accessor.getServerData(), accessor.getEntity().getUUID()).forEach(tooltip::add);
        }

        @Override public Identifier getUid() { return JadeCommonPlugin.UID; }
    }
}

package com.egakh.familytree.client.integration.wthit;

import com.egakh.familytree.client.integration.PetTooltip;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.EnumSet;
import java.util.Set;

public final class WthitClientPlugin implements IWailaClientPlugin {
    private static final Identifier HORSE_JUMP = Identifier.fromNamespaceAndPath("minecraft", "horse.jump_height");
    private static final Identifier HORSE_SPEED = Identifier.fromNamespaceAndPath("minecraft", "horse.speed");

    @Override public void register(IClientRegistrar registrar) {
        registrar.body(PetFamily.INSTANCE, Animal.class);
    }

    /** WTHIT's own horse lines show jump and speed for every horse-type mount, each behind its own option. */
    private static Set<Stat> shownByWthit(Entity entity, IPluginConfig config) {
        Set<Stat> shown = EnumSet.noneOf(Stat.class);
        if (!(entity instanceof AbstractHorse)) return shown;
        if (enabled(config, HORSE_JUMP)) shown.add(Stat.JUMP);
        if (enabled(config, HORSE_SPEED)) shown.add(Stat.SPEED);
        return shown;
    }

    private static boolean enabled(IPluginConfig config, Identifier option) {
        try {
            return config.getBoolean(option);
        } catch (RuntimeException missing) {
            return false;
        }
    }

    private enum PetFamily implements IEntityComponentProvider {
        INSTANCE;

        @Override public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            Entity entity = accessor.getEntity();
            PetTooltip.lines(accessor.getData().raw(), entity.getUUID(), shownByWthit(entity, config)).forEach(tooltip::addLine);
        }
    }
}

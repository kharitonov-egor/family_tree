package com.egakh.familytree.client.integration.jade;

import com.egakh.familytree.client.integration.PetTooltip;
import com.egakh.familytree.integration.jade.JadePlugin;
import com.egakh.familytree.util.HorseStatUnits.Stat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

import java.util.EnumSet;
import java.util.Set;

/** The client half of {@link JadePlugin}. */
public final class JadeTooltips {
    private static final Identifier HORSE_STATS = Identifier.fromNamespaceAndPath("minecraft", "horse_stats");

    private JadeTooltips() {}

    public static void register(IWailaClientRegistration registration) {
        registration.registerEntityComponent(PetFamily.INSTANCE, Animal.class);
    }

    /** What Jade's own Horse Stats lines show: strength for llamas, nothing for camels, and speed and jump otherwise. */
    private static Set<Stat> shownByJade(Entity entity, IPluginConfig config) {
        if (!(entity instanceof AbstractHorse) || entity instanceof Camel || !enabled(config)) return Set.of();
        return entity instanceof Llama ? EnumSet.of(Stat.STRENGTH) : EnumSet.of(Stat.SPEED, Stat.JUMP);
    }

    private static boolean enabled(IPluginConfig config) {
        try {
            return config.get(HORSE_STATS);
        } catch (RuntimeException missing) {
            return false;
        }
    }

    private enum PetFamily implements IEntityComponentProvider {
        INSTANCE;

        @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            Entity entity = accessor.getEntity();
            PetTooltip.lines(accessor.getServerData(), entity.getUUID(), shownByJade(entity, config)).forEach(tooltip::add);
        }

        @Override public Identifier getUid() { return JadePlugin.UID; }
    }
}

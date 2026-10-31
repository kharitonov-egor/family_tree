package com.egakh.familytree.menu;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if <1.21.2 {
/*import org.joml.Vector3f;
*///?}

import java.util.UUID;

/** A column of colored dust over a relative, sent only to the player who asked. Clients without the mod see it too. */
final class RelativeMarkers {
    private static final float SIZE = 1.5F;

    private RelativeMarkers() {}

    static void mark(ServerPlayer player, UUID entityId, int color) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;
        double x = entity.getX(), y = entity.getY() + entity.getBbHeight() + 0.8, z = entity.getZ();
        //? if >=1.21.2 {
        level.sendParticles(player, new DustParticleOptions(color, SIZE), true, false, x, y, z, 24, 0.2, 0.6, 0.2, 0);
        //?} else {
        /*DustParticleOptions dust = new DustParticleOptions(new Vector3f(((color >> 16) & 0xFF) / 255F,
                ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F), SIZE);
        level.sendParticles(player, dust, true, x, y, z, 24, 0.2, 0.6, 0.2, 0);
        *///?}
    }
}

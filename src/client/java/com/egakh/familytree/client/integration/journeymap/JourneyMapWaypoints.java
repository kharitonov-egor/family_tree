package com.egakh.familytree.client.integration.journeymap;

import com.egakh.familytree.FamilyTreeMod;
import com.egakh.familytree.client.integration.MapWaypoints;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * JourneyMap creates this plugin when it is installed and hands it the client API. JourneyMap 6.0 releases look
 * for the common annotation, and the 6.0.0 betas for the client one; the JVM ignores whichever is missing.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
@journeymap.api.v2.client.JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapWaypoints implements IClientPlugin, MapWaypoints.Target {
    private IClientAPI api;

    @Override public String getModId() { return FamilyTreeMod.MOD_ID; }

    @Override public void initialize(IClientAPI clientApi) {
        api = clientApi;
        MapWaypoints.register(this);
    }

    @Override public Component name() {
        return Component.translatable("familytree.waypoint.journeymap");
    }

    @Override public void add(String petName, BlockPos position, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) throw new IllegalArgumentException("Unknown dimension " + dimension);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        Waypoint waypoint;
        try {
            waypoint = WaypointFactory.createWaypoint(FamilyTreeMod.MOD_ID, position, petName, key, true);
        } catch (NoSuchMethodError beta) {
            waypoint = WaypointFactory.createClientWaypoint(FamilyTreeMod.MOD_ID, position, petName, key, true);
        }
        api.addWaypoint(FamilyTreeMod.MOD_ID, waypoint);
        MapWaypoints.tell(Component.translatable("familytree.waypoint.added", name(), petName, MapWaypoints.coordinates(position)));
    }
}

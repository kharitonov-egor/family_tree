package journeymap.api.v2.common.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class WaypointFactory {
    public static Waypoint createWaypoint(String modId, BlockPos pos, String name, ResourceKey<Level> dimension, boolean persistent) {
        throw new UnsupportedOperationException("Compile-only stub");
    }

    /** The JourneyMap 6.0.0 betas name the same factory this way. */
    public static Waypoint createClientWaypoint(String modId, BlockPos pos, String name, ResourceKey<Level> dimension, boolean persistent) {
        throw new UnsupportedOperationException("Compile-only stub");
    }
}

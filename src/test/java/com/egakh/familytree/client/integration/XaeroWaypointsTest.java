package com.egakh.familytree.client.integration;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XaeroWaypointsTest {
    @Test void usesXaerosShareFormat() {
        assertEquals("<Family Tree> xaero-waypoint:Ember:E:12:64:-300:10:false:0:Internal-overworld-waypoints",
                XaeroWaypoints.share("Ember", new BlockPos(12, 64, -300), "minecraft:overworld"));
        assertEquals("<Family Tree> xaero-waypoint:Sir^col^ Snow-ball^min^2:S:-1:70:5:10:false:0:Internal-the-nether-waypoints",
                XaeroWaypoints.share("Sir: Snow_ball-2", new BlockPos(-1, 70, 5), "minecraft:the_nether"));
        assertEquals("<Family Tree> xaero-waypoint:Rex:R:0:80:0:10:false:0:Internal-dim%twilightforest$twilight-forest-waypoints",
                XaeroWaypoints.share("Rex", new BlockPos(0, 80, 0), "twilightforest:twilight_forest"));
    }
}

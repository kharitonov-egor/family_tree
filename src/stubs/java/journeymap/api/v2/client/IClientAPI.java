package journeymap.api.v2.client;

import journeymap.api.v2.common.CommonAPI;
import journeymap.api.v2.common.waypoint.Waypoint;

public interface IClientAPI extends CommonAPI {
    void addWaypoint(String modId, Waypoint waypoint);
}

package journeymap.api.v2.client;

import journeymap.api.v2.common.IJourneyMapPlugin;

public interface IClientPlugin extends IJourneyMapPlugin<IClientAPI> {
    void initialize(IClientAPI api);
}

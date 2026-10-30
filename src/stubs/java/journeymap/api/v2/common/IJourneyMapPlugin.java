package journeymap.api.v2.common;

public interface IJourneyMapPlugin<T extends CommonAPI> {
    String getModId();
    void initialize(T api);
}

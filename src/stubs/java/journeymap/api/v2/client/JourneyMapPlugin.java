package journeymap.api.v2.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The plugin annotation of the JourneyMap 6.0.0 betas, deprecated in 6.0 releases. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JourneyMapPlugin {
    String apiVersion();
}

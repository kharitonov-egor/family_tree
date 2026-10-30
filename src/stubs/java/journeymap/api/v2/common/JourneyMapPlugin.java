package journeymap.api.v2.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JourneyMapPlugin {
    String apiVersion();
    String[] dependencies() default {};
    boolean require() default true;
}

package support.annotations;

import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(FIELD)
@Retention(RUNTIME)
public @interface RequestParam {

    String key() default "";

    boolean required() default true;

    Class<?> conversionService() default DefaultConversionService.class;
}

package support.annotations;

import support.dto.DTOBuilder;
import support.dto.DTOValidator;
import support.dto.NullDTOValidator;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(PARAMETER)
@Retention(RUNTIME)
public @interface RequestDTO {
    Class<?> input() default Object.class;

    Class<? extends DTOBuilder<?, ?>> builder();

    Class<? extends DTOValidator<?, ?>> validator() default NullDTOValidator.class;
}
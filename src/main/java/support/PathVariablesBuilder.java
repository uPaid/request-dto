package support;

import support.annotations.PathVariable;
import lombok.extern.java.Log;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Field;
import java.util.Map;

import static java.util.Objects.*;

@Log
public class PathVariablesBuilder {
    public <T> T build(T t, Map<String, String> pathVariables) {
        ConversionService conversionService = new DefaultConversionService();
        Class<?> tClass = t.getClass();
        for (Field field : tClass.getDeclaredFields()) {
            PathVariable annotation = field.getAnnotation(PathVariable.class);

            if (nonNull(annotation)) {
                String pathVariableName = annotation.value();
                if (pathVariableName.isEmpty()) {
                    pathVariableName = field.getName();
                }

                String pathVariable = pathVariables.get(pathVariableName);
                if (isNull(pathVariable)) {
                    log.warning("Path variable " + pathVariableName + " is not present in request.");
                }
                else {
                    field.setAccessible(true);
                    try {
                        Object value = conversionService.convert(pathVariable, field.getType());
                        field.set(t, value);
                    } catch (IllegalAccessException e) {
                        log.warning("Path variable setting failed. " + e);
                    }
                }
            }
        }

        return t;
    }
}

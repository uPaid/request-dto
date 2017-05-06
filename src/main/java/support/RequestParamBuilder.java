package support;

import support.annotations.RequestParam;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Field;
import java.util.Map;

import static java.util.Objects.*;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Log
public class RequestParamBuilder {

    @SneakyThrows
    public <T> T build(T input, Map<String, String> queryParams) {
        Class<?> tClass = input.getClass();
        for (Field field : tClass.getDeclaredFields()) {
            RequestParam annotation = field.getAnnotation(RequestParam.class);
            if (nonNull(annotation)) {
                boolean required = annotation.required();
                ConversionService conversionService = (ConversionService) annotation.conversionService()
                                                                                    .newInstance();
                String requestParamKey = annotation.key();
                if (requestParamKey.isEmpty()) {
                    requestParamKey = field.getName();
                }
                String requestParamValue = queryParams.get(requestParamKey);
                if (isBlank(requestParamValue)) {
                    if (required) {
                        log.warning("Request param " + requestParamKey + " is not present in request.");
                    }
                }
                else {
                    field.setAccessible(true);
                    try {

                        if (!required && isBlank(requestParamValue)) {
                            field.set(input, null);
                        }
                        else {
                            Object value = conversionService.convert(requestParamValue, field.getType());
                            field.set(input, value);
                        }
                    } catch (ConversionFailedException e) {
                        log.warning("Could not convert given value " + requestParamValue + " , exception " + e);
                        throw new RuntimeException();
                    } catch (Exception e) {
                        log.warning("Problem while setting a value " + requestParamValue + " with exception " + e);
                    }
                }
            }
        }

        return input;
    }
}

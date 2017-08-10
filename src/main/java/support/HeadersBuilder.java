package support;

import lombok.extern.java.Log;
import support.annotations.Header;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.util.Objects.*;

@Log
public class HeadersBuilder {
    public <T> T build(T t, Map<String, List<String>> headers) {
        Class<?> tClass = t.getClass();

        for (Field field : tClass.getDeclaredFields()) {
            Header annotation = field.getAnnotation(Header.class);

            if (nonNull(annotation)) {
                String headerName = annotation.value();
                if (headerName.isEmpty()) {
                    headerName = getHeaderNameFromJavaName(field.getName());
                }

                List<String> headerValues = headers.get(headerName.toLowerCase());
                if (isNull(headerValues)) {
                    log.warning("Header " + headerName + " is not present in request.");
                }
                else {
                    field.setAccessible(true);
                    try {
                        if (field.getType()
                                 .isAssignableFrom(List.class)) {
                            field.set(t, headerValues);
                        }
                        else {
                            field.set(t, headerValues.get(0));
                        }
                    } catch (IllegalAccessException e) {
                        log.warning("Header" + headerName + "setting failed. " + e);
                    } catch (Exception e) {
                        log.warning("Header " + headerName + " setting failed.");
                        throw e;
                    }
                }
            }
        }

        return t;
    }

    /**
     * Translates a header name (letters separated with hyphens) to java variable name.
     * For example: <p>X-Test-Header-Name</p> will be translated to: <p>xTestHeaderName</p>
     */
    private String getHeaderNameFromJavaName(String javaName) {
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : javaName.toCharArray()) {
            if (isUpperCase(c)) {
                c = toLowerCase(c);
                stringBuilder.append('-');
            }
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }
}
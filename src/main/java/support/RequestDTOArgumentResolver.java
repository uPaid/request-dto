package support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import support.annotations.RequestDTO;
import support.dto.DTO;
import support.dto.DTOBuilder;
import support.dto.DTOValidator;
import support.dto.NullDTOValidator;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Arrays.asList;
import static java.util.Objects.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

@Log
@RequiredArgsConstructor
public class RequestDTOArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String JSON_BODY_ATTRIBUTE = "JSON_REQUEST_BODY";

    private final ApplicationContext applicationContext;

    private final Validator validator;

    private final SerializationFunction serializationFunction;

    private final HeadersBuilder headersBuilder = new HeadersBuilder();

    private final PathVariablesBuilder pathVariablesBuilder = new PathVariablesBuilder();

    private final RequestParamBuilder requestParamBuilder = new RequestParamBuilder();

    public RequestDTOArgumentResolver(ApplicationContext applicationContext, Validator validator, ObjectMapper objectMapper) {
        this.applicationContext = applicationContext;
        this.validator = validator;
        this.serializationFunction = new SerializationFunction() {
            @Override
            @SneakyThrows
            public <T> T serialize(String input, Class<T> outputClass) {
                if (isEmpty(input)) {
                    return null;
                }
                else {
                    return objectMapper.readValue(input, outputClass);
                }
            }
        };
    }

    public RequestDTOArgumentResolver(ApplicationContext applicationContext, Validator validator) {
        this.applicationContext = applicationContext;
        this.validator = validator;
        this.serializationFunction = new SerializationFunction() {
            Gson gson = new Gson();

            @Override
            public <T> T serialize(String input, Class<T> outputClass) {
                return gson.fromJson(input, outputClass);
            }
        };
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestDTO.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
    throws IllegalAccessException, InstantiationException {
        RequestDTO annotation = parameter.getParameterAnnotation(RequestDTO.class);

        DTOBuilder<?, ?> dtoBuilder = applicationContext.getBean(annotation.builder());

        Class<?> inputClass = dtoBuilder.getInputClass();

        if (!inputClass.equals(annotation.input())) {
            log.warning("Builder input type is not compatible with input type declared in annotation.");
            throw new RuntimeException();
        }

        String requestBody = getRequestBody(webRequest);
        Map<String, String> pathVariables = getPathVariables(webRequest);
        Map<String, List<String>> headers = getHeaders(webRequest);
        Map<String, String> queryParams = getQueryParams(webRequest);

        Object input = serializationFunction.serialize(requestBody, inputClass);
        if (input == null) {
            input = inputClass.newInstance();
        }
        input = headersBuilder.build(input, headers);
        input = pathVariablesBuilder.build(input, pathVariables);
        input = requestParamBuilder.build(input, queryParams);

        validate(input);

        Object dto;
        try {
            dto = build(dtoBuilder, input);
        } catch (ClassCastException e) {
            log.warning("Declared builder output type is not compatible with parameter type. " + e);
            throw new RuntimeException();
        }

        Class<? extends DTOValidator<?, ?>> validatorClass = annotation.validator();
        if (validatorClass != NullDTOValidator.class) {
            DTOValidator<?, ?> validator = applicationContext.getBean(validatorClass);
            if (!validator.getSupportedDTOClass()
                          .equals(dto.getClass())) {
                log.warning("Declared validator input type is not compatible with parameter type.");
                throw new RuntimeException();
            }
            validate(validator, dto);
        }

        return dto;
    }

    @SuppressWarnings("unchecked")
    private <I, T extends DTO<I>> void validate(DTOValidator<I, T> validator, Object dto) {
        validator.validate((T) dto);
    }

    @SuppressWarnings("unchecked")
    private <I, T extends DTO<I>> T build(DTOBuilder<I, T> builder, Object input) {
        return builder.build((I) input);
    }

    @SneakyThrows(IOException.class)
    private String getRequestBody(NativeWebRequest webRequest) {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        String jsonBody = (String) servletRequest.getAttribute(JSON_BODY_ATTRIBUTE);
        if (nonNull(jsonBody)) {
            return jsonBody;
        }
        String body = IOUtils.toString(servletRequest.getInputStream());
        servletRequest.setAttribute(JSON_BODY_ATTRIBUTE, body);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPathVariables(NativeWebRequest webRequest) {
        HttpServletRequest httpServletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        return (Map<String, String>) httpServletRequest.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    }

    private Map<String, List<String>> getHeaders(NativeWebRequest webRequest) {
        Map<String, List<String>> headers = new HashMap<>();
        webRequest.getHeaderNames()
                  .forEachRemaining(name -> headers.put(name, asList(webRequest.getHeaderValues(name))));
        return headers;
    }

    private Map<String, String> getQueryParams(NativeWebRequest webRequest) {
        HttpServletRequest httpServletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(ofNullable(httpServletRequest.getQueryString()).orElse(""), defaultCharset());
        return nameValuePairs.stream()
                             .collect(toMap(NameValuePair::getName, NameValuePair::getValue));
    }

    private void validate(Object o) {
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(o);
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }
    }

    public interface SerializationFunction {
        <T> T serialize(String input, Class<T> outputClass);
    }

}
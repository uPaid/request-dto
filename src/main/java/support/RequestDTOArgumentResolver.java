package support;

import lombok.SneakyThrows;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import support.annotations.RequestDTO;
import support.dto.DTO;
import support.dto.DTOBuilder;
import support.dto.DTOValidator;
import support.dto.NullDTOValidator;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.Charset.defaultCharset;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

public class RequestDTOArgumentResolver extends AbstractMessageConverterMethodArgumentResolver {

    private final ApplicationContext applicationContext;

    private final Validator validator;

    private final HeadersBuilder headersBuilder = new HeadersBuilder();

    private final PathVariablesBuilder pathVariablesBuilder = new PathVariablesBuilder();

    private final RequestParamBuilder requestParamBuilder = new RequestParamBuilder();

    public RequestDTOArgumentResolver(ApplicationContext applicationContext,
                                      Validator validator,
                                      List<HttpMessageConverter<?>> converters) {
        super(converters);
        this.applicationContext = applicationContext;
        this.validator = validator;
    }

    public RequestDTOArgumentResolver(ApplicationContext applicationContext,
                                      Validator validator,
                                      List<HttpMessageConverter<?>> converters,
                                      List<Object> requestResponseBodyAdvices) {
        super(converters, requestResponseBodyAdvices);
        this.applicationContext = applicationContext;
        this.validator = validator;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestDTO.class);
    }

    @Override
    @SneakyThrows
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        RequestDTO annotation = parameter.getParameterAnnotation(RequestDTO.class);

        DTOBuilder<?, ?> dtoBuilder = applicationContext.getBean(annotation.builder());

        Class<?> inputClass = dtoBuilder.getInputClass();

        if (!inputClass.equals(annotation.input())) {
            throw new RequestDTOException("Builder input type is not compatible with input type declared in annotation.");
        }

        Map<String, String> pathVariables = getPathVariables(webRequest);
        Map<String, List<String>> headers = getHeaders(webRequest);
        Map<String, String> queryParams = getQueryParams(webRequest);

        Object input = readWithMessageConverters(webRequest, parameter, inputClass);
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
            throw new RequestDTOException("Declared builder output type is not compatible with parameter type.", e);
        }

        Class<? extends DTOValidator<?, ?>> validatorClass = annotation.validator();
        if (validatorClass != NullDTOValidator.class) {
            DTOValidator<?, ?> dtoValidator = applicationContext.getBean(validatorClass);
            if (!dtoValidator.getSupportedDTOClass()
                             .equals(dto.getClass())) {
                throw new RequestDTOException("Declared validator input type is not compatible with parameter type.");
            }
            validate(dtoValidator, dto);
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

}
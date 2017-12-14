package support

import com.google.gson.Gson
import org.springframework.context.ApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import spock.lang.Specification
import spock.lang.Unroll
import support.annotations.Header
import support.annotations.PathVariable
import support.annotations.RequestDTO
import support.dto.DTO
import support.dto.DTOBuilder
import support.dto.DTOValidator

import javax.servlet.http.HttpServletRequest
import javax.validation.ConstraintViolationException
import javax.validation.Validator
import javax.validation.constraints.NotNull
import java.lang.reflect.Type

import static java.lang.Boolean.TRUE
import static java.util.Collections.*
import static javax.validation.Validation.byDefaultProvider
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
import static support.RequestDTOArgumentResolverSpecTest.TestIn.TEST_HEADER_NAME

class RequestDTOArgumentResolverSpecTest extends Specification {

    def "should support RequestDTO parameter"() {
        given:
        def parameter = Mock MethodParameter
        def argumentResolver = new RequestDTOArgumentResolver(null, null, singletonList(new StringHttpMessageConverter()))

        when:
        parameter.hasParameterAnnotation(RequestDTO.class) >> true
        def supports = argumentResolver.supportsParameter parameter

        then:
        supports == TRUE
    }

    def "should resolve RequestDTO parameter for correct data"() {
        given:
        def context = Mock ApplicationContext
        def parameter = Mock MethodParameter
        def nativeRequest = Mock NativeWebRequest
        def request = Mock HttpServletRequest
        def annotation = Mock RequestDTO
        def validator = Mock Validator
        def argumentResolver = Spy(RequestDTOArgumentResolver, constructorArgs: [context, validator, [new MappingJackson2HttpMessageConverter()]]) {
            readWithMessageConverters(_ as NativeWebRequest, _ as MethodParameter, _ as Type) >> new TestIn(inputValue: "test")
        }

        when:
        TestDTO dto = argumentResolver.resolveArgument(parameter, null as ModelAndViewContainer, nativeRequest, null as WebDataBinderFactory) as TestDTO

        then:
        context.getBean(TestDTOBuilder.class) >> new TestDTOBuilder()
        context.getBean(TestDTOValidator.class) >> new TestDTOValidator()
        annotation.builder() >> TestDTOBuilder.class
        annotation.input() >> TestIn.class
        annotation.validator() >> TestDTOValidator.class
        validator.validate(_ as Object) >> emptySet()
        parameter.getParameterAnnotation(RequestDTO.class) >> annotation
        nativeRequest.getNativeRequest(HttpServletRequest.class) >> request
        request.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE) >> [testPathVariable: "test"]
        nativeRequest.getHeaderNames() >> [TEST_HEADER_NAME].iterator()
        nativeRequest.getHeaderValues(TEST_HEADER_NAME) >> ["test"]

        then:
        noExceptionThrown()
        dto.inputValue == "test"
        dto.testHeader == "test"
        dto.testPathVariable == "test"
    }

    @Unroll
    "should report #expectedViolationsAmount violations for incorrect data [request: #jsonRequest, path variables: #pathVariablesMap, header names: #headerNames, value: #headerValue]"() {
        given:
        def validator = byDefaultProvider().configure().buildValidatorFactory().getValidator()

        def context = Mock ApplicationContext
        def parameter = Mock MethodParameter
        def nativeRequest = Mock NativeWebRequest
        def annotation = Mock RequestDTO
        def request = Mock HttpServletRequest
        def argumentResolver = Spy(RequestDTOArgumentResolver, constructorArgs: [context, validator, [new MappingJackson2HttpMessageConverter()]]) {
            readWithMessageConverters(_ as NativeWebRequest, _ as MethodParameter, _ as Type) >> new Gson().fromJson(jsonRequest, TestIn)
        }

        when:
        context.getBean(TestDTOBuilder.class) >> new TestDTOBuilder()
        context.getBean(TestDTOValidator.class) >> new TestDTOValidator()
        annotation.builder() >> TestDTOBuilder.class
        annotation.input() >> TestIn.class
        annotation.validator() >> TestDTOValidator.class
        parameter.getParameterAnnotation(RequestDTO.class) >> annotation
        nativeRequest.getNativeRequest(HttpServletRequest.class) >> request
        nativeRequest.getHeaderNames() >> headerNames.iterator()
        nativeRequest.getHeaderValues(_ as String) >> [headerValue]
        request.getHeaderNames() >> new TestArrayEnumeration(headerNames)
        request.getHeaders(_ as String) >> new TestArrayEnumeration([headerValue])
        request.getAttribute(_ as String) >> { String attribute ->
            if (attribute == "JSON_REQUEST_BODY") {
                return jsonRequest
            } else if (attribute == URI_TEMPLATE_VARIABLES_ATTRIBUTE) {
                return pathVariablesMap
            }
        }
        argumentResolver.resolveArgument(parameter, null as ModelAndViewContainer, nativeRequest, null) as TestDTO

        then:
        ConstraintViolationException exception = thrown()
        exception.constraintViolations.size() == expectedViolationsAmount

        where:
        headerNames        | headerValue | pathVariablesMap           | jsonRequest              | expectedViolationsAmount
        emptyList()        | "test"      | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        [TEST_HEADER_NAME] | null        | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        [TEST_HEADER_NAME] | "test"      | emptyMap()                 | '{"inputValue": "test"}' | 1
        [TEST_HEADER_NAME] | "test"      | [testPathVariable: null]   | '{"inputValue": "test"}' | 1
        [TEST_HEADER_NAME] | "test"      | [testPathVariable: "test"] | '{}'                     | 1
        emptyList()        | null        | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        emptyList()        | null        | emptyMap()                 | '{}'                     | 3
    }

    static class TestIn {
        static final String TEST_HEADER_NAME = "test-header"

        @NotNull
        private String inputValue

        @NotNull
        @Header(TestIn.TEST_HEADER_NAME)
        private String testHeader

        @NotNull
        @PathVariable("testPathVariable")
        private String testPathVariable
    }

    class TestDTO implements DTO<TestIn> {
        private String inputValue
        private String testHeader
        private String testPathVariable
    }

    class TestDTOBuilder implements DTOBuilder<TestIn, TestDTO> {
        @Override
        TestDTO build(TestIn testIn) {
            return new TestDTO(inputValue: testIn.inputValue,
                               testHeader: testIn.testHeader,
                               testPathVariable: testIn.testPathVariable)
        }

        @Override
        Class<? extends TestIn> getInputClass() {
            return TestIn.class
        }
    }

    class TestDTOValidator implements DTOValidator<TestIn, TestDTO> {
        @Override
        void validate(TestDTO dto) {
        }

        @Override
        Class<TestDTO> getSupportedDTOClass() {
            return TestDTO.class
        }
    }

    class TestArrayEnumeration<T> implements Iterator<T>, Enumeration<T> {

        Iterator<T> objectIterator

        TestArrayEnumeration(List<T> objects) {
            this.objectIterator = objects.iterator()
        }

        @Override
        boolean hasMoreElements() {
            return objectIterator.hasNext()
        }

        @Override
        T nextElement() {
            return objectIterator.next()
        }

        @Override
        boolean hasNext() {
            return objectIterator.hasNext()
        }

        @Override
        T next() {
            return objectIterator.next()
        }
    }
}

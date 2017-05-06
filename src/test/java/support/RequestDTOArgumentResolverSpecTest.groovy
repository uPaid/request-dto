package support

import org.springframework.context.ApplicationContext
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import spock.lang.Specification
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

import static java.lang.Boolean.TRUE
import static java.util.Collections.*
import static javax.validation.Validation.byDefaultProvider
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE

// TODO: cleanup test cases
class RequestDTOArgumentResolverSpecTest extends Specification {
    def "should support RequestDTO parameter"() {
        given:
        def parameter = Mock MethodParameter
        def argumentResolver = new RequestDTOArgumentResolver(null, null)

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
        def annotation = Mock RequestDTO
        def request = Mock HttpServletRequest
        def validator = Mock Validator
        def argumentResolver = new RequestDTOArgumentResolver(context, validator)

        when:
        context.getBean(TestDTOBuilder.class) >> new TestDTOBuilder()
        context.getBean(TestDTOValidator.class) >> new TestDTOValidator()
        annotation.builder() >> TestDTOBuilder.class
        annotation.input() >> TestIn.class
        annotation.validator() >> TestDTOValidator.class
        validator.validate(_ as Object) >> emptySet()
        parameter.getParameterAnnotation(RequestDTO.class) >> annotation
        nativeRequest.getNativeRequest(HttpServletRequest.class) >> request
        nativeRequest.getHeaderNames() >> ["test-header"].iterator()
        nativeRequest.getHeaderValues("test-header") >> ["test"]
        request.getAttribute(_ as String) >> { String attribute ->
            if (attribute == "JSON_REQUEST_BODY") {
                return '''
                       {
                           "inputValue": "test"
                       }
                       '''
            } else if (attribute == URI_TEMPLATE_VARIABLES_ATTRIBUTE) {
                return [testPathVariable: "test"]
            }
        }
        TestDTO dto = argumentResolver.resolveArgument(parameter, null, nativeRequest, null) as TestDTO

        then:
        noExceptionThrown()
        dto.inputValue == "test"
        dto.testHeader == "test"
        dto.testPathVariable == "test"
    }

    def "should throw for incorrect data"() {
        given:
        def validator = byDefaultProvider().configure().buildValidatorFactory().getValidator()

        def context = Mock ApplicationContext
        def parameter = Mock MethodParameter
        def nativeRequest = Mock NativeWebRequest
        def annotation = Mock RequestDTO
        def request = Mock HttpServletRequest
        def argumentResolver = new RequestDTOArgumentResolver(context, validator)

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
        request.getAttribute(_ as String) >> { String attribute ->
            if (attribute == "JSON_REQUEST_BODY") {
                return jsonRequest
            } else if (attribute == URI_TEMPLATE_VARIABLES_ATTRIBUTE) {
                return pathVariablesMap
            }
        }
        argumentResolver.resolveArgument(parameter, null, nativeRequest, null) as TestDTO

        then:
        ConstraintViolationException exception = thrown()
        exception.constraintViolations.size() == expectedViolationsAmount

        where:
        headerNames     | headerValue | pathVariablesMap           | jsonRequest              | expectedViolationsAmount
        emptyList()     | "test"      | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        ["test-header"] | null        | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        ["test-header"] | "test"      | emptyMap()                 | '{"inputValue": "test"}' | 1
        ["test-header"] | "test"      | [testPathVariable: null]   | '{"inputValue": "test"}' | 1
        ["test-header"] | "test"      | [testPathVariable: "test"] | '{}'                     | 1
        emptyList()     | null        | [testPathVariable: "test"] | '{"inputValue": "test"}' | 1
        emptyList()     | null        | emptyMap()                 | '{}'                     | 3
    }

    class TestIn {
        @NotNull
        private String inputValue

        @NotNull
        @Header("test-header")
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
}

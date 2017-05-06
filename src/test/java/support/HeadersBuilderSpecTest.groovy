package support

import spock.lang.Specification
import support.annotations.Header

import static java.util.Collections.emptyMap
import static java.util.Collections.singletonMap
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric

class HeadersBuilderSpecTest extends Specification {

    def builder = new HeadersBuilder()
    ObjectUnderTest objectUnderTest

    void setup() {
        objectUnderTest = new ObjectUnderTest()
    }

    def "should return the same object if no headers are present"() {
        when:
        Object previousReference = objectUnderTest
        objectUnderTest = builder.build(objectUnderTest, emptyMap())

        then:
        noExceptionThrown()
        objectUnderTest == previousReference
    }

    def "should not add property that is not annotated as header to the object"() {
        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("not-a-header", [randomAlphanumeric(5)]))

        then:
        objectUnderTest.notAHeader == null
    }

    def "should add named header to the object"() {
        given:
        def header = randomAlphanumeric(5)

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("test", [header]))

        then:
        objectUnderTest.testHeaderNamed == header
    }

    def "should add unnamed header to the object"() {
        given:
        def header = randomAlphanumeric(5)

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("test-header-unnamed", [header]))

        then:
        objectUnderTest.testHeaderUnnamed == header
    }

    def "should add named headers to the object"() {
        given:
        def headers = [randomAlphanumeric(5)]

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("tests", headers))

        then:
        objectUnderTest.testHeadersNamed == headers
    }

    def "should add unnamed headers to the object"() {
        given:
        def headers = [randomAlphanumeric(5)]

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("test-headers-unnamed", headers))

        then:
        objectUnderTest.testHeadersUnnamed == headers
    }

    class ObjectUnderTest {
        String notAHeader

        @Header("test")
        String testHeaderNamed

        @Header
        String testHeaderUnnamed

        @Header("tests")
        List<String> testHeadersNamed

        @Header
        List<String> testHeadersUnnamed
    }
}

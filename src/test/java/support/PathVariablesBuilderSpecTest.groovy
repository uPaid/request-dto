package support


import spock.lang.Specification
import support.annotations.PathVariable

import static java.util.Collections.emptyMap
import static java.util.Collections.singletonMap
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric

class PathVariablesBuilderSpecTest extends Specification {

    def builder = new PathVariablesBuilder()
    ObjectUnderTest objectUnderTest

    void setup() {
        objectUnderTest = new ObjectUnderTest()
    }

    def "should return the same object if no path variables are present"() {
        when:
        Object previousReference = objectUnderTest
        objectUnderTest = builder.build(objectUnderTest, emptyMap())

        then:
        noExceptionThrown()
        objectUnderTest == previousReference
    }

    def "should not add property that is not annotated as path variable to the object"() {
        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("not-a-path-variable", randomAlphanumeric(5)))

        then:
        objectUnderTest.notAPathVariable == null
    }

    def "should add named path variable to the object"() {
        given:
        def pathVariable = randomAlphanumeric(5)

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("test", pathVariable))

        then:
        objectUnderTest.testPathVariableNamed == pathVariable
    }

    def "should add unnamed path variable to the object"() {
        given:
        def pathVariable = randomAlphanumeric(5)

        when:
        objectUnderTest = builder.build(objectUnderTest, singletonMap("testPathVariableUnnamed", pathVariable))

        then:
        objectUnderTest.testPathVariableUnnamed == pathVariable
    }

    class ObjectUnderTest {
        String notAPathVariable

        @PathVariable("test")
        String testPathVariableNamed

        @PathVariable
        String testPathVariableUnnamed
    }

}

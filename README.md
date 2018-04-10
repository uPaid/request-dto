# request-dto

Argument resolver for Data Transfer Objects

## Table of contents

* [Introduction](#introduction)
* [Configuration](#configuration)
  * [Spring MVC](#spring-mvc)
  * [Serialization](#serialization)
* [Example usage](#example-usage)
  * [Spring style](#spring-style)
  * [RequestDTO style](#requestdto-style)

## Introduction

Let's face it, our REST APIs are rarely "restful". With all the business logic our system consist of, a proper CRUD implementation simply cannot meet all the requirements. We sometimes have to obtain public API keys from headers, sometimes from the request body, and sometimes they are given to us as a path variable. All of this makes implementation rather messy, and the volume of our code tends to grow exponentialy.

The purpose of this library is aid the separation of different business logic requirements such as endpoint's API and validation.

Proceed with care. It worked out in one of our projects, but it does not mean it's suitable for every specification.

## Configuration

### Spring MVC

Add argument resolver to your web MVC configuration:

```java
@Configuration
public class MyWebConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private final ApplicationContext applicationContext;

    @Autowired
    private final ObjectMapper objectMapper;

    @Override
    protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        Validator validator = Validation.byDefaultProvider()
                                        .configure()
                                        .buildValidatorFactory()
                                        .getValidator();

        argumentResolvers.add(new RequestDTOArgumentResolver(applicationContext,
                                                             validator,
                                                             objectMapper));
  }

}
```

### Serialization

By default, Gson is used for serialization / deserialization, but you may use any object mapper you wish by passing it to a constructor as a `SerializationFunction` object:

```java
new RequestDTOArgumentResolver(applicationContext,
                               validator,
                               new SerializationFunction() {
                                   @Override
                                   public <T> T serialize(String input, Class<T> outputClass) {
                                       // Custom serialization goes here
                                   }
                               };
```

## Example usage

### Spring style

Normally, we would inject all the variables we need into controller method and deal with them separately. Sometimes we need a `@RequestBody`, sometimes a `HttpServletRequest` to get a header from it:

```java
@Validated
@RestController
public class ExampleController {

    private final ExampleService exampleService;

    @Autowired
    public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }

    @PostMapping("/example/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void example(@RequestBody @Valid ExampleInput exampleInput,
                        @PathVariable("id") Long id,
                        HttpServletRequest request) {
        String exampleHeader = request.getHeader("Example-Header");
        return exampleService.performAction(exampleInput, id, exampleHeader);
    }

}
```

### RequestDTO style

With `@RequestDTO` we can collect everything we need to perform an action into a single object. This way the only responsibility of our controller and service is to perform an action on the data. At the same time we explicitly show which class represent input, which class is responsible for putting everything together and which is responsible of domain-specific validation:

```java
@Validated
@RestController
public class ExampleController {

    private final ExampleService exampleService;

    @Autowired
    public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }

    @PostMapping("/example/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void example(@RequestDTO(input = ExampleInput.class,
                                    builder = ExampleDTOBuilder.class,
                                    validator = ExampleDTOValudator.class)
                                    ExampleDTO exampleDTO) {
        return exampleService.performAction(exampleDTO);
    }

}
```

Our example input class may look like:

```java
public class ExampleInput {
    @NotNull
    @Header
    private String exampleHeader;

    @NotNull
    @PathVariable
    private Long id;

    @NotEmpty
    private String someVariable;

    // Getters and setters
}
```

Builder to create the DTO from input:

```java
@Component
public class ExampleDTOBuilder implements DTOBuilder<ExampleInput, ExampleDTO> {

    private final ExampleRepository exampleRepository;

    @Autowired
    public ExampleDTOBuilder(ExampleRepository exampleRepository) {
        this.exampleRepository = exampleRepository;
    }

    @Override
    public ExampleDTO build(ExampleInput exampleInput) {
        DatabaseEntity exampleDatabaseEntity = exampleRepository.findOneById(exampleInput.getId());
        if (exampleDatabaseEntity == null) {
            throw new ExampleNotFoundException();
        }
        return new ExampleDTO(exampleDatabaseEntity, exampleInput.getSomeVariable());
    }

    @Override
    public Class<? extends ExampleInput> getInputClass() {
        return ExampleInput.class;
    }
}

```

Our resulting DTO (preferably immutable):

```java
public class ExampleDTO implements DTO<ExampleInput> {
    private DatabaseEntity exampleDatabaseEntity;
    private String someVariable;

    public ExampleDTO(DatabaseEntity exampleDatabaseEntity, String someVariable) {
        this.exampleDatabaseEntity = exampleDatabaseEntity;
        this.someVariable = someVariable;
    }

    // Getters and domain-specific methods
}
```

And our validator:

```java
@Component
public class ExampleDTOValudator implements DTOValidator<ExampleInput, ExampleDTO> {
    @Override
    public void validate(ExampleDTO dto) {
        // throw an exception if the DTO is corrupted
        if (dto.someVariable != exampleDatabaseEntity.someVariable) {
            throw new ExampleInternalException();
        }
    }

    @Override
    public Class<ExampleDTO> getSupportedDTOClass() {
        return ExampleDTO.class;
    }
}
```



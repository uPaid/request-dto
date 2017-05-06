package support.dto;

public interface DTOValidator<Input, DTOToValidate extends DTO<Input>> {
    void validate(DTOToValidate dto);

    Class<DTOToValidate> getSupportedDTOClass();
}

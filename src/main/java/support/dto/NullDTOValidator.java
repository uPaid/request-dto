package support.dto;

public class NullDTOValidator implements DTOValidator<Object, DTO<Object>> {
    public void validate(DTO<Object> dto) {
    }

    public Class<DTO<Object>> getSupportedDTOClass() {
        return null;
    }
}

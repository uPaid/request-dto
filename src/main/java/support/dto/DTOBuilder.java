package support.dto;

public interface DTOBuilder<Input, InputDTO extends DTO<Input>> {
    InputDTO build(Input input);

    Class<? extends Input> getInputClass();
}

package support;

public class RequestDTOException extends RuntimeException {
    public RequestDTOException() {
        super("Request DTO resolution failure");
    }

    public RequestDTOException(String message) {
        super(message);
    }

    public RequestDTOException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

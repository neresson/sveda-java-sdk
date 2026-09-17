package ai.sveda.client.exceptions;

public final class TransporterException extends SvedaException {
    private final Integer statusCode;

    public TransporterException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}

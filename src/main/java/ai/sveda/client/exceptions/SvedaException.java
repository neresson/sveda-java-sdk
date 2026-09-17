package ai.sveda.client.exceptions;

public class SvedaException extends RuntimeException {
    public SvedaException(String message) {
        super(message);
    }

    public SvedaException(String message, Throwable cause) {
        super(message, cause);
    }
}

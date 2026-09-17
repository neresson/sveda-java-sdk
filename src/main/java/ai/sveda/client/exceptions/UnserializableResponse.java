package ai.sveda.client.exceptions;

public final class UnserializableResponse extends SvedaException {
    public UnserializableResponse(String message) {
        super(message);
    }

    public UnserializableResponse(String message, Throwable cause) {
        super(message, cause);
    }
}

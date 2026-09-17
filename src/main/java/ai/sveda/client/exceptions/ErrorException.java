package ai.sveda.client.exceptions;

import java.util.Map;

public final class ErrorException extends SvedaException {
    private final int statusCode;
    private final Map<String, Object> response;

    public ErrorException(String message, int statusCode, Map<String, Object> response) {
        super(message);
        this.statusCode = statusCode;
        this.response = response;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, Object> response() {
        return response;
    }
}

package au.com.transport.tapngo.exception;

public class TapProcessingException extends RetryableException {
    public TapProcessingException(String message) { super(message); }
    public TapProcessingException(String message, Throwable cause) { super(message, cause); }
}

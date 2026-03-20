package au.com.transport.tapngo.exception;

public class InvalidTapDataException extends NonRetryableException {
    public InvalidTapDataException(String message) { super(message); }
}

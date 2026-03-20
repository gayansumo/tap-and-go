package au.com.transport.tapngo.exception;

/**
 * Base for all non-retryable exceptions.
 * JMS will NOT redeliver — message goes straight to DLQ.
 * Examples: corrupt data, unknown tap type, invalid PAN format.
 */
public class NonRetryableException extends RuntimeException {
    public NonRetryableException(String message) { super(message); }
    public NonRetryableException(String message, Throwable cause) { super(message, cause); }
}

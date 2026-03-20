package au.com.transport.tapngo.exception;

/**
 * Base for all retryable exceptions.
 * JMS will redeliver messages that throw this.
 * Examples: transient DB failure, queue timeout.
 */
public class RetryableException extends RuntimeException {
    public RetryableException(String message) { super(message); }
    public RetryableException(String message, Throwable cause) { super(message, cause); }
}

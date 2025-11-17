package kafkasql.io;

/**
 * Exception thrown when a read operation is cancelled.
 */
public final class ReadCancelledException extends RuntimeException {
    
    /**
     * Constructs a new ReadCancelledException with the specified message.
     * @param message The detail message
     */
    public ReadCancelledException(String message) {
        super(message);
    }

    /**
     * Constructs a new ReadCancelledException with the specified message and cause.
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public ReadCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
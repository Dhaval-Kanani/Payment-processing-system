package payment.exception;

/** Retry / DLQ publish failed. Consumer turns this into NACK. */
public class PublishException extends RuntimeException {
    public PublishException(String message) {
        super(message);
    }

    public PublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

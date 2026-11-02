package payment.exception;

/** Malformed event (bad request). Propagates out of the consumer; neither ACK nor NACK. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}

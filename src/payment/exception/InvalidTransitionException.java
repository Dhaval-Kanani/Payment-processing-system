package payment.exception;

import payment.model.EventType;
import payment.model.PaymentStatus;

/** Event type is not allowed from the payment's current status. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(PaymentStatus from, EventType eventType) {
        super("Cannot apply " + eventType + " to payment in status " + from);
    }
}

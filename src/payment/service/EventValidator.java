package payment.service;

import payment.exception.ValidationException;
import payment.model.PaymentEvent;

/** Service-boundary validation. Throws ValidationException (bad request) on the first violation. */
public class EventValidator {
    public void validate(PaymentEvent event) {
        if (event == null) {
            throw new ValidationException("event must not be null");
        }
        requireText(event.eventId(), "event_id");
        requireText(event.paymentId(), "payment_id");
        if (event.sequenceNumber() <= 0) {
            throw new ValidationException("sequence_number must be > 0, was " + event.sequenceNumber());
        }
        if (event.eventType() == null) {
            throw new ValidationException("event_type must not be null");
        }
        if (event.amount() < 0) {
            throw new ValidationException("amount must be >= 0, was " + event.amount());
        }
        requireText(event.currency(), "currency");
        if (event.createdAt() == null) {
            throw new ValidationException("created_at must not be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
    }
}

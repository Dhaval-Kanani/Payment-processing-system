package payment.publisher;

import payment.model.PaymentEvent;

/** Publishes an event that exhausted its retries. Throws PublishException on failure. */
public interface DeadLetterPublisher {
    void publish(PaymentEvent event, String reason);
}

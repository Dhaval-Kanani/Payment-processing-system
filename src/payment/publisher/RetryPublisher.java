package payment.publisher;

import payment.model.PaymentEvent;

/** Publishes an event to the delayed retry topic. Throws PublishException on failure. */
public interface RetryPublisher {
    void publish(PaymentEvent event, int attempt);
}

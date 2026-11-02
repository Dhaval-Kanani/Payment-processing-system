package payment.publisher;

import payment.model.PaymentEvent;
import payment.model.PaymentState;

/** Downstream service hook invoked when an event is applied. Mocked in this exercise. */
public interface DownstreamNotifier {
    void notify(PaymentState newState, PaymentEvent event);
}

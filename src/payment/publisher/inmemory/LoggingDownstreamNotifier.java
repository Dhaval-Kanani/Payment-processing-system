package payment.publisher.inmemory;

import payment.model.PaymentEvent;
import payment.model.PaymentState;
import payment.publisher.DownstreamNotifier;

/** Mock downstream: prints the notification. */
public class LoggingDownstreamNotifier implements DownstreamNotifier {
    @Override
    public void notify(PaymentState newState, PaymentEvent event) {
        System.out.println("  [downstream] " + event.eventId() + " -> " + newState);
    }
}

package payment.publisher.inmemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import payment.model.PaymentEvent;
import payment.publisher.RetryPublisher;

/** Simulated delayed-retry topic. Tests and the demo drain() it and re-feed the events. */
public class InMemoryRetryPublisher implements RetryPublisher {
    private final Queue<PaymentEvent> topic = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(PaymentEvent event, int attempt) {
        topic.add(event);
    }

    /** Removes and returns everything currently on the topic, in publish order. */
    public List<PaymentEvent> drain() {
        List<PaymentEvent> drained = new ArrayList<>();
        PaymentEvent e;
        while ((e = topic.poll()) != null) {
            drained.add(e);
        }
        return drained;
    }

    public int size() {
        return topic.size();
    }
}

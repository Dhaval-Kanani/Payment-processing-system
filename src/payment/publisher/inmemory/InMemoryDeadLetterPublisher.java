package payment.publisher.inmemory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import payment.model.PaymentEvent;
import payment.publisher.DeadLetterPublisher;

/** Simulated DLQ topic, inspectable by tests and the demo. */
public class InMemoryDeadLetterPublisher implements DeadLetterPublisher {
    public record DeadLetter(PaymentEvent event, String reason) {}

    private final List<DeadLetter> topic = new CopyOnWriteArrayList<>();

    @Override
    public void publish(PaymentEvent event, String reason) {
        topic.add(new DeadLetter(event, reason));
    }

    public List<DeadLetter> deadLetters() {
        return Collections.unmodifiableList(topic);
    }
}

package payment.support;

import payment.model.PaymentEvent;
import payment.model.PaymentState;
import payment.publisher.DownstreamNotifier;
import payment.publisher.inmemory.InMemoryDeadLetterPublisher;
import payment.publisher.inmemory.InMemoryRetryPublisher;
import payment.repository.inmemory.InMemoryPaymentStateRepository;
import payment.repository.inmemory.InMemoryRetryAttemptStore;
import payment.service.EventProcessor;
import payment.service.PaymentStateService;

/** Wires the real components with in-memory fakes. Downstream is a no-op unless overridden. */
public class Fixture {
    public static final int MAX_RETRIES = 3;

    public final InMemoryPaymentStateRepository repo = new InMemoryPaymentStateRepository();
    public final InMemoryRetryAttemptStore attempts = new InMemoryRetryAttemptStore();
    public final InMemoryRetryPublisher retry = new InMemoryRetryPublisher();
    public final InMemoryDeadLetterPublisher dlq = new InMemoryDeadLetterPublisher();
    public final PaymentStateService service;
    public final EventProcessor processor;

    public Fixture() {
        this((state, event) -> {});
    }

    public Fixture(DownstreamNotifier downstream) {
        this.service = new PaymentStateService(repo, downstream);
        this.processor = new EventProcessor(repo, service, attempts, retry, dlq, MAX_RETRIES);
    }

    public PaymentState state(String paymentId) {
        return repo.findByPaymentId(paymentId).orElseGet(() -> PaymentState.initial(paymentId));
    }

    public static DownstreamNotifier failingDownstream(String message) {
        return (PaymentState s, PaymentEvent e) -> { throw new RuntimeException(message); };
    }
}

package payment.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import payment.model.PaymentEvent;
import payment.model.PaymentState;
import payment.model.ProcessingOutcome;
import payment.publisher.DeadLetterPublisher;
import payment.publisher.RetryPublisher;
import payment.repository.PaymentStateRepository;
import payment.repository.RetryAttemptStore;

/**
 * Ordering + idempotency rules. The whole decision for one event runs under a
 * per-payment ReentrantLock so load -> compare -> apply is atomic per payment
 * while different payments proceed in parallel.
 */
public class EventProcessor {
    private final PaymentStateRepository repository;
    private final PaymentStateService stateService;
    private final RetryAttemptStore attempts;
    private final RetryPublisher retryPublisher;
    private final DeadLetterPublisher deadLetterPublisher;
    private final int maxRetries;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public EventProcessor(PaymentStateRepository repository,
                          PaymentStateService stateService,
                          RetryAttemptStore attempts,
                          RetryPublisher retryPublisher,
                          DeadLetterPublisher deadLetterPublisher,
                          int maxRetries) {
        this.repository = repository;
        this.stateService = stateService;
        this.attempts = attempts;
        this.retryPublisher = retryPublisher;
        this.deadLetterPublisher = deadLetterPublisher;
        this.maxRetries = maxRetries;
    }

    public ProcessingOutcome process(PaymentEvent event) {
        ReentrantLock lock = locks.computeIfAbsent(event.paymentId(), id -> new ReentrantLock());
        lock.lock();
        try {
            return decide(event);
        } finally {
            lock.unlock();
        }
    }

    private ProcessingOutcome decide(PaymentEvent event) {
        PaymentState state = repository.findByPaymentId(event.paymentId())
                .orElseGet(() -> PaymentState.initial(event.paymentId()));
        long last = state.lastProcessedSequence();
        long seq = event.sequenceNumber();

        if (seq <= last) {
            attempts.clear(event.eventId());
            return ProcessingOutcome.IGNORED_DUPLICATE;
        }
        if (seq == last + 1) {
            if (!stateService.canApply(state, event)) {
                return retryOrDeadLetter(event,
                        "invalid transition " + state.status() + " + " + event.eventType());
            }
            stateService.apply(state, event);
            attempts.clear(event.eventId());
            return ProcessingOutcome.APPLIED;
        }
        return retryOrDeadLetter(event, "future sequence " + seq + " > last " + last);
    }

    /** Publish first, count second: a failed publish leaves the attempt count untouched. */
    private ProcessingOutcome retryOrDeadLetter(PaymentEvent event, String reason) {
        int soFar = attempts.get(event.eventId());
        if (soFar >= maxRetries) {
            deadLetterPublisher.publish(event, reason);
            attempts.clear(event.eventId());
            return ProcessingOutcome.DEAD_LETTERED;
        }
        retryPublisher.publish(event, soFar + 1);
        attempts.increment(event.eventId());
        return ProcessingOutcome.RETRY_SCHEDULED;
    }
}

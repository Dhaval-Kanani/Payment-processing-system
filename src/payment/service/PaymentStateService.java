package payment.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import payment.exception.InvalidTransitionException;
import payment.model.EventType;
import payment.model.PaymentEvent;
import payment.model.PaymentState;
import payment.model.PaymentStatus;
import payment.publisher.DownstreamNotifier;
import payment.repository.PaymentStateRepository;

/**
 * Owns the payment state machine and applies an in-sequence event.
 * Caller (EventProcessor) holds the per-payment lock, so notify + save are not interleaved
 * with another event for the same payment.
 */
public class PaymentStateService {
    /** Allowed event types from each status. Terminal statuses have no entry. */
    private static final Map<PaymentStatus, Set<EventType>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.NEW, Set.of(EventType.AUTHORIZED, EventType.FAILED));
        ALLOWED.put(PaymentStatus.AUTHORIZED, Set.of(EventType.CAPTURED, EventType.FAILED));
        ALLOWED.put(PaymentStatus.CAPTURED, Set.of(EventType.REFUND));
        ALLOWED.put(PaymentStatus.REFUNDED, Set.of());
        ALLOWED.put(PaymentStatus.FAILED, Set.of());
    }

    private final PaymentStateRepository repository;
    private final DownstreamNotifier downstream;

    public PaymentStateService(PaymentStateRepository repository, DownstreamNotifier downstream) {
        this.repository = repository;
        this.downstream = downstream;
    }

    public boolean canApply(PaymentState current, PaymentEvent event) {
        return ALLOWED.getOrDefault(current.status(), Set.of()).contains(event.eventType());
    }

    /**
     * Notify downstream first, then save. If downstream throws nothing is persisted,
     * so the redelivered event sees the old state (at-least-once downstream).
     */
    public PaymentState apply(PaymentState current, PaymentEvent event) {
        if (!canApply(current, event)) {
            throw new InvalidTransitionException(current.status(), event.eventType());
        }
        PaymentState next = current.withApplied(statusFor(event.eventType()), event.sequenceNumber());
        downstream.notify(next, event);
        repository.save(next);
        return next;
    }

    private static PaymentStatus statusFor(EventType type) {
        switch (type) {
            case AUTHORIZED: return PaymentStatus.AUTHORIZED;
            case CAPTURED:   return PaymentStatus.CAPTURED;
            case REFUND:     return PaymentStatus.REFUNDED;
            case FAILED:     return PaymentStatus.FAILED;
            default: throw new IllegalArgumentException("Unmapped event type " + type);
        }
    }
}

package payment.support;

import java.time.Instant;
import payment.model.EventType;
import payment.model.PaymentEvent;

/** Test fixture builders. */
public final class Events {
    private Events() {}

    public static PaymentEvent event(String eventId, String paymentId, long seq, EventType type) {
        return PaymentEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .sequenceNumber(seq)
                .eventType(type)
                .amount(10_000L)
                .currency("INR")
                .createdAt(Instant.parse("2026-10-30T10:00:00Z"))
                .signature("sig")
                .build();
    }

    public static PaymentEvent authorized(String paymentId, long seq) {
        return event("evt_" + paymentId + "_" + seq, paymentId, seq, EventType.AUTHORIZED);
    }

    public static PaymentEvent captured(String paymentId, long seq) {
        return event("evt_" + paymentId + "_" + seq, paymentId, seq, EventType.CAPTURED);
    }

    public static PaymentEvent refund(String paymentId, long seq) {
        return event("evt_" + paymentId + "_" + seq, paymentId, seq, EventType.REFUND);
    }

    public static PaymentEvent failed(String paymentId, long seq) {
        return event("evt_" + paymentId + "_" + seq, paymentId, seq, EventType.FAILED);
    }
}

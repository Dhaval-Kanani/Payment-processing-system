package payment.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable inbound event. Identity is eventId. */
public final class PaymentEvent {
    private final String eventId;
    private final String paymentId;
    private final long sequenceNumber;
    private final EventType eventType;
    private final long amount; // minor units (paise)
    private final String currency;
    private final Instant createdAt;
    private final String signature;

    private PaymentEvent(Builder b) {
        this.eventId = b.eventId;
        this.paymentId = b.paymentId;
        this.sequenceNumber = b.sequenceNumber;
        this.eventType = b.eventType;
        this.amount = b.amount;
        this.currency = b.currency;
        this.createdAt = b.createdAt;
        this.signature = b.signature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String eventId() { return eventId; }
    public String paymentId() { return paymentId; }
    public long sequenceNumber() { return sequenceNumber; }
    public EventType eventType() { return eventType; }
    public long amount() { return amount; }
    public String currency() { return currency; }
    public Instant createdAt() { return createdAt; }
    public String signature() { return signature; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentEvent)) return false;
        return Objects.equals(eventId, ((PaymentEvent) o).eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(eventId);
    }

    @Override
    public String toString() {
        return "PaymentEvent{" + eventId + ", payment=" + paymentId + ", seq=" + sequenceNumber
                + ", type=" + eventType + ", amount=" + amount + " " + currency + "}";
    }

    public static final class Builder {
        private String eventId;
        private String paymentId;
        private long sequenceNumber;
        private EventType eventType;
        private long amount;
        private String currency;
        private Instant createdAt;
        private String signature;

        private Builder() {}

        public Builder eventId(String v) { this.eventId = v; return this; }
        public Builder paymentId(String v) { this.paymentId = v; return this; }
        public Builder sequenceNumber(long v) { this.sequenceNumber = v; return this; }
        public Builder eventType(EventType v) { this.eventType = v; return this; }
        public Builder amount(long v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder signature(String v) { this.signature = v; return this; }

        /** No validation here; EventValidator enforces rules at the service boundary. */
        public PaymentEvent build() {
            return new PaymentEvent(this);
        }
    }
}

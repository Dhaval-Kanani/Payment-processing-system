package payment.model;

import java.util.Objects;

/** Immutable snapshot of a payment. Replaced wholesale on every applied event. */
public final class PaymentState {
    private final String paymentId;
    private final PaymentStatus status;
    private final long lastProcessedSequence;

    private PaymentState(String paymentId, PaymentStatus status, long lastProcessedSequence) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.status = Objects.requireNonNull(status, "status");
        this.lastProcessedSequence = lastProcessedSequence;
    }

    /** State for a payment that has had no event applied yet. */
    public static PaymentState initial(String paymentId) {
        return new PaymentState(paymentId, PaymentStatus.NEW, 0L);
    }

    public PaymentState withApplied(PaymentStatus newStatus, long sequence) {
        return new PaymentState(paymentId, newStatus, sequence);
    }

    public String paymentId() { return paymentId; }
    public PaymentStatus status() { return status; }
    public long lastProcessedSequence() { return lastProcessedSequence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentState)) return false;
        PaymentState that = (PaymentState) o;
        return lastProcessedSequence == that.lastProcessedSequence
                && paymentId.equals(that.paymentId)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId, status, lastProcessedSequence);
    }

    @Override
    public String toString() {
        return "PaymentState{" + paymentId + ", status=" + status + ", lastSeq=" + lastProcessedSequence + "}";
    }
}

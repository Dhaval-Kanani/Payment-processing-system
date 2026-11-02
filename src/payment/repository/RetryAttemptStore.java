package payment.repository;

/** Retry attempts per eventId. */
public interface RetryAttemptStore {
    /** @return attempts so far, 0 if unknown */
    int get(String eventId);

    /** @return the new attempt count */
    int increment(String eventId);

    void clear(String eventId);
}

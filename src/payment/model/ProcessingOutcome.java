package payment.model;

/** What EventProcessor did with an event. */
public enum ProcessingOutcome {
    APPLIED,
    IGNORED_DUPLICATE,
    RETRY_SCHEDULED,
    DEAD_LETTERED
}

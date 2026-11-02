package payment.repository.inmemory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import payment.repository.RetryAttemptStore;

/** ConcurrentHashMap keyed by eventId; merge gives an atomic increment. */
public class InMemoryRetryAttemptStore implements RetryAttemptStore {
    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();

    @Override
    public int get(String eventId) {
        return attempts.getOrDefault(eventId, 0);
    }

    @Override
    public int increment(String eventId) {
        return attempts.merge(eventId, 1, Integer::sum);
    }

    @Override
    public void clear(String eventId) {
        attempts.remove(eventId);
    }
}

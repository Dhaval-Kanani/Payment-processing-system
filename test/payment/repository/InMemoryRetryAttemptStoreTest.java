package payment.repository;

import static payment.support.Assert.assertEquals;

import payment.repository.inmemory.InMemoryRetryAttemptStore;

public class InMemoryRetryAttemptStoreTest {
    private final InMemoryRetryAttemptStore store = new InMemoryRetryAttemptStore();

    public void testUnknownIsZero() {
        assertEquals(0, store.get("evt_x"));
    }

    public void testIncrementReturnsNewValue() {
        assertEquals(1, store.increment("evt_1"));
        assertEquals(2, store.increment("evt_1"));
        assertEquals(2, store.get("evt_1"));
    }

    public void testClearResets() {
        store.increment("evt_1");
        store.clear("evt_1");
        assertEquals(0, store.get("evt_1"));
    }

    public void testClearUnknownIsNoOp() {
        store.clear("evt_never");
        assertEquals(0, store.get("evt_never"));
    }
}
